package com.skylogistics.client;

import com.skylogistics.menu.KleisDominionWandMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Button;
import com.skylogistics.network.ModNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class KleisDominionWandScreen extends AbstractContainerScreen<KleisDominionWandMenu> {
    public KleisDominionWandScreen(KleisDominionWandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 240;
        imageHeight = 184;
        inventoryLabelY = 91;
    }

    @Override protected void init() {
        super.init(); int y = topPos + 78;
        addRenderableWidget(Button.builder(Component.literal("模式"), b -> ModNetworking.sendKleisMenuAction(KleisDominionWandMenu.ACTION_TOGGLE_MODE)).bounds(leftPos+12,y,42,18).build());
        addRenderableWidget(Button.builder(Component.literal("物"), b -> ModNetworking.sendKleisMenuAction(KleisDominionWandMenu.ACTION_TOGGLE_ITEMS)).bounds(leftPos+58,y,26,18).build());
        addRenderableWidget(Button.builder(Component.literal("液"), b -> ModNetworking.sendKleisMenuAction(KleisDominionWandMenu.ACTION_TOGGLE_FLUIDS)).bounds(leftPos+88,y,26,18).build());
        addRenderableWidget(Button.builder(Component.literal("能"), b -> ModNetworking.sendKleisMenuAction(KleisDominionWandMenu.ACTION_TOGGLE_ENERGY)).bounds(leftPos+118,y,26,18).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> ModNetworking.sendKleisMenuAction(KleisDominionWandMenu.ACTION_PRIORITY_DOWN)).bounds(leftPos+164,y,24,18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> ModNetworking.sendKleisMenuAction(KleisDominionWandMenu.ACTION_PRIORITY_UP)).bounds(leftPos+192,y,24,18).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ConfigPanel.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        ConfigPanel.drawContentPanel(graphics, leftPos + 8, topPos + 20, imageWidth - 16, 65);
        for (var slot : menu.slots) ConfigPanel.drawSlotBackground(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 7, 0xE8F7FF, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.kleis.line", menu.lineName()),
                14, 27, 0xB9FFF4, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.kleis.mode",
                Component.translatable("tooltip.skylogistics.face_mode." + menu.mode().getSerializedName())),
                14, 41, 0xFFD56F, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.kleis.target",
                menu.pos().toShortString(), menu.face().getName()), 14, 55, 0xD0D8E0, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.kleis.cross_dimension"),
                14, 69, 0xD9A8FF, false);
        graphics.drawString(font, playerInventoryTitle, 39, inventoryLabelY, 0xD0D8E0, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
