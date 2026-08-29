package com.skylogistics.event;

import com.skylogistics.compat.advancements.AdvancementDataPackRuntime;
import net.minecraftforge.event.server.ServerStartedEvent;

public final class AdvancementDataPackHandler {
    private AdvancementDataPackHandler() {}

    public static void onServerStarted(ServerStartedEvent event) {
        AdvancementDataPackRuntime.rebuild(event.getServer(), 15, true);
    }
}
