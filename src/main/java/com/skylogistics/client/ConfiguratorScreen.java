package com.skylogistics.client;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.menu.MenuAction;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.util.RedstoneControl;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ConfiguratorScreen extends AbstractContainerScreen<ConfiguratorMenu> {
    private final List<LineButton> lineButtons = new ArrayList<>();
    private final List<TypeToggleButton> typeButtons = new ArrayList<>();
    private final List<PriorityButton> priorityButtons = new ArrayList<>();
    private RedstoneButton redstoneButton;

    public ConfiguratorScreen(ConfiguratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 224;
        imageHeight = 168;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        lineButtons.clear();
        typeButtons.clear();
        priorityButtons.clear();
        addLineButton(leftPos + 86, topPos + 17, 22, Component.literal("|<"), MenuAction.LINE_FIRST);
        addLineButton(leftPos + 111, topPos + 17, 20, Component.literal("<"), MenuAction.LINE_PREVIOUS);
        addLineButton(leftPos + 134, topPos + 17, 24, Component.literal(">+"), MenuAction.LINE_NEXT_OR_CREATE);
        addLineButton(leftPos + 161, topPos + 17, 22, Component.literal(">|"), MenuAction.LINE_LAST);
        addLineButton(leftPos + 186, topPos + 17, 18, Component.literal("x"), MenuAction.LINE_REMOVE_CURRENT);

        addTypeButton(leftPos + 54, topPos + 76, ResourceType.ITEMS);
        addTypeButton(leftPos + 108, topPos + 76, ResourceType.FLUIDS);
        addTypeButton(leftPos + 162, topPos + 76, ResourceType.ENERGY);
        redstoneButton = addRenderableWidget(new RedstoneButton(leftPos + 54, topPos + 102));
        addPriorityButton(leftPos + 54, topPos + 128, -1, Component.literal("-"));
        addPriorityButton(leftPos + 128, topPos + 128, 1, Component.literal("+"));
    }

    private void addLineButton(int x, int y, int width, Component message, int action) {
        LineButton button = new LineButton(x, y, width, message, action);
        lineButtons.add(button);
        addRenderableWidget(button);
    }

    private void addTypeButton(int x, int y, ResourceType type) {
        TypeToggleButton button = new TypeToggleButton(x, y, type);
        typeButtons.add(button);
        addRenderableWidget(button);
    }

    private void addPriorityButton(int x, int y, int delta, Component message) {
        PriorityButton button = new PriorityButton(x, y, delta, message);
        priorityButtons.add(button);
        addRenderableWidget(button);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack stack = stack();
        int index = ConfiguratorItem.lineIndex(stack);
        int count = ConfiguratorItem.lineCount(stack);
        for (LineButton button : lineButtons) {
            button.refresh(index, count);
        }
        for (TypeToggleButton button : typeButtons) {
            button.active = config() != null;
        }
        for (PriorityButton button : priorityButtons) {
            button.active = config() != null;
        }
        if (redstoneButton != null) {
            redstoneButton.active = config() != null;
        }
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
                    14, 42, ConfigPanel.MUTED, false);
            return;
        }
        int lineIndex = ConfiguratorItem.lineIndex(stack()) + 1;
        int lineCount = Math.max(1, ConfiguratorItem.lineCount(stack()));
        graphics.drawString(font, Component.translatable("screen.skylogistics.line_name",
                        ConfiguratorItem.shortLine(config.lineId()))
                .append(Component.literal(" " + lineIndex + "/" + lineCount)),
                14, 34, ConfigPanel.TEXT, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.line_monitor",
                        menu.getLineNodes(), menu.getLineInputs(), menu.getLineOutputs()),
                14, 56, ConfigPanel.MUTED, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.resources"),
                14, 82, ConfigPanel.MUTED, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.redstone"),
                14, 108, ConfigPanel.MUTED, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.priority"),
                14, 134, ConfigPanel.MUTED, false);
        graphics.drawString(font, Component.literal(String.valueOf(config.placement().priority())),
                96, 134, ConfigPanel.TEXT, false);
    }

    private ConfiguratorItem.ToolConfig config() {
        return ConfiguratorItem.read(stack());
    }

    private ItemStack stack() {
        if (Minecraft.getInstance().player == null) {
            return ItemStack.EMPTY;
        }
        return Minecraft.getInstance().player.getItemInHand(menu.getHand());
    }

    private void borderedBox(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    private final class LineButton extends AbstractButton {
        private final int action;

        private LineButton(int x, int y, int width, Component message, int action) {
            super(x, y, width, 18, message);
            this.action = action;
        }

        private void refresh(int index, int count) {
            active = switch (action) {
                case MenuAction.LINE_FIRST, MenuAction.LINE_PREVIOUS -> count > 1 && index > 0;
                case MenuAction.LINE_LAST -> count > 1 && index < count - 1;
                case MenuAction.LINE_REMOVE_CURRENT -> count > 0;
                default -> true;
            };
        }

        @Override
        public void onPress() {
            if (active) {
                ModNetworking.sendMenuAction(action);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int fill = active ? 0xFF0C1A24 : 0xFF101820;
            int border = active ? ConfigPanel.BORDER : 0xFF2A3C46;
            borderedBox(graphics, getX(), getY(), width, height, fill, border);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 5,
                    active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class RedstoneButton extends AbstractButton {
        private RedstoneButton(int x, int y) {
            super(x, y, 96, 20, Component.translatable("screen.skylogistics.redstone"));
        }

        @Override
        public void onPress() {
            if (active) {
                ModNetworking.sendMenuAction(MenuAction.CONFIG_REDSTONE);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ConfiguratorItem.ToolConfig config = config();
            RedstoneControl control = config == null ? RedstoneControl.IGNORE : config.placement().redstoneControl();
            borderedBox(graphics, getX(), getY(), width, height, active ? 0xFF0C1A24 : 0xFF101820,
                    active ? ConfigPanel.BORDER : 0xFF2D4D5A);
            graphics.drawCenteredString(font, Component.translatable(control.translationKey()),
                    getX() + width / 2, getY() + 6, active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class PriorityButton extends AbstractButton {
        private final int delta;

        private PriorityButton(int x, int y, int delta, Component message) {
            super(x, y, 22, 20, message);
            this.delta = delta;
        }

        @Override
        public void onPress() {
            if (active) {
                ModNetworking.sendMenuAction(delta < 0
                        ? MenuAction.CONFIG_PRIORITY_DOWN
                        : MenuAction.CONFIG_PRIORITY_UP);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            borderedBox(graphics, getX(), getY(), width, height, active ? 0xFF0C1A24 : 0xFF101820,
                    active ? ConfigPanel.BORDER : 0xFF2D4D5A);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 6,
                    active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private enum ResourceType {
        ITEMS("button.skylogistics.items"),
        FLUIDS("button.skylogistics.fluids"),
        ENERGY("button.skylogistics.energy");

        private final String translationKey;

        ResourceType(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private final class TypeToggleButton extends AbstractButton {
        private final ResourceType type;

        private TypeToggleButton(int x, int y, ResourceType type) {
            super(x, y, 48, 20, Component.translatable(type.translationKey));
            this.type = type;
        }

        @Override
        public void onPress() {
            if (!active) {
                return;
            }
            switch (type) {
                case ITEMS -> ModNetworking.sendMenuAction(MenuAction.TOGGLE_ITEMS);
                case FLUIDS -> ModNetworking.sendMenuAction(MenuAction.TOGGLE_FLUIDS);
                case ENERGY -> ModNetworking.sendMenuAction(MenuAction.TOGGLE_ENERGY);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean enabled = isEnabled();
            int fill = enabled ? 0xFF123B45 : 0xFF0C1A24;
            borderedBox(graphics, getX(), getY(), width, height, fill, enabled ? ConfigPanel.BORDER : 0xFF2D4D5A);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 6,
                    enabled ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        private boolean isEnabled() {
            ConfiguratorItem.ToolConfig config = config();
            if (config == null) {
                return false;
            }
            return switch (type) {
                case ITEMS -> config.itemsEnabled();
                case FLUIDS -> config.fluidsEnabled();
                case ENERGY -> config.energyEnabled();
            };
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
