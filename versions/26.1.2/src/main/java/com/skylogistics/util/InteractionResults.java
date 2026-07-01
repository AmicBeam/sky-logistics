package com.skylogistics.util;

import net.minecraft.world.InteractionResult;

public final class InteractionResults {
    private InteractionResults() {
    }

    public static InteractionResult sidedSuccess(boolean clientSide) {
        return clientSide ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    public static InteractionResult passToEmptyHand(InteractionResult result) {
        return result instanceof InteractionResult.Pass ? InteractionResult.TRY_WITH_EMPTY_HAND : result;
    }
}
