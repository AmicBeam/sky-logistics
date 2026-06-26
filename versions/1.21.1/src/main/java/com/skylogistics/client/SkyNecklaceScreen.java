package com.skylogistics.client;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.SkyNecklaceItem;
import com.skylogistics.menu.MenuAction;
import com.skylogistics.menu.SkyNecklaceMenu;
import com.skylogistics.network.ModNetworking;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SkyNecklaceScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<SkyNecklaceMenu> {
    private static final int FILTER_ROW_Y = 66;
    private final List<LineButton> lineButtons = new ArrayList<>();
    private final List<ModeButton> modeButtons = new ArrayList<>();
    private final List<InsertSlotsButton> insertSlotsButtons = new ArrayList<>();

    public SkyNecklaceScreen(SkyNecklaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 254;
        imageHeight = 240;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        lineButtons.clear();
        modeButtons.clear();
        insertSlotsButtons.clear();
        addLineButton(leftPos + 116, topPos + 29, 22, Component.literal("|<"), MenuAction.LINE_FIRST);
        addLineButton(leftPos + 141, topPos + 29, 20, Component.literal("<"), MenuAction.LINE_PREVIOUS);
        addLineButton(leftPos + 164, topPos + 29, 24, Component.literal(">+"), MenuAction.LINE_NEXT_OR_CREATE);
        addLineButton(leftPos + 191, topPos + 29, 22, Component.literal(">|"), MenuAction.LINE_LAST);
        addLineButton(leftPos + 216, topPos + 29, 18, Component.literal("x"), MenuAction.LINE_REMOVE_CURRENT);
        addModeButton(leftPos + 54, topPos + 86, 70, SkyNecklaceItem.NecklaceMode.EXTRACT, MenuAction.MODE_EXTRACT);
        addModeButton(leftPos + 130, topPos + 86, 70, SkyNecklaceItem.NecklaceMode.INSERT, MenuAction.MODE_INSERT);
        addInsertSlotsButton(leftPos + 54, topPos + 110, Component.literal("-"),
                MenuAction.NECKLACE_INSERT_SLOTS_DOWN);
        addInsertSlotsButton(leftPos + 130, topPos + 110, Component.literal("+"),
                MenuAction.NECKLACE_INSERT_SLOTS_UP);
    }

    private void addLineButton(int x, int y, int width, Component message, int action) {
        LineButton button = new LineButton(x, y, width, message, action);
        lineButtons.add(button);
        addRenderableWidget(button);
    }

    private void addModeButton(int x, int y, int width, SkyNecklaceItem.NecklaceMode mode, int action) {
        ModeButton button = new ModeButton(x, y, width, mode, action);
        modeButtons.add(button);
        addRenderableWidget(button);
    }

    private void addInsertSlotsButton(int x, int y, Component message, int action) {
        InsertSlotsButton button = new InsertSlotsButton(x, y, message, action);
        insertSlotsButtons.add(button);
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
        for (ModeButton button : modeButtons) {
            button.refresh(SkyNecklaceItem.mode(stack));
        }
        for (InsertSlotsButton button : insertSlotsButtons) {
            button.refresh(stack);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ConfigPanel.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        renderMenuSlotBackgrounds(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack stack = stack();
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.read(stack);
        graphics.drawString(font, title, 14, 12, ConfigPanel.ACCENT, false);
        if (config == null) {
            graphics.drawString(font, Component.translatable("screen.skylogistics.configurator.unbound"),
                    14, 42, ConfigPanel.MUTED, false);
        } else {
            int lineIndex = ConfiguratorItem.lineIndex(stack) + 1;
            int lineCount = Math.max(1, ConfiguratorItem.lineCount(stack));
            graphics.drawString(font, Component.translatable("screen.skylogistics.line_name",
                            config.lineName())
                    .append(Component.literal(" " + lineIndex + "/" + lineCount)),
                    14, 34, ConfigPanel.TEXT, false);
        }
        graphics.drawString(font, Component.translatable("screen.skylogistics.mode_label"),
                14, 92, ConfigPanel.MUTED, false);
        boolean insertMode = SkyNecklaceItem.mode(stack) == SkyNecklaceItem.NecklaceMode.INSERT;
        int insertTextColor = insertMode ? ConfigPanel.TEXT : ConfigPanel.MUTED;
        graphics.drawString(font, Component.translatable("screen.skylogistics.sky_necklace.insert_slots"),
                14, 116, insertTextColor, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.sky_necklace.insert_slots_value",
                        SkyNecklaceItem.insertSlots(stack)),
                82, 116, insertTextColor, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.sky_necklace.item_only"),
                14, FILTER_ROW_Y, ConfigPanel.MUTED, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.filter_slot"),
                SkyNecklaceMenu.FILTER_LABEL_X, FILTER_ROW_Y, ConfigPanel.MUTED, false);
        if (!SkyNecklaceItem.hasValidItemWhitelist(stack)) {
            graphics.drawString(font, Component.translatable("screen.skylogistics.sky_necklace.needs_whitelist"),
                    14, 134, 0xFFFF9A8A, false);
        }
    }

    private ItemStack stack() {
        return Minecraft.getInstance().player == null ? ItemStack.EMPTY
                : Minecraft.getInstance().player.getItemInHand(menu.getHand());
    }

    private void renderMenuSlotBackgrounds(GuiGraphics graphics) {
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConfigPanel.drawSlotBackground(graphics, leftPos + slot.x, topPos + slot.y);
            }
        }
    }

    private static final class LineButton extends AbstractButton {
        private final int action;

        private LineButton(int x, int y, int width, Component message, int action) {
            super(x, y, width, 20, message);
            this.action = action;
        }

        private void refresh(int index, int count) {
            active = switch (action) {
                case MenuAction.LINE_FIRST, MenuAction.LINE_PREVIOUS -> index > 0;
                case MenuAction.LINE_LAST -> count > 0 && index < count - 1;
                case MenuAction.LINE_REMOVE_CURRENT -> count > 1;
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
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, isHoveredOrFocused());
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + 6, active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static final class ModeButton extends AbstractButton {
        private final SkyNecklaceItem.NecklaceMode mode;
        private final int action;
        private boolean selected;

        private ModeButton(int x, int y, int width, SkyNecklaceItem.NecklaceMode mode, int action) {
            super(x, y, width, 20, Component.translatable(mode.translationKey()));
            this.mode = mode;
            this.action = action;
        }

        private void refresh(SkyNecklaceItem.NecklaceMode current) {
            selected = current == mode;
        }

        @Override
        public void onPress() {
            ModNetworking.sendMenuAction(action);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, selected);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + 6, ConfigPanel.TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static final class InsertSlotsButton extends AbstractButton {
        private final int action;

        private InsertSlotsButton(int x, int y, Component message, int action) {
            super(x, y, 22, 18, message);
            this.action = action;
        }

        private void refresh(ItemStack stack) {
            boolean insertMode = SkyNecklaceItem.mode(stack) == SkyNecklaceItem.NecklaceMode.INSERT;
            int slots = SkyNecklaceItem.insertSlots(stack);
            active = insertMode && switch (action) {
                case MenuAction.NECKLACE_INSERT_SLOTS_DOWN -> slots > SkyNecklaceItem.MIN_INSERT_SLOTS;
                case MenuAction.NECKLACE_INSERT_SLOTS_UP -> slots < SkyNecklaceItem.MAX_INSERT_SLOTS;
                default -> false;
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
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + 5, active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
