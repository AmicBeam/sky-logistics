package com.skylogistics.client;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.menu.MenuAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ConfiguratorScreen extends AbstractContainerScreen<ConfiguratorMenu> {
    public ConfiguratorScreen(ConfiguratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 224;
        imageHeight = 168;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 16;
        int y = topPos + 92;
        addRenderableWidget(ConfigPanel.actionButton(x, y, 106, Component.translatable("button.skylogistics.new_line"),
                MenuAction.NEW_LINE));
        addRenderableWidget(ConfigPanel.actionButton(x + 112, y, 70, Component.translatable("button.skylogistics.items"),
                MenuAction.TOGGLE_ITEMS));
        addRenderableWidget(ConfigPanel.actionButton(x, y + 24, 70, Component.translatable("button.skylogistics.fluids"),
                MenuAction.TOGGLE_FLUIDS));
        addRenderableWidget(ConfigPanel.actionButton(x + 76, y + 24, 58,
                Component.translatable("button.skylogistics.energy"), MenuAction.TOGGLE_ENERGY));
        addRenderableWidget(ConfigPanel.actionButton(x + 140, y + 24, 42,
                Component.translatable("screen.skylogistics.redstone"), MenuAction.CONFIG_REDSTONE));
        addRenderableWidget(ConfigPanel.actionButton(x, y + 48, 32, Component.literal("-"),
                MenuAction.CONFIG_PRIORITY_DOWN));
        addRenderableWidget(ConfigPanel.actionButton(x + 38, y + 48, 32, Component.literal("+"),
                MenuAction.CONFIG_PRIORITY_UP));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ConfigPanel.BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, ConfigPanel.BORDER);
        graphics.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, ConfigPanel.BORDER);
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight, ConfigPanel.BORDER);
        graphics.fill(leftPos + imageWidth - 2, topPos, leftPos + imageWidth, topPos + imageHeight, ConfigPanel.BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        ConfiguratorItem.ToolConfig config = config();
        graphics.drawString(font, title, 14, 12, ConfigPanel.ACCENT, false);
        if (config == null) {
            graphics.drawString(font, Component.translatable("screen.skylogistics.configurator.unbound"),
                    14, 34, ConfigPanel.MUTED, false);
            return;
        }
        graphics.drawString(font, Component.translatable("screen.skylogistics.line_name",
                        ConfiguratorItem.shortLine(config.lineId())),
                14, 34, ConfigPanel.TEXT, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.types",
                ConfigPanel.yesNo(config.itemsEnabled()), ConfigPanel.yesNo(config.fluidsEnabled()),
                ConfigPanel.yesNo(config.energyEnabled())),
                14, 52, ConfigPanel.TEXT, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.configurator_redstone",
                Component.translatable(config.placement().redstoneControl().translationKey())),
                14, 70, ConfigPanel.TEXT, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.configurator_priority",
                config.placement().priority()), 116, 70, ConfigPanel.TEXT, false);
    }

    private ConfiguratorItem.ToolConfig config() {
        if (Minecraft.getInstance().player == null) {
            return null;
        }
        ItemStack stack = Minecraft.getInstance().player.getItemInHand(menu.getHand());
        return ConfiguratorItem.read(stack);
    }
}
