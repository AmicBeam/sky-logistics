package com.skylogistics.client;

import com.skylogistics.menu.KleisDominionWandMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Button;
import com.skylogistics.network.ModNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class KleisDominionWandScreen extends AbstractContainerScreen<KleisDominionWandMenu> {
    public KleisDominionWandScreen(KleisDominionWandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 240, 184);
        inventoryLabelY = 91;
    }
    @Override protected void init() {
        super.init(); int y=topPos+78;
        addRenderableWidget(Button.builder(Component.literal("Mode"),b->ModNetworking.sendKleisMenuAction(KleisDominionWandMenu.ACTION_TOGGLE_MODE)).bounds(leftPos+12,y,42,18).build());
        addRenderableWidget(Button.builder(Component.literal("I"),b->ModNetworking.sendKleisMenuAction(KleisDominionWandMenu.ACTION_TOGGLE_ITEMS)).bounds(leftPos+58,y,26,18).build());
        addRenderableWidget(Button.builder(Component.literal("F"),b->ModNetworking.sendKleisMenuAction(KleisDominionWandMenu.ACTION_TOGGLE_FLUIDS)).bounds(leftPos+88,y,26,18).build());
        addRenderableWidget(Button.builder(Component.literal("E"),b->ModNetworking.sendKleisMenuAction(KleisDominionWandMenu.ACTION_TOGGLE_ENERGY)).bounds(leftPos+118,y,26,18).build());
        addRenderableWidget(Button.builder(Component.literal("-"),b->ModNetworking.sendKleisMenuAction(KleisDominionWandMenu.ACTION_PRIORITY_DOWN)).bounds(leftPos+164,y,24,18).build());
        addRenderableWidget(Button.builder(Component.literal("+"),b->ModNetworking.sendKleisMenuAction(KleisDominionWandMenu.ACTION_PRIORITY_UP)).bounds(leftPos+192,y,24,18).build());
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        ConfigPanel.drawPanel(g, leftPos, topPos, imageWidth, imageHeight);
        ConfigPanel.drawContentPanel(g, leftPos + 8, topPos + 20, imageWidth - 16, 65);
        for (var slot : menu.slots) ConfigPanel.drawSlotBackground(g, leftPos + slot.x - 1, topPos + slot.y - 1);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, 8, 7, 0xFFE8F7FF, false);
        g.text(font, Component.translatable("screen.skylogistics.kleis.line", menu.lineName()), 14, 27, 0xFFB9FFF4, false);
        g.text(font, Component.translatable("screen.skylogistics.kleis.mode",
                Component.translatable("tooltip.skylogistics.face_mode." + menu.mode().getSerializedName())), 14, 41, 0xFFFFD56F, false);
        g.text(font, Component.translatable("screen.skylogistics.kleis.target", menu.pos().toShortString(), menu.face().getName()), 14, 55, 0xFFD0D8E0, false);
        g.text(font, Component.translatable("screen.skylogistics.kleis.cross_dimension"), 14, 69, 0xFFD9A8FF, false);
        g.text(font, playerInventoryTitle, 39, inventoryLabelY, 0xFFD0D8E0, false);
    }
}
