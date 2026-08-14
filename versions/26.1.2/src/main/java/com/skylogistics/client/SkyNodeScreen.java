package com.skylogistics.client;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.menu.MenuAction;
import com.skylogistics.menu.SkyNodeMenu;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.util.NodeFaceMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lwjgl.glfw.GLFW;

public class SkyNodeScreen extends AbstractContainerScreen<SkyNodeMenu> {
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
    private static final Direction[] FACE_ORDER = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final int FACE_PANEL_X = -34;
    private static final int FACE_PANEL_Y = 46;
    private static final int FACE_PANEL_WIDTH = 34;
    private static final int FACE_PANEL_HEIGHT = 177;
    private static final int FACE_BUTTON_X = -32;
    private static final int FACE_BUTTON_Y = 48;
    private static final int FACE_BUTTON_WIDTH = 30;
    private static final int FACE_BUTTON_HEIGHT = 28;
    private static final int FACE_BUTTON_STEP = 29;
    private static final int RESOURCE_GROUP_Y = 48;
    private static final int RESOURCE_CONTROL_Y = 55;
    private static final int RESOURCE_GROUP_X = 5;
    private static final int MODE_GROUP_X = 129;
    private static final int RESOURCE_MODE_GROUP_WIDTH = 120;
    private static final int RESOURCE_BUTTON_WIDTH = 34;
    private static final int RESOURCE_BUTTON_STEP = 38;
    private static final int ADVANCED_GROUP_Y = 83;
    private static final int ADVANCED_CONTROL_Y = 90;
    private static final int REDSTONE_GROUP_X = 5;
    private static final int REDSTONE_GROUP_WIDTH = 74;
    private static final int SLOT_LIMIT_GROUP_X = 83;
    private static final int SLOT_LIMIT_GROUP_WIDTH = 79;
    private static final int PRIORITY_GROUP_X = 166;
    private static final int PRIORITY_GROUP_WIDTH = 83;
    private static final int REDSTONE_CONTROL_X = 10;
    private static final int ADVANCED_CONTROL_WIDTH = 64;
    private static final int PRIORITY_BUTTON_WIDTH = 17;
    private static final int SLOT_LIMIT_DOWN_X = 87;
    private static final int SLOT_LIMIT_VALUE_X = 107;
    private static final int SLOT_LIMIT_VALUE_WIDTH = 31;
    private static final int SLOT_LIMIT_UP_X = 141;
    private static final int PRIORITY_DOWN_X = 172;
    private static final int PRIORITY_VALUE_X = 192;
    private static final int PRIORITY_VALUE_WIDTH = 31;
    private static final int PRIORITY_UP_X = 226;
    private static final int UPGRADE_FILTER_GROUP_Y = 118;
    private static final int UPGRADE_GROUP_X = 5;
    private static final int FILTER_GROUP_X = 129;
    private static final int UPGRADE_FILTER_GROUP_WIDTH = 120;
    private static final int UPGRADE_FILTER_GROUP_HEIGHT = 27;
    private final EnumMap<Direction, NodeFaceMode> localFaceModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, FaceButton> faceButtons = new EnumMap<>(Direction.class);
    private final List<LineButton> lineButtons = new ArrayList<>();
    private final List<TypeToggleButton> typeButtons = new ArrayList<>();
    private final List<ModeButton> modeButtons = new ArrayList<>();
    private final List<AdvancedButton> advancedButtons = new ArrayList<>();
    private Boolean localItemsEnabled;
    private Boolean localFluidsEnabled;
    private Boolean localEnergyEnabled;
    private Direction selectedFace = Direction.NORTH;
    private EditBox lineNameEdit;
    private EditBox exactQuantityEdit;
    private boolean refreshingExactQuantity;
    private boolean lineNameEditWasFocused;
    private UUID lineNameEditLine;
    private Direction tagFilterRejectedFace;
    private int tagFilterRejectedSlot = -1;
    private ItemStack tagFilterRejectedPrevious = ItemStack.EMPTY;

