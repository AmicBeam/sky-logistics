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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class SkyNecklaceScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<SkyNecklaceMenu> {
    private static final int LINE_NAME_LABEL_Y = 12;
    private static final int LINE_NAME_LABEL_GAP = 4;
    private static final int LINE_NAME_EDIT_X = 146;
    private static final int LINE_NAME_EDIT_Y = 7;
    private static final int LINE_NAME_EDIT_WIDTH = 92;
    private static final int LINE_NAME_EDIT_HEIGHT = 16;
    private static final int TITLE_ROW_Y = 12;
    private static final int LINE_ROW_Y = 36;
    private static final int LINE_BUTTON_ROW_Y = 31;
    private static final int FILTER_ROW_Y = 60;
    private static final int MODE_ROW_Y = 84;
    private static final int MODE_BUTTON_ROW_Y = 78;
    private static final int INSERT_SLOTS_LABEL_X = 14;
    private static final int INSERT_SLOTS_ROW_Y = 102;
    private static final int PRIORITY_ROW_Y = 126;
    private static final int WARNING_Y = 146;
    private static final int ADJUST_DOWN_X = 54;
    private static final int ADJUST_VALUE_X = 76;
    private static final int ADJUST_VALUE_WIDTH = 54;
    private static final int ADJUST_UP_X = 130;
    private static final int ADJUST_BUTTON_WIDTH = 22;
    private static final int ADJUST_BUTTON_HEIGHT = 18;
    private final List<LineButton> lineButtons = new ArrayList<>();
    private final List<ModeButton> modeButtons = new ArrayList<>();
    private final List<InsertSlotsButton> insertSlotsButtons = new ArrayList<>();
    private final List<PriorityButton> priorityButtons = new ArrayList<>();
    private EditBox lineNameEdit;
    private boolean lineNameEditWasFocused;
    private UUID lineNameEditLine;

    public SkyNecklaceScreen(SkyNecklaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 254, 252);
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        lineButtons.clear();
        modeButtons.clear();
        insertSlotsButtons.clear();
        priorityButtons.clear();
        addLineButton(leftPos + 116, topPos + LINE_BUTTON_ROW_Y, 22, Component.literal("|<"), MenuAction.LINE_FIRST);
        addLineButton(leftPos + 141, topPos + LINE_BUTTON_ROW_Y, 20, Component.literal("<"), MenuAction.LINE_PREVIOUS);
        addLineButton(leftPos + 164, topPos + LINE_BUTTON_ROW_Y, 24, Component.literal(">+"), MenuAction.LINE_NEXT_OR_CREATE);
        addLineButton(leftPos + 191, topPos + LINE_BUTTON_ROW_Y, 22, Component.literal(">|"), MenuAction.LINE_LAST);
        addLineButton(leftPos + 216, topPos + LINE_BUTTON_ROW_Y, 18, Component.literal("x"), MenuAction.LINE_REMOVE_CURRENT);
        lineNameEdit = new EditBox(font, leftPos + LINE_NAME_EDIT_X, topPos + LINE_NAME_EDIT_Y,
                LINE_NAME_EDIT_WIDTH, LINE_NAME_EDIT_HEIGHT,
                Component.translatable("screen.skylogistics.line_name"));
        lineNameEdit.setMaxLength(48);
        ConfigPanel.styleEditBox(lineNameEdit);
        addRenderableWidget(lineNameEdit);
        addModeButton(leftPos + 54, topPos + MODE_BUTTON_ROW_Y, 70, SkyNecklaceItem.NecklaceMode.EXTRACT,
                MenuAction.MODE_EXTRACT);
        addModeButton(leftPos + 130, topPos + MODE_BUTTON_ROW_Y, 70, SkyNecklaceItem.NecklaceMode.INSERT,
                MenuAction.MODE_INSERT);
        addInsertSlotsButton(leftPos + ADJUST_DOWN_X, topPos + INSERT_SLOTS_ROW_Y, Component.literal("-"),
                MenuAction.NECKLACE_INSERT_SLOTS_DOWN, MenuAction.NECKLACE_INSERT_SLOTS_DOWN_FAST);
        addInsertSlotsButton(leftPos + ADJUST_UP_X, topPos + INSERT_SLOTS_ROW_Y, Component.literal("+"),
                MenuAction.NECKLACE_INSERT_SLOTS_UP, MenuAction.NECKLACE_INSERT_SLOTS_UP_FAST);
        addPriorityButton(leftPos + ADJUST_DOWN_X, topPos + PRIORITY_ROW_Y, Component.literal("-"),
                MenuAction.NECKLACE_PRIORITY_DOWN, MenuAction.NECKLACE_PRIORITY_DOWN_FAST);
        addPriorityButton(leftPos + ADJUST_UP_X, topPos + PRIORITY_ROW_Y, Component.literal("+"),
                MenuAction.NECKLACE_PRIORITY_UP, MenuAction.NECKLACE_PRIORITY_UP_FAST);
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

    private void addInsertSlotsButton(int x, int y, Component message, int action, int fastAction) {
        InsertSlotsButton button = new InsertSlotsButton(x, y, message, action, fastAction);
        insertSlotsButtons.add(button);
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
        for (InsertSlotsButton button : insertSlotsButtons) {
            button.refresh(stack);
        }
        for (PriorityButton button : priorityButtons) {
            button.refresh(stack);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        ConfigPanel.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        if (lineNameEdit != null && lineNameEdit.visible) {
            ConfigPanel.drawInputBox(graphics, leftPos + LINE_NAME_EDIT_X, topPos + LINE_NAME_EDIT_Y,
                    LINE_NAME_EDIT_WIDTH, LINE_NAME_EDIT_HEIGHT, lineNameEdit.isFocused());
        }
        ConfigPanel.drawContentPanel(graphics, leftPos + 8, topPos + 54, imageWidth - 16, 45);
        ConfigPanel.drawContentPanel(graphics, leftPos + 8, topPos + 100, imageWidth - 16, 51);
        ConfigPanel.drawInventoryPanel(graphics, leftPos + 40, topPos + 153, 174, 88);
        renderMenuSlotBackgrounds(graphics);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ItemStack stack = stack();
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.read(stack);
        graphics.text(font, title, 14, TITLE_ROW_Y, ConfigPanel.ACCENT, false);
        if (config == null) {
            graphics.text(font, Component.translatable("screen.skylogistics.configurator.unbound"),
                    14, LINE_ROW_Y, ConfigPanel.MUTED, false);
        } else {
            int lineIndex = menu.getLineIndex() + 1;
            int lineCount = Math.max(1, menu.getLineCount());
            Component lineNameLabel = Component.translatable("screen.skylogistics.line_name");
            graphics.text(font, lineNameLabel,
                    LINE_NAME_EDIT_X - LINE_NAME_LABEL_GAP - font.width(lineNameLabel),
                    LINE_NAME_LABEL_Y, ConfigPanel.MUTED, false);
            graphics.text(font, Component.translatable("screen.skylogistics.line_index", lineIndex, lineCount),
                    14, LINE_ROW_Y, ConfigPanel.TEXT, false);
        }
        graphics.text(font, Component.translatable("screen.skylogistics.mode_label"),
                14, MODE_ROW_Y, ConfigPanel.MUTED, false);
        graphics.text(font, Component.translatable("screen.skylogistics.sky_necklace.insert_slots"),
                14, INSERT_SLOTS_ROW_Y + 6, ConfigPanel.MUTED, false);
        graphics.centeredText(font, SkyNecklaceItem.insertSlotsDisplay(stack),
                ADJUST_VALUE_X + ADJUST_VALUE_WIDTH / 2, INSERT_SLOTS_ROW_Y + 5, ConfigPanel.TEXT);
        graphics.text(font, Component.translatable("screen.skylogistics.priority"),
                14, PRIORITY_ROW_Y + 6, ConfigPanel.MUTED, false);
        graphics.centeredText(font, Component.literal(String.valueOf(SkyNecklaceItem.priority(stack))),
                ADJUST_VALUE_X + ADJUST_VALUE_WIDTH / 2, PRIORITY_ROW_Y + 5, ConfigPanel.TEXT);
        graphics.text(font, Component.translatable("screen.skylogistics.sky_necklace.item_only"),
                14, FILTER_ROW_Y, ConfigPanel.MUTED, false);
        graphics.text(font, Component.translatable("screen.skylogistics.filter_slot"),
                SkyNecklaceMenu.FILTER_LABEL_X, FILTER_ROW_Y, ConfigPanel.MUTED, false);
        if (!SkyNecklaceItem.hasValidItemWhitelist(stack)) {
            graphics.text(font, Component.translatable("screen.skylogistics.sky_necklace.needs_whitelist"),
                    14, WARNING_Y, 0xFFFF9A8A, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int x, int y) {
        if (isMouseOverInsertSlotsLabel(x, y)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable(
                    "tooltip.skylogistics.sky_necklace.insert_slots_hint")), x, y);
            return;
        }
        super.extractTooltip(graphics, x, y);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (lineNameEdit != null && lineNameEdit.isFocused()
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            commitLineNameEdit();
            lineNameEdit.setFocused(false);
            setFocused(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (lineNameEdit != null && lineNameEdit.isFocused() && !lineNameEdit.isMouseOver(mouseX, mouseY)) {
            commitLineNameEdit();
            lineNameEdit.setFocused(false);
            setFocused(null);
        }
        return super.mouseClicked(event, doubleClick);
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
        Component label = Component.translatable("screen.skylogistics.sky_necklace.insert_slots");
        int labelY = INSERT_SLOTS_ROW_Y + 6;
        int labelWidth = Math.min(font.width(label), ADJUST_DOWN_X - INSERT_SLOTS_LABEL_X - 2);
        return x >= leftPos + INSERT_SLOTS_LABEL_X
                && x < leftPos + INSERT_SLOTS_LABEL_X + labelWidth
                && y >= topPos + labelY
                && y < topPos + labelY + font.lineHeight;
    }

    private ItemStack stack() {
        return Minecraft.getInstance().player == null ? ItemStack.EMPTY
                : Minecraft.getInstance().player.getItemInHand(menu.getHand());
    }

    private void renderMenuSlotBackgrounds(GuiGraphicsExtractor graphics) {
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConfigPanel.drawSlotBackground(graphics, leftPos + slot.x, topPos + slot.y);
            }
        }
    }

    private final class LineButton extends AbstractButton {
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
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (active) {
                commitLineNameEdit();
                ModNetworking.sendMenuAction(action);
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, isHoveredOrFocused());
            graphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
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
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            ModNetworking.sendMenuAction(action);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, selected);
            graphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + 6, ConfigPanel.TEXT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static final class InsertSlotsButton extends AbstractButton {
        private final int action;
        private final int fastAction;

        private InsertSlotsButton(int x, int y, Component message, int action, int fastAction) {
            super(x, y, ADJUST_BUTTON_WIDTH, ADJUST_BUTTON_HEIGHT, message);
            this.action = action;
            this.fastAction = fastAction;
        }

        private void refresh(ItemStack stack) {
            int slots = SkyNecklaceItem.insertSlots(stack);
            active = switch (action) {
                case MenuAction.NECKLACE_INSERT_SLOTS_DOWN -> slots > SkyNecklaceItem.MIN_INSERT_SLOTS;
                case MenuAction.NECKLACE_INSERT_SLOTS_UP -> slots < SkyNecklaceItem.MAX_INSERT_SLOTS;
                default -> false;
            };
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (active) {
                int selectedAction = input.hasShiftDown() ? fastAction : action;
                ModNetworking.sendMenuAction(selectedAction);
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
            graphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + 5, active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
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
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (active) {
                int selectedAction = input.hasShiftDown() ? fastAction : action;
                ModNetworking.sendMenuAction(selectedAction);
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
            graphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + 5, active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
