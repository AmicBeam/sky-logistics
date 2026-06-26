#!/usr/bin/env python3
"""Deterministic budget model for the Sky Logistics transfer scheduler.

This script is intentionally small and does not start Minecraft.  It models the
operation budgets used by SkyNetworkTicker and the candidate target scans that
resource-specific output indexes are meant to reduce.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from typing import Mapping


RESOURCE_TYPES = ("item", "fluid", "chemical", "energy")


@dataclass(frozen=True)
class Budget:
    server_ops_per_tick: int = 2048
    line_ops_per_tick: int = 256
    endpoint_target_attempts: int = 16


@dataclass(frozen=True)
class Scenario:
    name: str
    inputs: int
    resources_per_input: tuple[str, ...]
    outputs_by_resource: Mapping[str, int]
    source_empty: bool = False
    target_accept_after: int | None = 1


@dataclass(frozen=True)
class Result:
    scenario: str
    indexed: bool
    operations: int
    source_checks: int
    target_attempts: int
    target_visits: int
    inputs_started: int
    successes: int
    failures: int
    budget_exhausted: bool


def simulate_tick(scenario: Scenario, budget: Budget, *, indexed: bool) -> Result:
    operations = 0
    source_checks = 0
    target_attempts = 0
    target_visits = 0
    inputs_started = 0
    successes = 0
    failures = 0
    budget_exhausted = False

    for _ in range(scenario.inputs):
        if operations >= budget.server_ops_per_tick or operations >= budget.line_ops_per_tick:
            budget_exhausted = True
            break
        inputs_started += 1
        for resource in scenario.resources_per_input:
            if resource not in RESOURCE_TYPES:
                raise ValueError(f"Unknown resource type: {resource}")
            if operations >= budget.server_ops_per_tick or operations >= budget.line_ops_per_tick:
                budget_exhausted = True
                break

            matching_outputs = scenario.outputs_by_resource.get(resource, 0)
            if matching_outputs <= 0:
                failures += 1
                continue

            operations += 1
            source_checks += 1
            if scenario.source_empty:
                failures += 1
                continue

            remaining_line_or_server_budget = min(
                budget.server_ops_per_tick - operations,
                budget.line_ops_per_tick - operations,
            )
            if remaining_line_or_server_budget <= 0:
                budget_exhausted = True
                failures += 1
                continue

            if scenario.target_accept_after is None:
                capped_target_attempts = min(budget.endpoint_target_attempts, matching_outputs)
            else:
                capped_target_attempts = min(
                    budget.endpoint_target_attempts,
                    scenario.target_accept_after,
                    matching_outputs,
                )
            attempts = min(remaining_line_or_server_budget, capped_target_attempts)
            operations += attempts
            target_attempts += attempts
            target_visits += target_visit_count(scenario, resource, attempts, indexed=indexed)

            accepted = scenario.target_accept_after is not None and attempts >= scenario.target_accept_after
            if accepted:
                successes += 1
            else:
                failures += 1
                if attempts < capped_target_attempts:
                    budget_exhausted = True

        if budget_exhausted:
            break

    return Result(
        scenario=scenario.name,
        indexed=indexed,
        operations=operations,
        source_checks=source_checks,
        target_attempts=target_attempts,
        target_visits=target_visits,
        inputs_started=inputs_started,
        successes=successes,
        failures=failures,
        budget_exhausted=budget_exhausted,
    )


def target_visit_count(scenario: Scenario, resource: str, attempts: int, *, indexed: bool) -> int:
    if attempts <= 0:
        return 0
    if indexed:
        return attempts

    matching_outputs = scenario.outputs_by_resource.get(resource, 0)
    unrelated_outputs = sum(scenario.outputs_by_resource.values()) - matching_outputs
    # Worst mixed-priority layout: unrelated resource outputs sort before the
    # matching resource outputs, so they are visited but do not consume the
    # endpointTargetAttempts budget.
    return unrelated_outputs + attempts


def built_in_scenarios() -> list[Scenario]:
    return [
        Scenario(
            name="40 inputs -> 40 item outputs, first target accepts",
            inputs=40,
            resources_per_input=("item",),
            outputs_by_resource={"item": 40},
            target_accept_after=1,
        ),
        Scenario(
            name="40 inputs -> mixed 4x40 outputs, item targets full",
            inputs=40,
            resources_per_input=("item",),
            outputs_by_resource={"item": 40, "fluid": 40, "chemical": 40, "energy": 40},
            target_accept_after=None,
        ),
        Scenario(
            name="40 FE inputs -> 40 FE outputs, first target accepts",
            inputs=40,
            resources_per_input=("energy",),
            outputs_by_resource={"energy": 40},
            target_accept_after=1,
        ),
        Scenario(
            name="1000 empty item sources -> 40 outputs",
            inputs=1000,
            resources_per_input=("item",),
            outputs_by_resource={"item": 40},
            source_empty=True,
            target_accept_after=1,
        ),
    ]


def assert_expected(results: list[Result]) -> None:
    by_key = {(result.scenario, result.indexed): result for result in results}
    large_success = by_key[("40 inputs -> 40 item outputs, first target accepts", True)]
    assert large_success.operations == 80, large_success
    assert large_success.successes == 40, large_success

    mixed_indexed = by_key[("40 inputs -> mixed 4x40 outputs, item targets full", True)]
    mixed_unindexed = by_key[("40 inputs -> mixed 4x40 outputs, item targets full", False)]
    assert mixed_indexed.operations == 256, mixed_indexed
    assert mixed_unindexed.operations == 256, mixed_unindexed
    assert mixed_unindexed.target_visits >= mixed_indexed.target_visits * 8, (
        mixed_indexed,
        mixed_unindexed,
    )

    empty_sources = by_key[("1000 empty item sources -> 40 outputs", True)]
    assert empty_sources.operations == 256, empty_sources
    assert empty_sources.source_checks == 256, empty_sources


def format_result(result: Result) -> str:
    mode = "indexed" if result.indexed else "unindexed"
    return (
        f"{result.scenario:52} {mode:9} "
        f"ops={result.operations:4} "
        f"src={result.source_checks:4} "
        f"target_ops={result.target_attempts:4} "
        f"target_visits={result.target_visits:5} "
        f"inputs={result.inputs_started:4} "
        f"ok={result.successes:4} "
        f"fail={result.failures:4} "
        f"budget={'yes' if result.budget_exhausted else 'no'}"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-ops", type=int, default=Budget.server_ops_per_tick)
    parser.add_argument("--line-ops", type=int, default=Budget.line_ops_per_tick)
    parser.add_argument("--target-attempts", type=int, default=Budget.endpoint_target_attempts)
    parser.add_argument("--no-assert", action="store_true", help="Print results without regression assertions.")
    args = parser.parse_args()

    budget = Budget(
        server_ops_per_tick=args.server_ops,
        line_ops_per_tick=args.line_ops,
        endpoint_target_attempts=args.target_attempts,
    )
    results: list[Result] = []
    for scenario in built_in_scenarios():
        results.append(simulate_tick(scenario, budget, indexed=True))
        results.append(simulate_tick(scenario, budget, indexed=False))

    print(
        "Budget: "
        f"serverOpsPerTick={budget.server_ops_per_tick}, "
        f"lineOpsPerTick={budget.line_ops_per_tick}, "
        f"endpointTargetAttempts={budget.endpoint_target_attempts}"
    )
    for result in results:
        print(format_result(result))

    if not args.no_assert:
        assert_expected(results)
        print("Regression assertions passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
