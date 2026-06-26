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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SkyNodeScreen extends AbstractContainerScreen<SkyNodeMenu> {
    private static final Direction[] FACE_ORDER = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
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
    private boolean advancedPanel;

    public SkyNodeScreen(SkyNodeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 254;
        imageHeight = 276;
        inventoryLabelY = 180;
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
        selectedFace = node == null ? Direction.NORTH : firstSelectableFace(node);
        menu.selectFace(selectedFace);
        ModNetworking.sendMenuAction(MenuAction.faceSelect(selectedFace));

        addLineButton(leftPos + 116, topPos + 29, 22, Component.literal("|<"), MenuAction.LINE_FIRST);
        addLineButton(leftPos + 141, topPos + 29, 20, Component.literal("<"), MenuAction.LINE_PREVIOUS);
        addLineButton(leftPos + 164, topPos + 29, 24, Component.literal(">+"), MenuAction.LINE_NEXT_OR_CREATE);
        addLineButton(leftPos + 191, topPos + 29, 22, Component.literal(">|"), MenuAction.LINE_LAST);
        addLineButton(leftPos + 216, topPos + 29, 18, Component.literal("x"), MenuAction.LINE_REMOVE_CURRENT);

        int x = leftPos + 14;
        int y = topPos + 48;
        for (int i = 0; i < FACE_ORDER.length; i++) {
            Direction direction = FACE_ORDER[i];
            FaceButton button = new FaceButton(x + i * 37, y, direction);
            faceButtons.put(direction, button);
            addRenderableWidget(button);
        }

        addTypeButton(leftPos + 54, topPos + 100, ResourceType.ITEMS);
        addTypeButton(leftPos + 108, topPos + 100, ResourceType.FLUIDS);
        addTypeButton(leftPos + 162, topPos + 100, ResourceType.ENERGY);
        addModeButton(leftPos + 54, topPos + 126, 48, NodeFaceMode.NONE,
                Component.translatable("button.skylogistics.none"));
        addModeButton(leftPos + 108, topPos + 126, 48, NodeFaceMode.INPUT,
                Component.translatable("button.skylogistics.extract"));
        addModeButton(leftPos + 162, topPos + 126, 48, NodeFaceMode.OUTPUT,
                Component.translatable("button.skylogistics.insert"));
        int upgradeX = SkyNodeMenu.upgradeSlotX(menu.isOpenedWithConfigurator());
        int moreWidth = 48;
        moreButton = new MoreButton(leftPos + upgradeX + SkyNodeBlockEntity.UPGRADE_SLOTS * 20 + 6,
                topPos + SkyNodeMenu.UPGRADE_ROW_Y, moreWidth);
        addRenderableWidget(moreButton);
        addAdvancedButton(new RedstoneButton(leftPos + 70, topPos + 112));
        addAdvancedButton(new PriorityButton(leftPos + 70, topPos + 138, -1, Component.literal("-")));
        addAdvancedButton(new PriorityButton(leftPos + 144, topPos + 138, 1, Component.literal("+")));

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
        int lineIndex = node.getLineIndex();
        int lineCount = node.getLineCount();
        for (LineButton button : lineButtons) {
            button.refresh(lineIndex, lineCount);
        }
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
        for (TypeToggleButton button : typeButtons) {
            button.visible = !advancedPanel;
            button.active = !advancedPanel && selectedFaceActive;
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
        int lineIndex = node.getLineIndex() + 1;
        int lineCount = Math.max(1, node.getLineCount());
        graphics.drawString(font, Component.translatable("screen.skylogistics.line_name",
                        node.getLineName())
                .append(Component.literal(" " + lineIndex + "/" + lineCount)),
                14, 34, ConfigPanel.TEXT, false);

        Direction face = selectedFace;
        graphics.drawString(font, Component.translatable("screen.skylogistics.current_face",
                faceName(face), targetName(node, face)), 14, 88, ConfigPanel.TEXT, false);
        if (advancedPanel) {
            graphics.drawString(font, Component.translatable("screen.skylogistics.redstone"),
                    14, 118, ConfigPanel.MUTED, false);
            graphics.drawString(font, Component.translatable("screen.skylogistics.face_filters"),
                    176, 100, ConfigPanel.MUTED, false);
            graphics.drawString(font, Component.translatable("screen.skylogistics.priority"),
                    14, 142, ConfigPanel.MUTED, false);
            graphics.drawString(font, Component.literal(String.valueOf(node.getPriority(face))),
                    106, 142, ConfigPanel.TEXT, false);
        } else {
            graphics.drawString(font, Component.translatable("screen.skylogistics.resources"),
                    14, 106, ConfigPanel.MUTED, false);
            graphics.drawString(font, Component.translatable("screen.skylogistics.mode_label"),
                    14, 132, ConfigPanel.MUTED, false);
        }
        graphics.drawString(font, Component.translatable("screen.skylogistics.upgrade_slots"),
                14, SkyNodeMenu.UPGRADE_ROW_Y + 5, ConfigPanel.MUTED, false);
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

    private int colorFor(NodeFaceMode mode) {
        return switch (mode) {
            case INPUT -> 0xFFFFB56B;
            case OUTPUT -> 0xFF7DEBFF;
            case NONE -> ConfigPanel.MUTED;
        };
    }

    private Direction firstSelectableFace(SkyNodeBlockEntity node) {
        for (Direction direction : FACE_ORDER) {
            if (hasTargetBlock(node, direction)) {
                return direction;
            }
        }
        return node.getTargetDirection();
    }

    private boolean hasTargetBlock(SkyNodeBlockEntity node, Direction direction) {
        if (Minecraft.getInstance().level == null) {
            return false;
        }
        return !Minecraft.getInstance().level.getBlockState(node.getTargetPos(direction)).isAir();
    }

    private NodeFaceMode modeFor(SkyNodeBlockEntity node, Direction direction) {
        return localFaceModes.getOrDefault(direction, node.getFaceMode(direction));
    }

    private ItemStack iconFor(SkyNodeBlockEntity node, Direction direction) {
        if (Minecraft.getInstance().level == null) {
            return ItemStack.EMPTY;
        }
        BlockState state = Minecraft.getInstance().level.getBlockState(node.getTargetPos(direction));
        return state.getBlock().asItem().getDefaultInstance();
    }

    private Component targetName(SkyNodeBlockEntity node, Direction direction) {
        if (Minecraft.getInstance().level == null) {
            return Component.translatable("screen.skylogistics.no_target");
        }
        BlockState state = Minecraft.getInstance().level.getBlockState(node.getTargetPos(direction));
        if (state.isAir()) {
            return Component.translatable("screen.skylogistics.no_target");
        }
        return state.getBlock().getName();
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
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, selected);
            if (node != null) {
                ItemStack icon = iconFor(node, direction);
                if (!icon.isEmpty()) {
                    graphics.renderItem(icon, getX() + 10, getY() + 7);
                }
                graphics.drawString(font, faceShortName(direction), getX() + 3, getY() + 3,
                        active ? ConfigPanel.TEXT : ConfigPanel.MUTED, false);
                int modeColor = colorFor(modeFor(node, direction));
                graphics.fill(getX() + 6, getY() + height - 5, getX() + width - 6, getY() + height - 3, modeColor);
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
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 5,
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
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 6, textColor);
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
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 5,
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
            graphics.drawCenteredString(font, message, getX() + width / 2, getY() + (height - 8) / 2,
                    active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class RedstoneButton extends AdvancedButton {
        private RedstoneButton(int x, int y) {
            super(x, y, 96, 18, Component.translatable("screen.skylogistics.redstone"));
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
            super(x, y, 22, 18, message);
            this.delta = delta;
        }

        @Override
        public void onPress() {
            if (active) {
                ModNetworking.sendMenuAction(delta < 0
                        ? MenuAction.facePriorityDown(selectedFace)
                        : MenuAction.facePriorityUp(selectedFace));
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
            boolean enabled = node != null && isEnabled(node);
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, enabled);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 6,
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