    public SkyNodeScreen(SkyNodeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 254,
                menu.isSingleEndpoint()
                        ? SkyNodeMenu.PANEL_HEIGHT - SkyNodeMenu.SINGLE_ENDPOINT_VERTICAL_SHIFT
                        : SkyNodeMenu.PANEL_HEIGHT);
        inventoryLabelY = menu.screenY(SkyNodeMenu.PLAYER_INVENTORY_LABEL_Y);
    }

    @Override
    protected void init() {
        super.init();
        faceButtons.clear();
        lineButtons.clear();
        typeButtons.clear();
        modeButtons.clear();
        advancedButtons.clear();
        SkyNodeBlockEntity node = node();
        boolean singleEndpoint = node != null && node.usesSingleEndpoint();
        selectedFace = node == null ? Direction.NORTH : firstSelectableFace(node);
        menu.selectFace(selectedFace);
        ModNetworking.sendMenuAction(MenuAction.faceSelect(selectedFace));

        addLineButton(leftPos + 157, topPos + 23, 15, Component.literal("|<"), MenuAction.LINE_FIRST);
        addLineButton(leftPos + 174, topPos + 23, 15, Component.literal("<"), MenuAction.LINE_PREVIOUS);
        addLineButton(leftPos + 191, topPos + 23, 15, Component.literal(">+"), MenuAction.LINE_NEXT_OR_CREATE);
        addLineButton(leftPos + 208, topPos + 23, 15, Component.literal(">|"), MenuAction.LINE_LAST);
        addLineButton(leftPos + 225, topPos + 23, 15, Component.literal("x"), MenuAction.LINE_REMOVE_CURRENT);
        lineNameEdit = new EditBox(font, leftPos + LINE_NAME_EDIT_X, topPos + LINE_NAME_EDIT_Y,
                LINE_NAME_EDIT_WIDTH, LINE_NAME_EDIT_HEIGHT,
                Component.translatable("screen.skylogistics.line_name"));
        lineNameEdit.setMaxLength(48);
        lineNameEdit.setTextColor(ConfigPanel.TEXT);
        lineNameEdit.setTextColorUneditable(ConfigPanel.MUTED);
        addRenderableWidget(lineNameEdit);

        if (!singleEndpoint) {
            for (int i = 0; i < FACE_ORDER.length; i++) {
                Direction direction = FACE_ORDER[i];
                FaceButton button = new FaceButton(leftPos + FACE_BUTTON_X,
                        topPos + FACE_BUTTON_Y + i * FACE_BUTTON_STEP, direction);
                faceButtons.put(direction, button);
                addRenderableWidget(button);
            }
        }

        addTypeButton(leftPos + 10, topPos + menu.screenY(RESOURCE_CONTROL_Y), ResourceType.ITEMS);
        addTypeButton(leftPos + 10 + RESOURCE_BUTTON_STEP, topPos + menu.screenY(RESOURCE_CONTROL_Y), ResourceType.FLUIDS);
        addTypeButton(leftPos + 10 + RESOURCE_BUTTON_STEP * 2, topPos + menu.screenY(RESOURCE_CONTROL_Y), ResourceType.ENERGY);
        addModeButton(leftPos + 134, topPos + menu.screenY(RESOURCE_CONTROL_Y), RESOURCE_BUTTON_WIDTH, NodeFaceMode.NONE,
                Component.translatable("button.skylogistics.none"));
        addModeButton(leftPos + 134 + RESOURCE_BUTTON_STEP, topPos + menu.screenY(RESOURCE_CONTROL_Y), RESOURCE_BUTTON_WIDTH, NodeFaceMode.INPUT,
                Component.translatable("button.skylogistics.extract"));
        addModeButton(leftPos + 134 + RESOURCE_BUTTON_STEP * 2, topPos + menu.screenY(RESOURCE_CONTROL_Y), RESOURCE_BUTTON_WIDTH, NodeFaceMode.OUTPUT,
                Component.translatable("button.skylogistics.insert"));
        addAdvancedButton(new RedstoneButton(leftPos + REDSTONE_CONTROL_X, topPos + menu.screenY(ADVANCED_CONTROL_Y)));
        addAdvancedButton(new SlotLimitButton(leftPos + SLOT_LIMIT_DOWN_X, topPos + menu.screenY(ADVANCED_CONTROL_Y),
                -1, Component.literal("-")));
        addAdvancedButton(new SlotLimitButton(leftPos + SLOT_LIMIT_UP_X, topPos + menu.screenY(ADVANCED_CONTROL_Y),
                1, Component.literal("+")));
        addAdvancedButton(new PriorityButton(leftPos + PRIORITY_DOWN_X, topPos + menu.screenY(ADVANCED_CONTROL_Y),
                -1, Component.literal("-")));
        addAdvancedButton(new PriorityButton(leftPos + PRIORITY_UP_X, topPos + menu.screenY(ADVANCED_CONTROL_Y),
                1, Component.literal("+")));
        exactQuantityEdit = new EditBox(font, leftPos + SLOT_LIMIT_VALUE_X,
                topPos + menu.screenY(ADVANCED_CONTROL_Y), SLOT_LIMIT_VALUE_WIDTH, ConfigPanel.STEPPER_HEIGHT,
                Component.translatable("screen.skylogistics.exact_quantity"));
        exactQuantityEdit.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        exactQuantityEdit.setMaxLength(10);
        exactQuantityEdit.setResponder(this::exactQuantityChanged);
        addRenderableWidget(exactQuantityEdit);

    }

    private void addLineButton(int x, int y, int width, Component message, int action) {
        LineButton button = new LineButton(x, y, width, message, action);
        lineButtons.add(button);
        addRenderableWidget(button);
    }

    private void addModeButton(int x, int y, int width, NodeFaceMode mode, Component label) {
        ModeButton button = new ModeButton(x, y, width, mode, label);
        modeButtons.add(button);
        addRenderableWidget(button);
    }

    private void addTypeButton(int x, int y, ResourceType type) {
        TypeToggleButton button = new TypeToggleButton(x, y, type);
        typeButtons.add(button);
        addRenderableWidget(button);
    }

    private void addAdvancedButton(AdvancedButton button) {
        advancedButtons.add(button);
        addRenderableWidget(button);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        SkyNodeBlockEntity node = node();
        if (node == null) {
            return;
        }
        int lineIndex = menu.getLineIndex();
        int lineCount = menu.getLineCount();
        for (LineButton button : lineButtons) {
            button.refresh(lineIndex, lineCount);
        }
        refreshLineNameEdit(node);
        Direction firstSelectable = firstSelectableFace(node);
        if (!hasTargetBlock(node, selectedFace) && selectedFace != firstSelectable) {
            selectedFace = firstSelectable;
            menu.selectFace(selectedFace);
            ModNetworking.sendMenuAction(MenuAction.faceSelect(selectedFace));
        }
        for (Direction direction : FACE_ORDER) {
            NodeFaceMode localMode = localFaceModes.get(direction);
            if (localMode != null && node.getFaceMode(direction) == localMode) {
                localFaceModes.remove(direction);
            }
            FaceButton button = faceButtons.get(direction);
            if (button != null) {
                button.active = hasTargetBlock(node, direction);
            }
        }
        boolean selectedFaceActive = hasTargetBlock(node, selectedFace);
        boolean resourceControlsActive = selectedFaceActive && modeFor(node, selectedFace) != NodeFaceMode.NONE;
        for (TypeToggleButton button : typeButtons) {
            button.active = resourceControlsActive;
        }
        menu.setFaceFilterSlotsActive(selectedFaceActive);
        for (ModeButton button : modeButtons) {
            button.active = selectedFaceActive;
        }
        for (AdvancedButton button : advancedButtons) {
            button.visible = !(button instanceof SlotLimitButton && node.hasExactQuantityUpgrade());
            button.active = selectedFaceActive && button.canUse(node);
        }
        refreshExactQuantity(node);
        if (localItemsEnabled != null && node.isItemsEnabled(selectedFace) == localItemsEnabled) {
            localItemsEnabled = null;
        }
        if (localFluidsEnabled != null && node.isFluidsEnabled(selectedFace) == localFluidsEnabled) {
            localFluidsEnabled = null;
        }
        if (localEnergyEnabled != null && node.isEnergyEnabled(selectedFace) == localEnergyEnabled) {
            localEnergyEnabled = null;
        }
        if (shouldClearTagFilterWarning(node)) {
            clearTagFilterWarning();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        if (!menu.isSingleEndpoint()) {
            drawFacePanel(graphics);
        }
        ConfigPanel.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        ConfigPanel.drawContentPanel(graphics, leftPos + LINE_PANEL_X, topPos + LINE_PANEL_Y,
                LINE_PANEL_WIDTH, 24);
        SkyNodeBlockEntity node = node();
        ConfigPanel.drawFieldset(graphics, leftPos + RESOURCE_GROUP_X, topPos + menu.screenY(RESOURCE_GROUP_Y),
                RESOURCE_MODE_GROUP_WIDTH, font.width(Component.translatable("screen.skylogistics.resources")));
        ConfigPanel.drawFieldset(graphics, leftPos + MODE_GROUP_X, topPos + menu.screenY(RESOURCE_GROUP_Y),
                RESOURCE_MODE_GROUP_WIDTH, font.width(Component.translatable("screen.skylogistics.mode_label")));
        if (node != null) {
            Component slotLegend = Component.translatable(node.hasExactQuantityUpgrade()
                    ? "screen.skylogistics.exact_quantity" : "screen.skylogistics.slot_limit");
            int groupY = topPos + menu.screenY(ADVANCED_GROUP_Y);
            ConfigPanel.drawFieldset(graphics, leftPos + REDSTONE_GROUP_X, groupY,
                    REDSTONE_GROUP_WIDTH, font.width(Component.translatable("screen.skylogistics.redstone")));
            ConfigPanel.drawFieldset(graphics, leftPos + SLOT_LIMIT_GROUP_X, groupY,
                    SLOT_LIMIT_GROUP_WIDTH, font.width(slotLegend));
            ConfigPanel.drawFieldset(graphics, leftPos + PRIORITY_GROUP_X, groupY,
                    PRIORITY_GROUP_WIDTH, font.width(Component.translatable("screen.skylogistics.priority")));
            if (!node.hasExactQuantityUpgrade()) {
                ConfigPanel.drawStepperValue(graphics, leftPos + SLOT_LIMIT_VALUE_X,
                        topPos + menu.screenY(ADVANCED_CONTROL_Y), SLOT_LIMIT_VALUE_WIDTH);
            }
            ConfigPanel.drawStepperValue(graphics, leftPos + PRIORITY_VALUE_X,
                    topPos + menu.screenY(ADVANCED_CONTROL_Y), PRIORITY_VALUE_WIDTH);
        }
        int slotGroupY = topPos + menu.screenY(UPGRADE_FILTER_GROUP_Y);
        ConfigPanel.drawFieldset(graphics, leftPos + UPGRADE_GROUP_X, slotGroupY,
                UPGRADE_FILTER_GROUP_WIDTH, font.width(Component.translatable("screen.skylogistics.upgrade_slots")),
                UPGRADE_FILTER_GROUP_HEIGHT);
        ConfigPanel.drawFieldset(graphics, leftPos + FILTER_GROUP_X, slotGroupY,
                UPGRADE_FILTER_GROUP_WIDTH, font.width(Component.translatable(node != null && node.usesSingleEndpoint()
                        ? "screen.skylogistics.filter_slot" : "screen.skylogistics.face_filters")),
                UPGRADE_FILTER_GROUP_HEIGHT);
        renderMenuSlotBackgrounds(graphics);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SkyNodeBlockEntity node = node();
        graphics.text(font, title, 10, 7, ConfigPanel.ACCENT, false);
        if (node == null) {
            graphics.text(font, Component.translatable("screen.skylogistics.missing_node"),
                    10, 28, ConfigPanel.MUTED, false);
            return;
        }
        Component externalExtractHint = externalExtractHint(node, selectedFace);
        if (externalExtractHint != null) {
            graphics.text(font, externalExtractHint, imageWidth - 10 - font.width(externalExtractHint), 7,
                    0xFFFF9A8A, false);
        }
        int lineIndex = menu.getLineIndex() + 1;
        int lineCount = Math.max(1, menu.getLineCount());
        Component lineNameLabel = Component.translatable("screen.skylogistics.configurator.line");
        graphics.text(font, lineNameLabel,
                LINE_NAME_EDIT_X - LINE_NAME_LABEL_GAP - font.width(lineNameLabel),
                LINE_NAME_LABEL_Y, ConfigPanel.MUTED, false);
        graphics.centeredText(font, Component.literal(lineIndex + "/" + lineCount),
                LINE_COUNT_CENTER_X, 28, ConfigPanel.TEXT);

        Direction face = selectedFace;
        graphics.centeredText(font, Component.translatable("screen.skylogistics.resources"),
                RESOURCE_GROUP_X + RESOURCE_MODE_GROUP_WIDTH / 2,
                menu.screenY(RESOURCE_GROUP_Y) - 4, ConfigPanel.MUTED);
        graphics.centeredText(font, Component.translatable("screen.skylogistics.mode_label"),
                MODE_GROUP_X + RESOURCE_MODE_GROUP_WIDTH / 2,
                menu.screenY(RESOURCE_GROUP_Y) - 4, ConfigPanel.MUTED);
        int legendY = menu.screenY(ADVANCED_GROUP_Y) - 4;
        graphics.centeredText(font, Component.translatable("screen.skylogistics.redstone"),
                REDSTONE_GROUP_X + REDSTONE_GROUP_WIDTH / 2, legendY, ConfigPanel.MUTED);
        graphics.centeredText(font, Component.translatable(node.hasExactQuantityUpgrade()
                        ? "screen.skylogistics.exact_quantity" : "screen.skylogistics.slot_limit"),
                SLOT_LIMIT_GROUP_X + SLOT_LIMIT_GROUP_WIDTH / 2, legendY, ConfigPanel.MUTED);
        if (!node.hasExactQuantityUpgrade()) graphics.centeredText(font,
                slotLimitDisplay(node.getItemSlotLimit(face)), SLOT_LIMIT_VALUE_X + SLOT_LIMIT_VALUE_WIDTH / 2,
                menu.screenY(ADVANCED_CONTROL_Y) + 4, ConfigPanel.TEXT);
        graphics.centeredText(font, Component.translatable("screen.skylogistics.priority"),
                PRIORITY_GROUP_X + PRIORITY_GROUP_WIDTH / 2, legendY, ConfigPanel.MUTED);
        graphics.centeredText(font, Component.literal(String.valueOf(node.getPriority(face))),
                PRIORITY_VALUE_X + PRIORITY_VALUE_WIDTH / 2,
                menu.screenY(ADVANCED_CONTROL_Y) + 4, ConfigPanel.TEXT);
        int slotLegendY = menu.screenY(UPGRADE_FILTER_GROUP_Y) - 4;
        graphics.centeredText(font, Component.translatable("screen.skylogistics.upgrade_slots"),
                UPGRADE_GROUP_X + UPGRADE_FILTER_GROUP_WIDTH / 2, slotLegendY, ConfigPanel.MUTED);
        graphics.centeredText(font, Component.translatable(node.usesSingleEndpoint()
                        ? "screen.skylogistics.filter_slot" : "screen.skylogistics.face_filters"),
                FILTER_GROUP_X + UPGRADE_FILTER_GROUP_WIDTH / 2, slotLegendY, ConfigPanel.MUTED);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int x, int y) {
        FaceButton button = hoveredFaceButton(x, y);
        SkyNodeBlockEntity node = node();
        if (button != null && node != null) {
            graphics.setComponentTooltipForNextFrame(font, List.of(targetName(node, button.direction)), x, y);
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
        if (exactQuantityEdit != null && exactQuantityEdit.isFocused()
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            exactQuantityEdit.setFocused(false);
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
        if (exactQuantityEdit != null && exactQuantityEdit.isFocused()
                && !exactQuantityEdit.isMouseOver(mouseX, mouseY)) {
            exactQuantityEdit.setFocused(false);
            setFocused(null);
        }
        updateTagFilterWarningFromClick(mouseX, mouseY, event.hasShiftDown());
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void removed() {
        commitLineNameEdit();
        super.removed();
    }

    private void refreshLineNameEdit(SkyNodeBlockEntity node) {
        if (lineNameEdit == null) {
            return;
        }
        boolean focused = lineNameEdit.isFocused();
        if (lineNameEditWasFocused && !focused) {
            commitLineNameEdit();
            node = node();
            focused = lineNameEdit.isFocused();
        }
        lineNameEditWasFocused = focused;
        lineNameEdit.visible = node != null;
        lineNameEdit.active = node != null;
        if (node == null) {
            lineNameEditLine = null;
            lineNameEdit.setValue("");
            return;
        }
        String displayName = displayLineName(node);
        if (!focused && (!node.getLineId().equals(lineNameEditLine)
                || !lineNameEdit.getValue().equals(displayName))) {
            lineNameEditLine = node.getLineId();
            lineNameEdit.setValue(displayName);
        }
    }

    private void refreshExactQuantity(SkyNodeBlockEntity node) {
        if (exactQuantityEdit == null) return;
        boolean visible = node.hasExactQuantityUpgrade();
        exactQuantityEdit.visible = visible;
        exactQuantityEdit.active = visible;
        if (visible && !exactQuantityEdit.isFocused()) {
            String value = String.valueOf(node.exactQuantity());
            if (!value.equals(exactQuantityEdit.getValue())) {
                refreshingExactQuantity = true;
                exactQuantityEdit.setValue(value);
                refreshingExactQuantity = false;
            }
        }
    }

    private void exactQuantityChanged(String value) {
        if (refreshingExactQuantity || value.isEmpty()) return;
        try {
            long parsed = Long.parseLong(value);
            ModNetworking.sendExactQuantity((int) Math.min(Integer.MAX_VALUE, Math.max(1L, parsed)));
        } catch (NumberFormatException ignored) { }
    }

    private void commitLineNameEdit() {
        if (lineNameEdit == null) {
            return;
        }
        SkyNodeBlockEntity node = node();
        if (node == null) {
            return;
        }
        String oldName = displayLineName(node);
        String assignedName = node.getAssignedLineName();
        String newName = ClientLineNames.editedName(node.getLineId(), lineNameEdit.getValue(), assignedName);
        ClientLineNames.apply(node.getLineId(), assignedName, newName);
        lineNameEditLine = node.getLineId();
        lineNameEdit.setValue(newName);
        if (!oldName.equals(newName)) {
            ModNetworking.sendLineRename(lineNameEdit.getValue());
        }
    }

    private String displayLineName(SkyNodeBlockEntity node) {
        return ClientLineNames.displayName(node.getLineId(), node.getLineName());
    }

    private FaceButton hoveredFaceButton(double mouseX, double mouseY) {
        for (FaceButton button : faceButtons.values()) {
            if (button.isMouseOver(mouseX, mouseY)) {
                return button;
            }
        }
        return null;
    }

    private Component externalExtractHint(SkyNodeBlockEntity node, Direction face) {
        if (!node.hasTagFaceFilterRestriction(face)) {
            return null;
        }
        if (tagFilterRejectedFace == face) {
            return Component.translatable("message.skylogistics.sky_node.tag_filter_external_extract");
        }
        return node.hasValidItemWhitelistFaceFilter(face)
                ? null
                : Component.translatable("screen.skylogistics.sky_node.needs_whitelist_external_extract");
    }

    private void updateTagFilterWarningFromClick(double mouseX, double mouseY, boolean shiftDown) {
        SkyNodeBlockEntity node = node();
        if (node == null || !node.hasTagFaceFilterRestriction(selectedFace)) {
            return;
        }
        Slot slot = slotAt(mouseX, mouseY);
        if (slot == null) {
            return;
        }
        ItemStack attempted = ItemStack.EMPTY;
        int targetSlot = faceFilterMenuSlot(slot);
        if (targetSlot >= 0) {
            attempted = menu.getCarried();
            if (!attempted.isEmpty() && !SkyNodeBlockEntity.isFaceFilterItem(attempted)) {
                return;
            }
        } else if (shiftDown && SkyNodeBlockEntity.isFaceFilterItem(slot.getItem())) {
            targetSlot = 0;
            attempted = slot.getItem();
        } else {
            return;
        }
        refreshTagFilterWarning(node, selectedFace, targetSlot, attempted);
    }

    private void refreshTagFilterWarning(SkyNodeBlockEntity node, Direction face, int slot, ItemStack attempted) {
        if (TagFilterListItem.isTagFilterList(attempted)) {
            tagFilterRejectedFace = face;
            tagFilterRejectedSlot = slot;
            tagFilterRejectedPrevious = node.getFaceFilter(face, slot).copy();
        } else {
            clearTagFilterWarning();
        }
    }

    private boolean shouldClearTagFilterWarning(SkyNodeBlockEntity node) {
        if (tagFilterRejectedFace == null) {
            return false;
        }
        if (!node.hasTagFaceFilterRestriction(tagFilterRejectedFace)
                || node.hasValidItemWhitelistFaceFilter(tagFilterRejectedFace)) {
            return true;
        }
        if (tagFilterRejectedSlot < 0 || tagFilterRejectedSlot >= SkyNodeBlockEntity.FACE_FILTER_SLOTS) {
            return false;
        }
        return !ItemStack.matches(node.getFaceFilter(tagFilterRejectedFace, tagFilterRejectedSlot),
                tagFilterRejectedPrevious);
    }

    private void clearTagFilterWarning() {
        tagFilterRejectedFace = null;
        tagFilterRejectedSlot = -1;
        tagFilterRejectedPrevious = ItemStack.EMPTY;
    }

    private Slot slotAt(double mouseX, double mouseY) {
        for (Slot slot : menu.slots) {
            if (slot.isActive() && mouseX >= leftPos + slot.x - 1 && mouseX < leftPos + slot.x + 17
                    && mouseY >= topPos + slot.y - 1 && mouseY < topPos + slot.y + 17) {
                return slot;
            }
        }
        return null;
    }

    private int faceFilterMenuSlot(Slot slot) {
        int start = SkyNodeBlockEntity.UPGRADE_SLOTS;
        int end = start + SkyNodeBlockEntity.FACE_FILTER_SLOTS;
        for (int index = start; index < end && index < menu.slots.size(); index++) {
            if (menu.slots.get(index) == slot) {
                return index - start;
            }
        }
        return -1;
    }

    private void renderMenuSlotBackgrounds(GuiGraphicsExtractor graphics) {
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConfigPanel.drawSlotBackground(graphics, leftPos + slot.x, topPos + slot.y);
            }
        }
    }

    private void drawFacePanel(GuiGraphicsExtractor graphics) {
        int x = leftPos + FACE_PANEL_X;
        int y = topPos + FACE_PANEL_Y;
        graphics.fill(x, y, x + FACE_PANEL_WIDTH, y + FACE_PANEL_HEIGHT, ConfigPanel.BG);
        graphics.fill(x, y, x + FACE_PANEL_WIDTH, y + 1, ConfigPanel.BORDER_ACTIVE);
        graphics.fill(x, y, x + 1, y + FACE_PANEL_HEIGHT, ConfigPanel.BORDER_ACTIVE);
        graphics.fill(x, y + FACE_PANEL_HEIGHT - 1, x + FACE_PANEL_WIDTH, y + FACE_PANEL_HEIGHT,
                ConfigPanel.BORDER);
    }

    private int colorFor(NodeFaceMode mode) {
        return switch (mode) {
            case INPUT -> 0xFFFFB56B;
            case OUTPUT -> 0xFF7DEBFF;
            case NONE -> ConfigPanel.MUTED;
        };
    }

    private Direction firstSelectableFace(SkyNodeBlockEntity node) {
        if (node.usesSingleEndpoint()) {
            return node.getSingleEndpointDirection();
        }
        for (Direction direction : FACE_ORDER) {
            if (isPreferredFace(node, direction)) {
                return direction;
            }
        }
        for (Direction direction : FACE_ORDER) {
            if (hasTargetBlock(node, direction)) {
                return direction;
            }
        }
        return node.getTargetDirection();
    }

    private boolean isPreferredFace(SkyNodeBlockEntity node, Direction direction) {
        return hasTargetBlock(node, direction)
                && node.getFaceMode(direction) != NodeFaceMode.NONE
                && (node.isItemsEnabled(direction) || node.isFluidsEnabled(direction)
                        || node.isEnergyEnabled(direction));
    }

    private boolean hasTargetBlock(SkyNodeBlockEntity node, Direction direction) {
        return node.hasConfigurableTarget(direction);
    }

    private NodeFaceMode modeFor(SkyNodeBlockEntity node, Direction direction) {
        return localFaceModes.getOrDefault(direction, node.getFaceMode(direction));
    }

    private ItemStack iconFor(SkyNodeBlockEntity node, Direction direction) {
        return node.getTargetIcon(direction);
    }

    private Component targetName(SkyNodeBlockEntity node, Direction direction) {
        return node.getTargetName(direction);
    }

    private Component faceName(Direction direction) {
        return Component.translatable("screen.skylogistics.face." + direction.getSerializedName());
    }

    private Component faceShortName(Direction direction) {
        return Component.translatable("screen.skylogistics.face_short." + direction.getSerializedName());
    }

    private boolean itemsEnabled(SkyNodeBlockEntity node) {
        return localItemsEnabled == null ? node.isItemsEnabled(selectedFace) : localItemsEnabled;
    }

    private boolean fluidsEnabled(SkyNodeBlockEntity node) {
        return localFluidsEnabled == null ? node.isFluidsEnabled(selectedFace) : localFluidsEnabled;
    }

    private boolean energyEnabled(SkyNodeBlockEntity node) {
        return localEnergyEnabled == null ? node.isEnergyEnabled(selectedFace) : localEnergyEnabled;
    }

    private Component slotLimitDisplay(int slotLimit) {
        return slotLimit == SkyNodeBlockEntity.ITEM_SLOT_LIMIT_UNLIMITED
                ? Component.translatable("screen.skylogistics.slot_limit.unlimited")
                : Component.literal(String.valueOf(slotLimit));
    }

    private SkyNodeBlockEntity node() {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(menu.getPos());
        return blockEntity instanceof SkyNodeBlockEntity node ? node : null;
    }

    private void borderedBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border) {
        ConfigPanel.drawBox(graphics, x, y, width, height, fill, border);
    }

    private final class FaceButton extends AbstractButton {
        private final Direction direction;

        private FaceButton(int x, int y, Direction direction) {
            super(x, y, FACE_BUTTON_WIDTH, FACE_BUTTON_HEIGHT, faceName(direction));
            this.direction = direction;
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (active) {
                selectedFace = direction;
                localItemsEnabled = null;
                localFluidsEnabled = null;
                localEnergyEnabled = null;
                menu.selectFace(direction);
                ModNetworking.sendMenuAction(MenuAction.faceSelect(direction));
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            SkyNodeBlockEntity node = node();
            boolean selected = direction == selectedFace;
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, selected);
            if (node != null) {
                ItemStack icon = iconFor(node, direction);
                if (!icon.isEmpty()) {
                    graphics.item(icon, getX() + 9, getY() + 5);
                }
                graphics.text(font, faceShortName(direction), getX() + 2, getY() + 2,
                        active ? ConfigPanel.TEXT : ConfigPanel.MUTED, false);
                int modeColor = colorFor(modeFor(node, direction));
                graphics.fill(getX() + 5, getY() + height - 5, getX() + width - 5, getY() + height - 3, modeColor);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
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
                case MenuAction.LINE_FIRST, MenuAction.LINE_PREVIOUS -> count > 1 && index > 0;
                case MenuAction.LINE_LAST -> count > 1 && index < count - 1;
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
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
            graphics.centeredText(font, getMessage(), getX() + width / 2, getY() + 5,
                    active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class ModeButton extends AbstractButton {
        private final NodeFaceMode mode;

        private ModeButton(int x, int y, int width, NodeFaceMode mode, Component message) {
            super(x, y, width, 21, message);
            this.mode = mode;
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (!active) {
                return;
            }
            localFaceModes.put(selectedFace, mode);
            int action = switch (mode) {
                case NONE -> MenuAction.faceNone(selectedFace);
                case INPUT -> MenuAction.faceExtract(selectedFace);
                case OUTPUT -> MenuAction.faceInsert(selectedFace);
            };
            ModNetworking.sendMenuAction(action);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            SkyNodeBlockEntity node = node();
            boolean selected = node != null && modeFor(node, selectedFace) == mode;
            int accent = mode == NodeFaceMode.INPUT ? 0xFFFFA52D
                    : mode == NodeFaceMode.OUTPUT ? 0xFF36B4D2 : ConfigPanel.BORDER_ACTIVE;
            ConfigPanel.drawImageButtonChrome(graphics, getX(), getY(), width, height, active, selected, accent);
            int iconColor = active ? (selected ? accent : ConfigPanel.TEXT) : ConfigPanel.MUTED;
            if (mode == NodeFaceMode.NONE) {
                drawDisabledModeIcon(graphics, getX() + 2, getY() + 2, iconColor);
            } else {
                drawModeArrow(graphics, getX() + 2, getY() + 2, mode == NodeFaceMode.INPUT, iconColor);
            }
            graphics.text(font, compactText(getMessage()), getX() + 20, getY() + 7,
                    active ? ConfigPanel.TEXT : ConfigPanel.MUTED, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private abstract class AdvancedButton extends AbstractButton {
        private AdvancedButton(int x, int y, int width, int height, Component message) {
            super(x, y, width, height, message);
        }

        protected boolean canUse(SkyNodeBlockEntity node) {
            return true;
        }

        protected Component dynamicMessage(SkyNodeBlockEntity node) {
            return getMessage();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            SkyNodeBlockEntity node = node();
            Component message = node == null ? getMessage() : dynamicMessage(node);
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
            graphics.centeredText(font, message, getX() + width / 2, getY() + (height - 8) / 2,
                    active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class RedstoneButton extends AdvancedButton {
        private RedstoneButton(int x, int y) {
            super(x, y, ADVANCED_CONTROL_WIDTH, ConfigPanel.STEPPER_HEIGHT,
                    Component.translatable("screen.skylogistics.redstone"));
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (active) {
                ModNetworking.sendMenuAction(MenuAction.faceRedstone(selectedFace));
            }
        }

        @Override
        protected Component dynamicMessage(SkyNodeBlockEntity node) {
            return Component.translatable(node.getRedstoneControl(selectedFace).translationKey());
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            SkyNodeBlockEntity node = node();
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
            if (node == null) {
                return;
            }
            ConfigPanel.drawRedstoneIcon(graphics, getX() + 5, getY(), node.getRedstoneControl(selectedFace));
            graphics.text(font, dynamicMessage(node), getX() + 23, getY() + 4,
                    active ? ConfigPanel.TEXT : ConfigPanel.MUTED, false);
        }
    }

    private final class PriorityButton extends AdvancedButton {
        private final int delta;

        private PriorityButton(int x, int y, int delta, Component message) {
            super(x, y, PRIORITY_BUTTON_WIDTH, ConfigPanel.STEPPER_HEIGHT, message);
            this.delta = delta;
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (active) {
                boolean fast = input.hasShiftDown();
                int action = delta < 0
                        ? (fast ? MenuAction.facePriorityDownFast(selectedFace)
                                : MenuAction.facePriorityDown(selectedFace))
                        : (fast ? MenuAction.facePriorityUpFast(selectedFace)
                                : MenuAction.facePriorityUp(selectedFace));
                ModNetworking.sendMenuAction(action);
            }
        }
    }

    private final class SlotLimitButton extends AdvancedButton {
        private final int delta;

        private SlotLimitButton(int x, int y, int delta, Component message) {
            super(x, y, PRIORITY_BUTTON_WIDTH, ConfigPanel.STEPPER_HEIGHT, message);
            this.delta = delta;
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (active) {
                boolean fast = input.hasShiftDown();
                int action = delta < 0
                        ? (fast ? MenuAction.faceSlotLimitDownFast(selectedFace)
                                : MenuAction.faceSlotLimitDown(selectedFace))
                        : (fast ? MenuAction.faceSlotLimitUpFast(selectedFace)
                                : MenuAction.faceSlotLimitUp(selectedFace));
                ModNetworking.sendMenuAction(action);
            }
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
            super(x, y, RESOURCE_BUTTON_WIDTH, 21, Component.translatable(type.translationKey));
            this.type = type;
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (!active) {
                return;
            }
            SkyNodeBlockEntity node = node();
            if (node == null) {
                return;
            }
            switch (type) {
                case ITEMS -> {
                    localItemsEnabled = !itemsEnabled(node);
                    ModNetworking.sendMenuAction(MenuAction.TOGGLE_ITEMS);
                }
                case FLUIDS -> {
                    localFluidsEnabled = !fluidsEnabled(node);
                    ModNetworking.sendMenuAction(MenuAction.TOGGLE_FLUIDS);
                }
                case ENERGY -> {
                    localEnergyEnabled = !energyEnabled(node);
                    ModNetworking.sendMenuAction(MenuAction.TOGGLE_ENERGY);
                }
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            SkyNodeBlockEntity node = node();
            boolean enabled = node != null && active && isEnabled(node);
            ConfigPanel.drawImageButtonChrome(graphics, getX(), getY(), width, height, active, enabled,
                    ConfigPanel.ACCENT);
            ConfigPanel.drawResourceIcon(graphics, getX() + 2, getY() + 2, resourceName(), enabled);
            graphics.text(font, compactText(getMessage()), getX() + 20, getY() + 7,
                    enabled ? ConfigPanel.TEXT : ConfigPanel.MUTED, false);
        }

        private String resourceName() {
            return switch (type) {
                case ITEMS -> "item";
                case FLUIDS -> "fluid";
                case ENERGY -> "energy";
            };
        }

        private boolean isEnabled(SkyNodeBlockEntity node) {
            return switch (type) {
                case ITEMS -> itemsEnabled(node);
                case FLUIDS -> fluidsEnabled(node);
                case ENERGY -> energyEnabled(node);
            };
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static void drawModeArrow(GuiGraphicsExtractor graphics, int x, int y, boolean up, int color) {
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

    private static void drawDisabledModeIcon(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x + 7, y + 3, x + 13, y + 5, color);
        graphics.fill(x + 5, y + 5, x + 7, y + 12, color);
        graphics.fill(x + 13, y + 5, x + 15, y + 12, color);
        graphics.fill(x + 7, y + 12, x + 13, y + 14, color);
        graphics.fill(x + 5, y + 4, x + 8, y + 7, color);
        graphics.fill(x + 7, y + 6, x + 10, y + 9, color);
        graphics.fill(x + 9, y + 8, x + 12, y + 11, color);
        graphics.fill(x + 11, y + 10, x + 14, y + 13, color);
    }

    private static Component compactText(Component component) {
        String value = component.getString();
        if (value.isEmpty()) return Component.empty();
        return Component.literal(value.substring(0, value.offsetByCodePoints(0, 1)));
    }
}
