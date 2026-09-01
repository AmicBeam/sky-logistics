package com.skylogistics.client;

import com.skylogistics.menu.KleisDominionWandMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Uses the node screen verbatim; the single remembered endpoint suppresses only the left face selector. */
public final class KleisDominionWandScreen extends SkyNodeScreen<KleisDominionWandMenu> {
    public KleisDominionWandScreen(KleisDominionWandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
