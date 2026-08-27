package com.skylogistics.compat;

import com.skylogistics.SkyLogistics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

public final class GuideMeCompat {
    private static final String GUIDEME = "guideme";
    private static final String GUIDEME_PROXY = "guideme.internal.GuideMEProxy";
    private static final ResourceLocation SKY_LOGISTICS_GUIDE =
            new ResourceLocation(SkyLogistics.MOD_ID, "sky_logistics");

    private GuideMeCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(GUIDEME);
    }

    public static boolean openManual(Player player) {
        if (!isLoaded()) return false;
        try {
            Class<?> proxyClass = Class.forName(GUIDEME_PROXY);
            Object proxy = proxyClass.getMethod("instance").invoke(null);
            Object opened = proxyClass.getMethod("openGuide", Player.class, ResourceLocation.class)
                    .invoke(proxy, player, SKY_LOGISTICS_GUIDE);
            return opened instanceof Boolean result && result;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return false;
        }
    }
}
