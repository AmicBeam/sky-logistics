package com.skylogistics.event;

import com.skylogistics.compat.advancements.AdvancementDataPackRuntime;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

public final class AdvancementDataPackHandler {
    private AdvancementDataPackHandler() {}

    public static void onServerStarted(ServerStartedEvent event) {
        AdvancementDataPackRuntime.rebuild(event.getServer(), 48, false);
    }
}
