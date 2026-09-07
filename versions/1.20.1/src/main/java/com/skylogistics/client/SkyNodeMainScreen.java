package com.skylogistics.client;

import com.skylogistics.menu.SkyNodeMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SkyNodeMainScreen extends SkyNodeScreen<SkyNodeMenu> {
    public SkyNodeMainScreen(SkyNodeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
