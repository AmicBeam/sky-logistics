package com.skylogistics.util;

/** Connection availability must never overwrite the player's saved extraction mode. */
public final class SimplePipeRecoveryPolicy {
    public enum Neighbor { UNLOADED, PIPE, BLOCKED_PIPE, CONTAINER, PENDING_CONTAINER, OTHER }

    public record Result(SimplePipeConnection connection, boolean retry) { }

    private SimplePipeRecoveryPolicy() { }

    public static Result resolve(boolean disconnected, boolean extracting, boolean connected, Neighbor neighbor) {
        if (disconnected) return new Result(SimplePipeConnection.NONE, false);
        SimplePipeConnection mode = extracting ? SimplePipeConnection.EXTRACT : SimplePipeConnection.INSERT;
        return switch (neighbor) {
            case UNLOADED -> new Result(connected ? mode : SimplePipeConnection.NONE, connected || extracting);
            case PIPE -> new Result(SimplePipeConnection.PIPE, false);
            case CONTAINER -> new Result(mode, false);
            case PENDING_CONTAINER -> new Result(connected ? mode : SimplePipeConnection.NONE, connected || extracting);
            case BLOCKED_PIPE, OTHER -> new Result(SimplePipeConnection.NONE, false);
        };
    }
}
