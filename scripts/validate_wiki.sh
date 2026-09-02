#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
wiki_dir="$repo_root/wiki"
lang_file="$repo_root/versions/26.1.2/src/main/resources/assets/skylogistics/lang/zh_cn.json"
offering_recipe_dir="$repo_root/versions/26.1.2/src/main/resources/data/skylogistics/recipe"
guideme_dir="$repo_root/common/src/main/resources/assets/skylogistics/guides/skylogistics/sky_logistics"

if [[ ! -d "$wiki_dir" ]]; then
    echo "wiki directory not found: $wiki_dir" >&2
    exit 1
fi

missing=0
if rg -n '^\|.*\[\[[^]]*\|[^]]*\]\]' "$wiki_dir" -g '*.md'; then
    echo "GitHub Wiki links containing '|' cannot be used inside Markdown tables" >&2
    missing=1
fi

while IFS= read -r resource_id; do
    if ! rg -q --fixed-strings "skylogistics:$resource_id" "$wiki_dir/方块图鉴.md" "$wiki_dir/物品图鉴.md"; then
        echo "missing Wiki entry for skylogistics:$resource_id" >&2
        missing=1
    fi
done < <(
    sed -nE 's/^[[:space:]]*"(block|item)\.skylogistics\.([a-z0-9_]+)".*/\2/p' "$lang_file" |
        sort -u
)

for recipe_file in "$offering_recipe_dir"/*.json; do
    if [[ "$(jq -r '.type // empty' "$recipe_file")" != "skylogistics:sky_offering" ]]; then
        continue
    fi
    result_id="$(jq -r '.result.id // empty' "$recipe_file")"
    if [[ -n "$result_id" ]] && ! rg -q --fixed-strings "$result_id" "$wiki_dir/供奉系统.md"; then
        echo "missing offering result in Wiki offering table: $result_id" >&2
        missing=1
    fi
done

if ! diff -u \
    <(rg -o '\]\([^)]+\.md\)' "$guideme_dir/index.md" | sed -E 's/.*\]\(([^)]+)\).*/\1/' | sort -u) \
    <(rg -o '\]\([^)]+\.md\)' "$guideme_dir/_zh_cn/index.md" | sed -E 's/.*\]\(([^)]+)\).*/\1/' | sort -u); then
    echo "GuideME English and Chinese index topics differ" >&2
    missing=1
fi

for lang in zh_cn en_us; do
    patchouli_dir="$repo_root/common/src/main/resources/assets/skylogistics/patchouli_books/sky_logistics/$lang/entries"
    localized_guideme_dir="$guideme_dir"
    [[ "$lang" == "zh_cn" ]] && localized_guideme_dir="$guideme_dir/_zh_cn"
    for patchouli_file in "$patchouli_dir"/*.json; do
        page="$(basename "$patchouli_file" .json)"
        [[ "$page" == "logistics_overview" ]] && continue
        guideme_file="$localized_guideme_dir/$page.md"
        if [[ ! -f "$guideme_file" ]]; then
            echo "missing GuideME peer for Patchouli page: $lang/$page" >&2
            missing=1
            continue
        fi
        patchouli_recipes="$(jq -r '.pages[] | .recipe?, .recipe2? | select(. != null)' "$patchouli_file" | sort -u)"
        guideme_recipes="$(rg -o 'RecipeFor id="[^"]+"' "$guideme_file" | sed -E 's/.*id="([^"]+)"/skylogistics:\1/' | sort -u || true)"
        if [[ "$patchouli_recipes" != "$guideme_recipes" ]]; then
            echo "Patchouli/GuideME recipe references differ: $lang/$page" >&2
            missing=1
        fi
    done
done

for version in 1.20.1 1.21.1; do
    version_assets="$repo_root/versions/$version/src/main/resources/assets/skylogistics"
    for lang in zh_cn en_us; do
        localized_guideme_dir="$version_assets/guides/skylogistics/sky_logistics"
        [[ "$lang" == "zh_cn" ]] && localized_guideme_dir="$localized_guideme_dir/_zh_cn"
        for page in astages_integration advancement_integration; do
            if [[ ! -f "$localized_guideme_dir/$page.md" ]]; then
                echo "missing version-specific GuideME peer: $version/$lang/$page" >&2
                missing=1
            fi
        done
    done
done

while IFS= read -r link; do
    if [[ "$link" == *"|"* ]]; then
        target="${link#*|}"
    else
        target="$link"
    fi
    target="${target%%#*}"
    [[ -z "$target" ]] && continue
    if [[ ! -f "$wiki_dir/$target.md" ]]; then
        echo "broken Wiki link: [[$link]] (expected $target.md)" >&2
        missing=1
    fi
done < <(rg -o '\[\[[^]]+\]\]' "$wiki_dir" -g '*.md' | sed -E 's/.*\[\[([^]]+)\]\].*/\1/' | sort -u)

while IFS= read -r image_path; do
    if [[ ! -f "$wiki_dir/$image_path" ]]; then
        echo "broken Wiki image: $image_path" >&2
        missing=1
    fi
done < <(rg -o '\]\(images/[^)]+\)' "$wiki_dir" -g '*.md' | sed -E 's/.*\]\((images\/[^)]+)\).*/\1/' | sort -u)

if [[ "$missing" -ne 0 ]]; then
    exit 1
fi

echo "Documentation validation passed: Wiki coverage and Patchouli/GuideME peers are complete."
