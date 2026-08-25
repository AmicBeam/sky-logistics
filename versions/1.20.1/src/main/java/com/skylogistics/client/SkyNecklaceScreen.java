package com.skylogistics.client;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.SkyNecklaceItem;
import com.skylogistics.menu.MenuAction;
import com.skylogistics.menu.SkyNecklaceMenu;
import com.skylogistics.network.ModNetworking;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class SkyNecklaceScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<SkyNecklaceMenu> {
    private static final int LINE_PANEL_X = 5;
    private static final int LINE_PANEL_Y = 20;
    private static final int LINE_PANEL_WIDTH = 244;
    private static final int LINE_NAME_LABEL_Y = 28;
    private static final int LINE_NAME_LABEL_GAP = 4;
    private static final int LINE_NAME_EDIT_X = 37;
    private static final int LINE_NAME_EDIT_Y = 24;
    private static final int LINE_NAME_EDIT_WIDTH = 90;
    private static final int LINE_NAME_EDIT_HEIGHT = 15;
    private static final int LINE_COUNT_CENTER_X = 144;
    private static final int TITLE_ROW_Y = 7;
    private static final int UPGRADE_FILTER_GROUP_Y = 83;
    private static final int UPGRADE_GROUP_X = 5;
    private static final int FILTER_GROUP_X = 129;
    private static final int UPGRADE_FILTER_GROUP_WIDTH = 120;
    private static final int UPGRADE_FILTER_GROUP_HEIGHT = 27;
    private static final int MODE_GROUP_X = 5;
    private static final int MODE_GROUP_Y = 48;
    private static final int MODE_GROUP_WIDTH = 244;
    private static final int MODE_BUTTON_ROW_Y = 55;
    private static final int MODE_BUTTON_X = 12;
    private static final int MODE_BUTTON_WIDTH = 74;
    private static final int MODE_BUTTON_STEP = 78;
    private static final int BOTTOM_GROUP_Y = 118;
    private static final int BOTTOM_CONTROL_Y = 125;
    private static final int SLOT_GROUP_X = 5;
    private static final int PRIORITY_GROUP_X = 129;
    private static final int BOTTOM_GROUP_WIDTH = 120;
    private static final int SLOT_VALUE_X = 12;
    private static final int SLOT_VALUE_WIDTH = 78;
    private static final int MAINTAIN_UNIT_X = 93;
    private static final int MAINTAIN_UNIT_WIDTH = 25;
    private static final int PRIORITY_DOWN_X = 138;
    private static final int PRIORITY_VALUE_X = 158;
    private static final int PRIORITY_VALUE_WIDTH = 62;
    private static final int PRIORITY_UP_X = 223;
    private static final int ADJUST_BUTTON_WIDTH = 17;
    private static final int ADJUST_BUTTON_HEIGHT = ConfigPanel.STEPPER_HEIGHT;
    private static final int MAINTAIN_ACCENT = ConfigPanel.MAINTAIN_ACCENT;
    private final List<LineButton> lineButtons = new ArrayList<>();
    private final List<ModeButton> modeButtons = new ArrayList<>();
    private final List<PriorityButton> priorityButtons = new ArrayList<>();
    private EditBox lineNameEdit;
    private EditBox maintainAmountEdit;
    private boolean refreshingMaintainAmount;
    private boolean lineNameEditWasFocused;
    private UUID lineNameEditLine;

    public SkyNecklaceScreen(SkyNecklaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 254;
        imageHeight = 242;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        lineButtons.clear();
        modeButtons.clear();
        priorityButtons.clear();
        addLineButton(leftPos + 157, topPos + 23, 15, Component.literal("|<"), MenuAction.LINE_FIRST);
        addLineButton(leftPos + 174, topPos + 23, 15, Component.literal("<"), MenuAction.LINE_PREVIOUS);
        addLineButton(leftPos + 191, topPos + 23, 15, Component.literal(">+"), MenuAction.LINE_NEXT_OR_CREATE);
        addLineButton(leftPos + 208, topPos + 23, 15, Component.literal(">|"), MenuAction.LINE_LAST);
        addLineButton(leftPos + 225, topPos + 23, 15, Component.literal("x"), MenuAction.LINE_REMOVE_CURRENT);
        lineNameEdit = new EditBox(font, leftPos + LINE_NAME_EDIT_X, topPos + LINE_NAME_EDIT_Y,
                LINE_NAME_EDIT_WIDTH, LINE_NAME_EDIT_HEIGHT,
                Component.translatable("screen.skylogistics.line_name"));
        lineNameEdit.setMaxLength(48);
        lineNameEdit.setTextColor(ConfigPanel.FIELD_TEXT);
        lineNameEdit.setTextColorUneditable(ConfigPanel.MUTED);
        addRenderableWidget(lineNameEdit);
        addModeButton(leftPos + MODE_BUTTON_X, topPos + MODE_BUTTON_ROW_Y, MODE_BUTTON_WIDTH, SkyNecklaceItem.NecklaceMode.EXTRACT,
                MenuAction.MODE_EXTRACT);
        addModeButton(leftPos + MODE_BUTTON_X + MODE_BUTTON_STEP, topPos + MODE_BUTTON_ROW_Y, MODE_BUTTON_WIDTH, SkyNecklaceItem.NecklaceMode.INSERT,
                MenuAction.MODE_INSERT);
        addModeButton(leftPos + MODE_BUTTON_X + MODE_BUTTON_STEP * 2, topPos + MODE_BUTTON_ROW_Y, MODE_BUTTON_WIDTH, SkyNecklaceItem.NecklaceMode.MAINTAIN,
                MenuAction.MODE_MAINTAIN);
        addRenderableWidget(new MaintainUnitButton(leftPos + MAINTAIN_UNIT_X, topPos + BOTTOM_CONTROL_Y));
        addPriorityButton(leftPos + PRIORITY_DOWN_X, topPos + BOTTOM_CONTROL_Y, Component.literal("-"),
                MenuAction.NECKLACE_PRIORITY_DOWN, MenuAction.NECKLACE_PRIORITY_DOWN_FAST);
        addPriorityButton(leftPos + PRIORITY_UP_X, topPos + BOTTOM_CONTROL_Y, Component.literal("+"),
                MenuAction.NECKLACE_PRIORITY_UP, MenuAction.NECKLACE_PRIORITY_UP_FAST);
        maintainAmountEdit = new EditBox(font, leftPos + SLOT_VALUE_X, topPos + BOTTOM_CONTROL_Y,
                SLOT_VALUE_WIDTH, ADJUST_BUTTON_HEIGHT, Component.translatable("screen.skylogistics.sky_necklace.maintain_amount"));
        maintainAmountEdit.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        maintainAmountEdit.setMaxLength(10);
        maintainAmountEdit.setResponder(this::maintainAmountChanged);
        centerEditText(maintainAmountEdit);
        addRenderableWidget(maintainAmountEdit);
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

    private void addPriorityButton(int x, int y, Component message, int action, int fastAction) {
        PriorityButton button = new PriorityButton(x, y, message, action, fastAction);
        priorityButtons.add(button);
        addRenderableWidget(button);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack stack = stack();
        int index = menu.getLineIndex();
        int count = menu.getLineCount();
        for (LineButton button : lineButtons) {
            button.refresh(index, count);
        }
        refreshLineNameEdit(ConfiguratorItem.read(stack));
        for (ModeButton button : modeButtons) {
            button.refresh(SkyNecklaceItem.mode(stack));
        }
        for (PriorityButton button : priorityButtons) {
            button.refresh(stack);
        }
        refreshMaintainAmount(stack);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ConfigPanel.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        ConfigPanel.drawContentPanel(graphics, leftPos + LINE_PANEL_X, topPos + LINE_PANEL_Y,
                LINE_PANEL_WIDTH, 24);
        ConfigPanel.drawFieldset(graphics, leftPos + UPGRADE_GROUP_X, topPos + UPGRADE_FILTER_GROUP_Y,
                UPGRADE_FILTER_GROUP_WIDTH, font.width(Component.translatable("screen.skylogistics.upgrade_slots")),
                UPGRADE_FILTER_GROUP_HEIGHT);
        ConfigPanel.drawFieldset(graphics, leftPos + FILTER_GROUP_X, topPos + UPGRADE_FILTER_GROUP_Y,
                UPGRADE_FILTER_GROUP_WIDTH, font.width(Component.translatable("screen.skylogistics.filter_slot")),
                UPGRADE_FILTER_GROUP_HEIGHT);
        ConfigPanel.drawFieldset(graphics, leftPos + MODE_GROUP_X, topPos + MODE_GROUP_Y,
                MODE_GROUP_WIDTH, font.width(Component.translatable("screen.skylogistics.mode_label")));
        Component slotLegend = Component.translatable("screen.skylogistics.sky_necklace.maintain_amount");
        ConfigPanel.drawFieldset(graphics, leftPos + SLOT_GROUP_X, topPos + BOTTOM_GROUP_Y,
                BOTTOM_GROUP_WIDTH, font.width(slotLegend));
        ConfigPanel.drawFieldset(graphics, leftPos + PRIORITY_GROUP_X, topPos + BOTTOM_GROUP_Y,
                BOTTOM_GROUP_WIDTH, font.width(Component.translatable("screen.skylogistics.priority")));
        ConfigPanel.drawStepperValue(graphics, leftPos + PRIORITY_VALUE_X, topPos + BOTTOM_CONTROL_Y,
                PRIORITY_VALUE_WIDTH);
        renderMenuSlotBackgrounds(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack stack = stack();
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.read(stack);
        graphics.drawString(font, title, 10, TITLE_ROW_Y, ConfigPanel.TEXT, false);
        if (config == null) {
            graphics.drawString(font, Component.translatable("screen.skylogistics.configurator.unbound"),
                    10, 28, ConfigPanel.MUTED, false);
        } else {
            int lineIndex = menu.getLineIndex() + 1;
            int lineCount = Math.max(1, menu.getLineCount());
            Component lineNameLabel = Component.translatable("screen.skylogistics.configurator.line");
            graphics.drawString(font, lineNameLabel,
                    LINE_NAME_EDIT_X - LINE_NAME_LABEL_GAP - font.width(lineNameLabel),
                    LINE_NAME_LABEL_Y, ConfigPanel.MUTED, false);
            ConfigPanel.drawCenteredText(graphics, font, Component.literal(lineIndex + "/" + lineCount),
                    LINE_COUNT_CENTER_X, 28, ConfigPanel.TEXT);
        }
        int slotLegendY = UPGRADE_FILTER_GROUP_Y - 4;
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.upgrade_slots"),
                UPGRADE_GROUP_X + UPGRADE_FILTER_GROUP_WIDTH / 2, slotLegendY, ConfigPanel.MUTED);
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.filter_slot"),
                FILTER_GROUP_X + UPGRADE_FILTER_GROUP_WIDTH / 2, slotLegendY, ConfigPanel.MUTED);
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.mode_label"),
                MODE_GROUP_X + MODE_GROUP_WIDTH / 2, MODE_GROUP_Y - 4, ConfigPanel.MUTED);
        Component slotLegend = Component.translatable("screen.skylogistics.sky_necklace.maintain_amount");
        ConfigPanel.drawCenteredText(graphics, font, slotLegend,
                SLOT_GROUP_X + BOTTOM_GROUP_WIDTH / 2, BOTTOM_GROUP_Y - 4, ConfigPanel.MUTED);
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.priority"),
                PRIORITY_GROUP_X + BOTTOM_GROUP_WIDTH / 2, BOTTOM_GROUP_Y - 4, ConfigPanel.MUTED);
        ConfigPanel.drawCenteredText(graphics, font, Component.literal(String.valueOf(SkyNecklaceItem.priority(stack))),
                PRIORITY_VALUE_X + PRIORITY_VALUE_WIDTH / 2, BOTTOM_CONTROL_Y + 4, ConfigPanel.FIELD_TEXT);
        if (!SkyNecklaceItem.hasValidItemWhitelist(stack)) {
            Component warning = Component.translatable("screen.skylogistics.sky_necklace.needs_whitelist");
            graphics.drawString(font, warning, imageWidth - 10 - font.width(warning), TITLE_ROW_Y,
                    0xFFB84343, false);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (isMouseOverInsertSlotsLabel(x, y)) {
            graphics.renderComponentTooltip(font, List.of(Component.translatable(
                    "tooltip.skylogistics.sky_necklace.insert_slots_hint")), x, y);
            return;
        }
        super.renderTooltip(graphics, x, y);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((lineNameEdit != null && lineNameEdit.isFocused()
                || maintainAmountEdit != null && maintainAmountEdit.isFocused())
                && minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        if (lineNameEdit != null && lineNameEdit.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            commitLineNameEdit();
            lineNameEdit.setFocused(false);
            setFocused(null);
            return true;
        }
        if (maintainAmountEdit != null && maintainAmountEdit.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            maintainAmountEdit.setFocused(false);
            setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (lineNameEdit != null && lineNameEdit.isFocused() && !lineNameEdit.isMouseOver(mouseX, mouseY)) {
            commitLineNameEdit();
            lineNameEdit.setFocused(false);
            setFocused(null);
        }
        if (maintainAmountEdit != null && maintainAmountEdit.isFocused()
                && !maintainAmountEdit.isMouseOver(mouseX, mouseY)) {
            maintainAmountEdit.setFocused(false);
            setFocused(null);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        commitLineNameEdit();
        super.removed();
    }

    private void refreshLineNameEdit(ConfiguratorItem.ToolConfig currentConfig) {
        if (lineNameEdit == null) {
            return;
        }
        boolean focused = lineNameEdit.isFocused();
        if (lineNameEditWasFocused && !focused) {
            commitLineNameEdit();
            currentConfig = ConfiguratorItem.read(stack());
            focused = lineNameEdit.isFocused();
        }
        lineNameEditWasFocused = focused;
        lineNameEdit.visible = currentConfig != null;
        lineNameEdit.active = currentConfig != null;
        if (currentConfig == null) {
            lineNameEditLine = null;
            lineNameEdit.setValue("");
            return;
        }
        String displayName = displayLineName(currentConfig);
        if (!focused && (!currentConfig.lineId().equals(lineNameEditLine)
                || !lineNameEdit.getValue().equals(displayName))) {
            lineNameEditLine = currentConfig.lineId();
            lineNameEdit.setValue(displayName);
        }
    }

    private void commitLineNameEdit() {
        if (lineNameEdit == null) {
            return;
        }
        ConfiguratorItem.ToolConfig currentConfig = ConfiguratorItem.read(stack());
        if (currentConfig == null) {
            return;
        }
        String oldName = displayLineName(currentConfig);
        String assignedName = assignedLineName(currentConfig);
        String newName = ClientLineNames.editedName(currentConfig.lineId(), lineNameEdit.getValue(), assignedName);
        ClientLineNames.apply(currentConfig.lineId(), assignedName, newName);
        lineNameEditLine = currentConfig.lineId();
        lineNameEdit.setValue(newName);
        if (!oldName.equals(newName)) {
            ModNetworking.sendLineRename(lineNameEdit.getValue());
        }
    }

    private String displayLineName(ConfiguratorItem.ToolConfig config) {
        return ClientLineNames.displayName(config.lineId(), config.lineName());
    }

    private String assignedLineName(ConfiguratorItem.ToolConfig config) {
        return ClientLineNames.assignedName(config.lineId(), ConfiguratorItem.assignedLineName(stack()));
    }

    private boolean isMouseOverInsertSlotsLabel(int x, int y) {
        Component label = Component.translatable("screen.skylogistics.sky_necklace.maintain_amount");
        int labelY = BOTTOM_GROUP_Y - 4;
        int labelX = SLOT_GROUP_X + (BOTTOM_GROUP_WIDTH - font.width(label)) / 2;
        return x >= leftPos + labelX
                && x < leftPos + labelX + font.width(label)
                && y >= topPos + labelY
                && y < topPos + labelY + font.lineHeight;
    }

    private ItemStack stack() {
        return Minecraft.getInstance().player == null ? ItemStack.EMPTY
                : Minecraft.getInstance().player.getItemInHand(menu.getHand());
    }

    private void refreshMaintainAmount(ItemStack stack) {
        if (maintainAmountEdit == null || maintainAmountEdit.isFocused()) return;
        String value = String.valueOf(SkyNecklaceItem.maintainAmount(stack));
        if (!value.equals(maintainAmountEdit.getValue())) {
            refreshingMaintainAmount = true;
            maintainAmountEdit.setValue(value);
            refreshingMaintainAmount = false;
        }
    }

    private void centerEditText(EditBox editBox) {
        editBox.setFormatter((text, start) -> {
            int spaceWidth = Math.max(1, font.width(" "));
            int padding = start == 0
                    ? Math.max(0, (editBox.getWidth() - 8 - font.width(editBox.getValue())) / 2 / spaceWidth)
                    : 0;
            return net.minecraft.util.FormattedCharSequence.forward(" ".repeat(padding) + text,
                    net.minecraft.network.chat.Style.EMPTY);
        });
    }

    private void maintainAmountChanged(String value) {
        if (refreshingMaintainAmount || value.isEmpty()) return;
        try {
            long parsed = Long.parseLong(value);
            ModNetworking.sendExactQuantity((int) Math.min(Integer.MAX_VALUE, Math.max(0L, parsed)));
        } catch (NumberFormatException ignored) { }
    }

    private void renderMenuSlotBackgrounds(GuiGraphics graphics) {
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConfigPanel.drawSlotBackground(graphics, leftPos + slot.x, topPos + slot.y);
            }
        }
    }

    private final class LineButton extends AbstractButton {
        private final int action;

        private LineButton(int x, int y, int width, Component message, int action) {
            super(x, y, width, 17, message);
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
                commitLineNameEdit();
                ModNetworking.sendMenuAction(action);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, isHovered());
            ConfigPanel.drawCenteredButtonText(graphics, Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + 5, active);
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
            super(x, y, width, 21, Component.translatable(mode.translationKey()));
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
            int accent = mode == SkyNecklaceItem.NecklaceMode.EXTRACT ? ConfigPanel.EXTRACT_ACCENT
                    : mode == SkyNecklaceItem.NecklaceMode.INSERT ? ConfigPanel.INSERT_ACCENT : MAINTAIN_ACCENT;
            ConfigPanel.drawImageButtonChrome(graphics, getX(), getY(), width, height, active, isHovered(), selected, accent);
            net.minecraft.client.gui.Font buttonFont = Minecraft.getInstance().font;
            int contentX = getX() + (width - 20 - buttonFont.width(getMessage())) / 2;
            int iconColor = ConfigPanel.buttonTextColor(active);
            if (mode == SkyNecklaceItem.NecklaceMode.MAINTAIN) {
                ConfigPanel.drawResourceIcon(graphics, contentX, getY() + 2, "auto_white", true);
            } else {
                drawModeArrow(graphics, contentX, getY() + 2,
                        mode == SkyNecklaceItem.NecklaceMode.EXTRACT, iconColor);
            }
            graphics.drawString(buttonFont, getMessage(), contentX + 20,
                    getY() + 7, ConfigPanel.buttonTextColor(active), true);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static void drawModeArrow(GuiGraphics graphics, int x, int y, boolean up, int color) {
        if (up) {
            graphics.fill(x + 8, y + 2, x + 10, y + 4, color);
            graphics.fill(x + 6, y + 4, x + 12, y + 6, color);
            graphics.fill(x + 4, y + 6, x + 14, y + 8, color);
            graphics.fill(x + 8, y + 8, x + 10, y + 15, color);
        } else {
            graphics.fill(x + 8, y + 2, x + 10, y + 9, color);
            graphics.fill(x + 4, y + 9, x + 14, y + 11, color);
            graphics.fill(x + 6, y + 11, x + 12, y + 13, color);
            graphics.fill(x + 8, y + 13, x + 10, y + 15, color);
        }
    }

    private final class MaintainUnitButton extends AbstractButton {
        private MaintainUnitButton(int x, int y) {
            super(x, y, MAINTAIN_UNIT_WIDTH, ADJUST_BUTTON_HEIGHT, Component.empty());
        }

        @Override
        public void onPress() {
            ModNetworking.sendMenuAction(MenuAction.NECKLACE_TOGGLE_MAINTAIN_UNIT);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, isHovered());
            Component unit = Component.translatable(SkyNecklaceItem.maintainByItems(stack())
                    ? "screen.skylogistics.sky_necklace.unit.items"
                    : "screen.skylogistics.sky_necklace.unit.slots");
            ConfigPanel.drawCenteredButtonText(graphics, Minecraft.getInstance().font, unit, getX() + width / 2,
                    getY() + 5, active);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static final class PriorityButton extends AbstractButton {
        private final int action;
        private final int fastAction;

        private PriorityButton(int x, int y, Component message, int action, int fastAction) {
            super(x, y, ADJUST_BUTTON_WIDTH, ADJUST_BUTTON_HEIGHT, message);
            this.action = action;
            this.fastAction = fastAction;
        }

        private void refresh(ItemStack stack) {
            int priority = SkyNecklaceItem.priority(stack);
            active = stack.is(com.skylogistics.registry.ModItems.SKY_NECKLACE.get()) && switch (action) {
                case MenuAction.NECKLACE_PRIORITY_DOWN -> priority > SkyNecklaceItem.MIN_PRIORITY;
                case MenuAction.NECKLACE_PRIORITY_UP -> priority < SkyNecklaceItem.MAX_PRIORITY;
                default -> false;
            };
        }

        @Override
        public void onPress() {
            if (active) {
                int selectedAction = net.minecraft.client.gui.screens.Screen.hasShiftDown() ? fastAction : action;
                ModNetworking.sendMenuAction(selectedAction);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, isHovered());
            ConfigPanel.drawCenteredButtonText(graphics, Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + 5, active);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
