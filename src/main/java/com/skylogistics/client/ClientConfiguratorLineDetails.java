package com.skylogistics.client;

import com.skylogistics.network.ConfiguratorLineDetailsPacket;
import java.util.List;
import java.util.UUID;

public final class ClientConfiguratorLineDetails {
    private static UUID lineId;
    private static List<ConfiguratorLineDetailsPacket.Entry> entries = List.of();

    private ClientConfiguratorLineDetails() {
    }

    public static void apply(ConfiguratorLineDetailsPacket packet) {
        lineId = packet.lineId();
        entries = List.copyOf(packet.entries());
    }

    public static List<ConfiguratorLineDetailsPacket.Entry> entries(UUID currentLineId) {
        if (currentLineId == null || !currentLineId.equals(lineId)) {
            return List.of();
        }
        return entries;
    }
}
