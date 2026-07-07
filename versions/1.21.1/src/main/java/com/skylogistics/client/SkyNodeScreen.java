package com.skylogistics.client;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.menu.MenuAction;
import com.skylogistics.menu.SkyNodeMenu;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.util.NodeFaceMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    private static final int LINE_NAME_LABEL_Y = 12;
    private static final int LINE_NAME_LABEL_GAP = 4;
    private static final int LINE_NAME_EDIT_X = 146;
    private static final int LINE_NAME_EDIT_Y = 7;
    private static final int LINE_NAME_EDIT_WIDTH = 92;
    private static final int LINE_NAME_EDIT_HEIGHT = 16;
    private static final Direction[] FACE_ORDER = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final int FIRST_DETAIL_ROW_Y = 100;
    private static final int SECOND_DETAIL_ROW_Y = 126;
    private static final int ADVANCED_CONTROL_X = 48;
    private static final int ADVANCED_CONTROL_WIDTH = 76;
    private static final int ADVANCED_RIGHT_LABEL_X = 138;
    private static final int PRIORITY_BUTTON_WIDTH = 20;
    private static final int STEPPER_HEIGHT = 18;
    private static final int PRIORITY_DOWN_X = ADVANCED_CONTROL_X;
    private static final int PRIORITY_UP_X = 104;
    private static final int PRIORITY_VALUE_X = PRIORITY_DOWN_X + PRIORITY_BUTTON_WIDTH;
    private static final int PRIORITY_VALUE_WIDTH = PRIORITY_UP_X - PRIORITY_VALUE_X;
    private static final int SLOT_LIMIT_DOWN_X = SkyNodeMenu.FACE_FILTER_SLOT_X;
    private static final int SLOT_LIMIT_VALUE_X = SLOT_LIMIT_DOWN_X + PRIORITY_BUTTON_WIDTH;
    private static final int SLOT_LIMIT_VALUE_WIDTH = 26;
    private static final int SLOT_LIMIT_UP_X = SLOT_LIMIT_VALUE_X + SLOT_LIMIT_VALUE_WIDTH;
    private static final int MORE_BUTTON_X = 162;
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
    private MoreButton moreButton;
    private EditBox lineNameEdit;
    private boolean lineNameEditWasFocused;
    private UUID lineNameEditLine;
    private boolean advancedPanel;

    public SkyNodeScreen(SkyNodeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 254;
        imageHeight = menu.isSingleEndpoint() ? 265 - SkyNodeMenu.SINGLE_ENDPOINT_VERTICAL_SHIFT : 265;
        inventoryLabelY = menu.screenY(169);
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

        addLineButton(leftPos + 116, topPos + 29, 22, Component.literal("|<"), MenuAction.LINE_FIRST);
        addLineButton(leftPos + 141, topPos + 29, 20, Component.literal("<"), MenuAction.LINE_PREVIOUS);
        addLineButton(leftPos + 164, topPos + 29, 24, Component.literal(">+"), MenuAction.LINE_NEXT_OR_CREATE);
        addLineButton(leftPos + 191, topPos + 29, 22, Component.literal(">|"), MenuAction.LINE_LAST);
        addLineButton(leftPos + 216, topPos + 29, 18, Component.literal("x"), MenuAction.LINE_REMOVE_CURRENT);
        lineNameEdit = new EditBox(font, leftPos + LINE_NAME_EDIT_X, topPos + LINE_NAME_EDIT_Y,
                LINE_NAME_EDIT_WIDTH, LINE_NAME_EDIT_HEIGHT,
                Component.translatable("screen.skylogistics.line_name"));
        lineNameEdit.setMaxLength(48);
        ConfigPanel.styleEditBox(lineNameEdit);
        addRenderableWidget(lineNameEdit);

        if (!singleEndpoint) {
            int x = leftPos + 14;
            int y = topPos + 48;
            for (int i = 0; i < FACE_ORDER.length; i++) {
                Direction direction = FACE_ORDER[i];
                FaceButton button = new FaceButton(x + i * 37, y, direction);
                faceButtons.put(direction, button);
                addRenderableWidget(button);
            }
        }

        addTypeButton(leftPos + 54, topPos + menu.screenY(100), ResourceType.ITEMS);
        addTypeButton(leftPos + 108, topPos + menu.screenY(100), ResourceType.FLUIDS);
        addTypeButton(leftPos + 162, topPos + menu.screenY(100), ResourceType.ENERGY);
        addModeButton(leftPos + 54, topPos + menu.screenY(126), 48, NodeFaceMode.NONE,
                Component.translatable("button.skylogistics.none"));
        addModeButton(leftPos + 108, topPos + menu.screenY(126), 48, NodeFaceMode.INPUT,
                Component.translatable("button.skylogistics.extract"));
        addModeButton(leftPos + 162, topPos + menu.screenY(126), 48, NodeFaceMode.OUTPUT,
                Component.translatable("button.skylogistics.insert"));
        int moreWidth = 48;
        moreButton = new MoreButton(leftPos + MORE_BUTTON_X, topPos + menu.screenY(SkyNodeMenu.UPGRADE_ROW_Y),
                moreWidth);
        addRenderableWidget(moreButton);
        addAdvancedButton(new RedstoneButton(leftPos + ADVANCED_CONTROL_X, topPos + menu.screenY(FIRST_DETAIL_ROW_Y)));
        addAdvancedButton(new SlotLimitButton(leftPos + SLOT_LIMIT_DOWN_X, topPos + menu.screenY(FIRST_DETAIL_ROW_Y),
                -1, Component.literal("-")));
        addAdvancedButton(new SlotLimitButton(leftPos + SLOT_LIMIT_UP_X, topPos + menu.screenY(FIRST_DETAIL_ROW_Y),
                1, Component.literal("+")));
        addAdvancedButton(new PriorityButton(leftPos + PRIORITY_DOWN_X, topPos + menu.screenY(SECOND_DETAIL_ROW_Y),
                -1, Component.literal("-")));
        addAdvancedButton(new PriorityButton(leftPos + PRIORITY_UP_X, topPos + menu.screenY(SECOND_DETAIL_ROW_Y),
                1, Component.literal("+")));

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
        button.visible = advancedPanel;
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
            button.visible = !advancedPanel;
            button.active = !advancedPanel && resourceControlsActive;
        }
        menu.setFaceFilterSlotsActive(advancedPanel && selectedFaceActive);
        for (ModeButton button : modeButtons) {
            button.visible = !advancedPanel;
            button.active = !advancedPanel && selectedFaceActive;
        }
        for (AdvancedButton button : advancedButtons) {
            button.visible = advancedPanel;
            button.active = advancedPanel && selectedFaceActive && button.canUse(node);
        }
        if (moreButton != null) {
            moreButton.active = selectedFaceActive;
            moreButton.setMessage(Component.translatable(advancedPanel
                    ? "button.skylogistics.basic"
                    : "button.skylogistics.more"));
        }
        if (localItemsEnabled != null && node.isItemsEnabled(selectedFace) == localItemsEnabled) {
            localItemsEnabled = null;
        }
        if (localFluidsEnabled != null && node.isFluidsEnabled(selectedFace) == localFluidsEnabled) {
            localFluidsEnabled = null;
        }
        if (localEnergyEnabled != null && node.isEnergyEnabled(selectedFace) == localEnergyEnabled) {
            localEnergyEnabled = null;
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
        if (lineNameEdit != null && lineNameEdit.visible) {
            ConfigPanel.drawInputBox(graphics, leftPos + LINE_NAME_EDIT_X, topPos + LINE_NAME_EDIT_Y,
                    LINE_NAME_EDIT_WIDTH, LINE_NAME_EDIT_HEIGHT, lineNameEdit.isFocused());
        }
        renderSectionPanels(graphics);
        renderMenuSlotBackgrounds(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        SkyNodeBlockEntity node = node();
        graphics.drawString(font, title, 14, 10, ConfigPanel.ACCENT, false);
        if (node == null) {
            graphics.drawString(font, Component.translatable("screen.skylogistics.missing_node"),
                    14, 34, ConfigPanel.MUTED, false);
            return;
        }
        int lineIndex = menu.getLineIndex() + 1;
        int lineCount = Math.max(1, menu.getLineCount());
        Component lineNameLabel = Component.translatable("screen.skylogistics.line_name");
        graphics.drawString(font, lineNameLabel,
                LINE_NAME_EDIT_X - LINE_NAME_LABEL_GAP - font.width(lineNameLabel),
                LINE_NAME_LABEL_Y, ConfigPanel.MUTED, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.line_index", lineIndex, lineCount),
                14, 34, ConfigPanel.TEXT, false);

        Direction face = selectedFace;
        if (!node.usesSingleEndpoint()) {
            graphics.drawString(font, Component.translatable("screen.skylogistics.current_face",
                    faceName(face), targetName(node, face)), 14, 88, ConfigPanel.TEXT, false);
        }
        if (advancedPanel) {
            int slotLimitTextY = ConfigPanel.textCenterY(menu.screenY(FIRST_DETAIL_ROW_Y), STEPPER_HEIGHT);
            int priorityTextY = ConfigPanel.textCenterY(menu.screenY(SECOND_DETAIL_ROW_Y), STEPPER_HEIGHT);
            graphics.drawString(font, Component.translatable("screen.skylogistics.redstone"),
                    14, slotLimitTextY, ConfigPanel.MUTED, false);
            graphics.drawString(font, Component.translatable("screen.skylogistics.slot_limit"),
                    ADVANCED_RIGHT_LABEL_X, slotLimitTextY, ConfigPanel.MUTED, false);
            graphics.drawCenteredString(font, slotLimitDisplay(node.getItemSlotLimit(face)),
                    SLOT_LIMIT_VALUE_X + SLOT_LIMIT_VALUE_WIDTH / 2, slotLimitTextY, ConfigPanel.TEXT);
            graphics.drawString(font, Component.translatable(node.usesSingleEndpoint()
                            ? "screen.skylogistics.filter_slot"
                            : "screen.skylogistics.face_filters"),
                    ADVANCED_RIGHT_LABEL_X, priorityTextY, ConfigPanel.MUTED, false);
            graphics.drawString(font, Component.translatable("screen.skylogistics.priority"),
                    14, priorityTextY, ConfigPanel.MUTED, false);
            graphics.drawCenteredString(font, Component.literal(String.valueOf(node.getPriority(face))),
                    PRIORITY_VALUE_X + PRIORITY_VALUE_WIDTH / 2, priorityTextY, ConfigPanel.TEXT);
        } else {
            graphics.drawString(font, Component.translatable("screen.skylogistics.resources"),
                    14, menu.screenY(106), ConfigPanel.MUTED, false);
            graphics.drawString(font, Component.translatable("screen.skylogistics.mode_label"),
                    14, menu.screenY(132), ConfigPanel.MUTED, false);
        }
        graphics.drawString(font, Component.translatable("screen.skylogistics.upgrade_slots"),
                14, menu.screenY(SkyNodeMenu.UPGRADE_ROW_Y) + 5, ConfigPanel.MUTED, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        FaceButton button = hoveredFaceButton(x, y);
        SkyNodeBlockEntity node = node();
        if (button != null && node != null) {
            graphics.renderComponentTooltip(font, List.of(targetName(node, button.direction)), x, y);
            return;
        }
        super.renderTooltip(graphics, x, y);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (lineNameEdit != null && lineNameEdit.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            commitLineNameEdit();
            lineNameEdit.setFocused(false);
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
        return super.mouseClicked(mouseX, mouseY, button);
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

    private void renderMenuSlotBackgrounds(GuiGraphics graphics) {
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConfigPanel.drawSlotBackground(graphics, leftPos + slot.x, topPos + slot.y);
            }
        }
    }

    private void renderSectionPanels(GuiGraphics graphics) {
        SkyNodeBlockEntity node = node();
        if (node != null && !node.usesSingleEndpoint()) {
            ConfigPanel.drawContentPanel(graphics, leftPos + 8, topPos + 42, imageWidth - 16, 45);
        }
        ConfigPanel.drawContentPanel(graphics, leftPos + 8, topPos + menu.screenY(96), imageWidth - 16, 52);
        ConfigPanel.drawContentPanel(graphics, leftPos + 8, topPos + menu.screenY(147), imageWidth - 16, 25);
        ConfigPanel.drawInventoryPanel(graphics, leftPos + 42, topPos + menu.screenY(174), 170, 86);
        boolean stepperActive = advancedPanel && node != null && hasTargetBlock(node, selectedFace);
        if (advancedPanel) {
            ConfigPanel.drawStepperValue(graphics, leftPos + SLOT_LIMIT_VALUE_X,
                    topPos + menu.screenY(FIRST_DETAIL_ROW_Y), SLOT_LIMIT_VALUE_WIDTH, STEPPER_HEIGHT,
                    stepperActive);
            ConfigPanel.drawStepperValue(graphics, leftPos + PRIORITY_VALUE_X,
                    topPos + menu.screenY(SECOND_DETAIL_ROW_Y), PRIORITY_VALUE_WIDTH, STEPPER_HEIGHT,
                    stepperActive);
        }
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

    private void borderedBox(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        ConfigPanel.drawBox(graphics, x, y, width, height, fill, border);
    }

    private final class FaceButton extends AbstractButton {
        private final Direction direction;

        private FaceButton(int x, int y, Direction direction) {
            super(x, y, 32, 32, faceName(direction));
            this.direction = direction;
        }

        @Override
        public void onPress() {
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
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            SkyNodeBlockEntity node = node();
            boolean selected = direction == selectedFace;
            ConfigPanel.drawFaceButtonChrome(graphics, getX(), getY(), width, height, active, selected);
            if (node != null) {
                ItemStack icon = iconFor(node, direction);
                if (!icon.isEmpty()) {
                    graphics.renderItem(icon, getX() + 8, getY() + 12);
                }
                graphics.drawCenteredString(font, faceShortName(direction), getX() + width / 2, getY() + 4,
                        active ? (selected ? ConfigPanel.ACCENT : ConfigPanel.TEXT) : ConfigPanel.MUTED);
                int modeColor = colorFor(modeFor(node, direction));
                ConfigPanel.drawStatusStrip(graphics, getX() + 8, getY() + height - 5, width - 16, 3, modeColor);
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
            super(x, y, width, 18, message);
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
        public void onPress() {
            if (active) {
                commitLineNameEdit();
                ModNetworking.sendMenuAction(action);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2,
                    ConfigPanel.textCenterY(getY(), height),
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
            super(x, y, width, 20, message);
            this.mode = mode;
        }

        @Override
        public void onPress() {
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
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            SkyNodeBlockEntity node = node();
            boolean selected = node != null && modeFor(node, selectedFace) == mode;
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, selected);
            if (selected) {
                graphics.fill(getX() + 4, getY() + height - 4, getX() + width - 4, getY() + height - 2,
                        colorFor(mode));
            }
            int textColor = active ? ConfigPanel.TEXT : ConfigPanel.MUTED;
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2,
                    ConfigPanel.textCenterY(getY(), height), textColor);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class MoreButton extends AbstractButton {
        private MoreButton(int x, int y, int width) {
            super(x, y, width, 18, Component.translatable("button.skylogistics.more"));
        }

        @Override
        public void onPress() {
            advancedPanel = !advancedPanel;
            SkyNodeBlockEntity node = node();
            menu.setFaceFilterSlotsActive(advancedPanel && node != null && hasTargetBlock(node, selectedFace));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, advancedPanel);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2,
                    ConfigPanel.textCenterY(getY(), height),
                    active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
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
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            SkyNodeBlockEntity node = node();
            Component message = node == null ? getMessage() : dynamicMessage(node);
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
            graphics.drawCenteredString(font, message, getX() + width / 2,
                    ConfigPanel.textCenterY(getY(), height),
                    active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class RedstoneButton extends AdvancedButton {
        private RedstoneButton(int x, int y) {
            super(x, y, ADVANCED_CONTROL_WIDTH, 18, Component.translatable("screen.skylogistics.redstone"));
        }

        @Override
        public void onPress() {
            if (active) {
                ModNetworking.sendMenuAction(MenuAction.faceRedstone(selectedFace));
            }
        }

        @Override
        protected Component dynamicMessage(SkyNodeBlockEntity node) {
            return Component.translatable(node.getRedstoneControl(selectedFace).translationKey());
        }
    }

    private final class PriorityButton extends AdvancedButton {
        private final int delta;

        private PriorityButton(int x, int y, int delta, Component message) {
            super(x, y, PRIORITY_BUTTON_WIDTH, 18, message);
            this.delta = delta;
        }

        @Override
        public void onPress() {
            if (active) {
                boolean fast = net.minecraft.client.gui.screens.Screen.hasShiftDown();
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
            super(x, y, PRIORITY_BUTTON_WIDTH, 18, message);
            this.delta = delta;
        }

        @Override
        public void onPress() {
            if (active) {
                boolean fast = net.minecraft.client.gui.screens.Screen.hasShiftDown();
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
            super(x, y, 48, 20, Component.translatable(type.translationKey));
            this.type = type;
        }

        @Override
        public void onPress() {
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
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            SkyNodeBlockEntity node = node();
            boolean enabled = node != null && active && isEnabled(node);
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, enabled);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2,
                    ConfigPanel.textCenterY(getY(), height),
                    enabled ? ConfigPanel.TEXT : ConfigPanel.MUTED);
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
}
