package com.skylogistics.client;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.menu.MenuAction;
import com.skylogistics.network.ConfiguratorLineDetailsPacket;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.RedstoneControl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

public class ConfiguratorScreen extends AbstractContainerScreen<ConfiguratorMenu> {
    private static final String SKY_NECKLACE_ID = "skylogistics:sky_necklace";
    private static final String CONFIGURATOR_TEXTURE_ROOT = "textures/gui/configurator/";
    private static final int LINE_NAME_LABEL_Y = 28;
    private static final int LINE_NAME_LABEL_GAP = 4;
    private static final int LINE_NAME_EDIT_X = 40;
    private static final int LINE_NAME_EDIT_Y = 24;
    private static final int LINE_NAME_EDIT_WIDTH = 90;
    private static final int LINE_NAME_EDIT_HEIGHT = 15;
    private static final int CONTENT_X = 6;
    private static final int CONTENT_WIDTH = 248;
    private static final int DETAIL_X = 8;
    private static final int DETAIL_Y = 72;
    private static final int DETAIL_WIDTH = 244;
    private static final int DETAIL_HEIGHT = 101;
    private static final int DETAIL_ROW_HEIGHT = 22;
    private static final int DETAIL_HEADER_HEIGHT = 12;
    private static final int DETAIL_VISIBLE_ROWS = 4;
    private static final int DETAIL_ICON_X = DETAIL_X + 6;
    private static final int DETAIL_FOOTER_Y = DETAIL_Y + DETAIL_HEIGHT + 3;
    private static final int CONTROL_START_X = 12;
    private static final int CONTROL_STEP_X = 60;
    private static final int RESOURCE_BUTTON_WIDTH = 56;
    private static final int RESOURCE_CONTROL_Y = 188;
    private static final int CONTROL_LEFT_WIDTH = 64;
    private static final int BOTTOM_CONTROL_Y = 223;
    private static final int BOTTOM_CONTROL_HEIGHT = ConfigPanel.STEPPER_HEIGHT;
    private static final int BOTTOM_CONTROL_WIDTH = 17;
    private static final int PRIORITY_DOWN_X = 176;
    private static final int PRIORITY_VALUE_X = 196;
    private static final int PRIORITY_VALUE_WIDTH = 29;
    private static final int PRIORITY_UP_X = 228;
    private static final int SLOT_LIMIT_DOWN_X = 93;
    private static final int SLOT_LIMIT_VALUE_X = 113;
    private static final int SLOT_LIMIT_VALUE_WIDTH = 29;
    private static final int SLOT_LIMIT_UP_X = 145;
    private static final int BOTTOM_GROUP_Y = 216;
    private static final long DOUBLE_CLICK_INTERVAL_MS = 250L;
    private final List<LineButton> lineButtons = new ArrayList<>();
    private final List<TypeToggleButton> typeButtons = new ArrayList<>();
    private final List<PriorityButton> priorityButtons = new ArrayList<>();
    private final List<SlotLimitButton> slotLimitButtons = new ArrayList<>();
    private RedstoneButton redstoneButton;
    private EditBox lineNameEdit;
    private boolean lineNameEditWasFocused;
    private UUID lineNameEditLine;
    private UUID detailLine;
    private List<ConfiguratorLineDetailsPacket.Entry> detailEntries = List.of();
    private final Map<ConfiguratorLineDetailsPacket.Entry, ItemStack> detailIconCache = new HashMap<>();
    private int detailScroll;
    private ConfiguratorLineDetailsPacket.Entry lastCoordinateClick;
    private long lastCoordinateClickMillis;

    public ConfiguratorScreen(ConfiguratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 260;
        imageHeight = 250;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        lineButtons.clear();
        typeButtons.clear();
        priorityButtons.clear();
        slotLimitButtons.clear();
        addLineButton(leftPos + 160, topPos + 23, 15, Component.literal("|<"), MenuAction.LINE_FIRST);
        addLineButton(leftPos + 177, topPos + 23, 15, Component.literal("<"), MenuAction.LINE_PREVIOUS);
        addLineButton(leftPos + 194, topPos + 23, 15, Component.literal(">+"), MenuAction.LINE_NEXT_OR_CREATE);
        addLineButton(leftPos + 211, topPos + 23, 15, Component.literal(">|"), MenuAction.LINE_LAST);
        addLineButton(leftPos + 228, topPos + 23, 15, Component.literal("x"), MenuAction.LINE_REMOVE_CURRENT);
        lineNameEdit = new EditBox(font, leftPos + LINE_NAME_EDIT_X, topPos + LINE_NAME_EDIT_Y,
                LINE_NAME_EDIT_WIDTH, LINE_NAME_EDIT_HEIGHT,
                Component.translatable("screen.skylogistics.line_name"));
        lineNameEdit.setMaxLength(48);
        lineNameEdit.setTextColor(ConfigPanel.FIELD_TEXT);
        lineNameEdit.setTextColorUneditable(ConfigPanel.MUTED);
        addRenderableWidget(lineNameEdit);

        addTypeButton(leftPos + CONTROL_START_X, topPos + RESOURCE_CONTROL_Y, ResourceType.ITEMS);
        addTypeButton(leftPos + CONTROL_START_X + CONTROL_STEP_X, topPos + RESOURCE_CONTROL_Y, ResourceType.FLUIDS);
        addTypeButton(leftPos + CONTROL_START_X + CONTROL_STEP_X * 2, topPos + RESOURCE_CONTROL_Y, ResourceType.ENERGY);
        addTypeButton(leftPos + CONTROL_START_X + CONTROL_STEP_X * 3, topPos + RESOURCE_CONTROL_Y, ResourceType.AUTO);
        redstoneButton = addRenderableWidget(new RedstoneButton(leftPos + 15, topPos + BOTTOM_CONTROL_Y));
        addSlotLimitButton(leftPos + SLOT_LIMIT_DOWN_X, topPos + BOTTOM_CONTROL_Y, -1, Component.literal("-"));
        addSlotLimitButton(leftPos + SLOT_LIMIT_UP_X, topPos + BOTTOM_CONTROL_Y, 1, Component.literal("+"));
        addPriorityButton(leftPos + PRIORITY_DOWN_X, topPos + BOTTOM_CONTROL_Y, -1, Component.literal("-"));
        addPriorityButton(leftPos + PRIORITY_UP_X, topPos + BOTTOM_CONTROL_Y, 1, Component.literal("+"));
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

    private void addSlotLimitButton(int x, int y, int delta, Component message) {
        SlotLimitButton button = new SlotLimitButton(x, y, delta, message);
        slotLimitButtons.add(button);
        addRenderableWidget(button);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack stack = stack();
        ConfiguratorItem.ToolConfig currentConfig = config();
        UUID currentLine = currentConfig == null ? null : currentConfig.lineId();
        if (!Objects.equals(detailLine, currentLine)) {
            detailLine = currentLine;
            detailScroll = 0;
            detailIconCache.clear();
        }
        detailScroll = Mth.clamp(detailScroll, 0, maxDetailScroll(currentLine));
        int index = menu.getLineIndex();
        int count = menu.getLineCount();
        for (LineButton button : lineButtons) {
            button.refresh(index, count);
        }
        refreshLineNameEdit(currentConfig);
        for (TypeToggleButton button : typeButtons) {
            button.active = config() != null;
        }
        for (PriorityButton button : priorityButtons) {
            button.active = config() != null;
        }
        for (SlotLimitButton button : slotLimitButtons) {
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
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        ConfiguratorLineDetailsPacket.Entry entry = hoveredDetailIcon(x, y);
        if (entry != null) {
            graphics.renderComponentTooltip(font, List.of(targetDisplayName(entry)), x, y);
            return;
        }
        entry = hoveredDetailLocation(x, y);
        if (entry != null) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.literal(pos(entry.targetPos())),
                    Component.literal(entry.dimension())), x, y);
            return;
        }
        super.renderTooltip(graphics, x, y);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ConfigPanel.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        ConfigPanel.drawContentPanel(graphics, leftPos + CONTENT_X, topPos + 20, CONTENT_WIDTH, 24);
        drawStatPanels(graphics);
        ConfigPanel.drawContentPanel(graphics, leftPos + CONTENT_X, topPos + DETAIL_Y,
                CONTENT_WIDTH, DETAIL_HEIGHT);
        drawBottomFieldset(graphics, 10, 74, Component.translatable("screen.skylogistics.redstone"));
        drawBottomFieldset(graphics, 88, 79, Component.translatable("screen.skylogistics.slot_limit"));
        drawBottomFieldset(graphics, 171, 79, Component.translatable("screen.skylogistics.priority"));
        drawBottomValueBoxes(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        ConfiguratorItem.ToolConfig config = config();
        graphics.drawString(font, title, 10, 7, ConfigPanel.TEXT, false);
        if (config == null) {
            graphics.drawString(font, Component.translatable("screen.skylogistics.configurator.unbound"),
                    10, 52, ConfigPanel.MUTED, false);
            return;
        }
        int lineIndex = menu.getLineIndex() + 1;
        int lineCount = Math.max(1, menu.getLineCount());
        Component lineNameLabel = Component.translatable("screen.skylogistics.configurator.line");
        graphics.drawString(font, lineNameLabel,
                LINE_NAME_EDIT_X - LINE_NAME_LABEL_GAP - font.width(lineNameLabel),
                LINE_NAME_LABEL_Y, ConfigPanel.TEXT, false);
        ConfigPanel.drawCenteredText(graphics, font, Component.literal(lineIndex + "/" + lineCount), 147, 28, ConfigPanel.TEXT);
        drawCenteredLabel(graphics, Component.translatable("screen.skylogistics.stat.nodes", menu.getLineNodes()),
                46, 55, ConfigPanel.TEXT);
        drawCenteredLabel(graphics, Component.translatable("screen.skylogistics.stat.extract", menu.getLineInputs()),
                130, 55, ConfigPanel.TEXT);
        drawCenteredLabel(graphics, Component.translatable("screen.skylogistics.stat.insert", menu.getLineOutputs()),
                214, 55, ConfigPanel.TEXT);
        renderLineDetails(graphics, config);
        graphics.drawString(font, Component.translatable("screen.skylogistics.configurator.offhand_applies"),
                DETAIL_X, DETAIL_FOOTER_Y, ConfigPanel.MUTED, false);
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.redstone"),
                47, 212, ConfigPanel.MUTED);
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.slot_limit"),
                127, 212, ConfigPanel.MUTED);
        ConfigPanel.drawCenteredText(graphics, font, slotLimitDisplay(config.slotLimit()),
                SLOT_LIMIT_VALUE_X + SLOT_LIMIT_VALUE_WIDTH / 2, BOTTOM_CONTROL_Y + 4, ConfigPanel.FIELD_TEXT);
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.priority"),
                210, 212, ConfigPanel.MUTED);
        ConfigPanel.drawCenteredText(graphics, font, Component.literal(String.valueOf(config.placement().priority())),
                PRIORITY_VALUE_X + PRIORITY_VALUE_WIDTH / 2, BOTTOM_CONTROL_Y + 4, ConfigPanel.FIELD_TEXT);
    }

    private void drawStatPanels(GuiGraphics graphics) {
        ConfigPanel.drawContentPanel(graphics, leftPos + CONTENT_X, topPos + 48, 80, 20);
        ConfigPanel.drawContentPanel(graphics, leftPos + 90, topPos + 48, 80, 20);
        ConfigPanel.drawContentPanel(graphics, leftPos + 174, topPos + 48, 80, 20);
    }

    private void drawBottomFieldset(GuiGraphics graphics, int x, int width, Component legend) {
        ConfigPanel.drawFieldset(graphics, leftPos + x, topPos + BOTTOM_GROUP_Y, width, font.width(legend));
    }

    private void drawBottomValueBoxes(GuiGraphics graphics) {
        ConfigPanel.drawStepperValue(graphics, leftPos + SLOT_LIMIT_VALUE_X, topPos + BOTTOM_CONTROL_Y,
                SLOT_LIMIT_VALUE_WIDTH);
        ConfigPanel.drawStepperValue(graphics, leftPos + PRIORITY_VALUE_X, topPos + BOTTOM_CONTROL_Y,
                PRIORITY_VALUE_WIDTH);
    }

    private void drawCenteredLabel(GuiGraphics graphics, Component label, int centerX, int y, int color) {
        ConfigPanel.drawCenteredText(graphics, font, label, centerX, y, color);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        ConfiguratorItem.ToolConfig config = config();
        if (config != null && isOverDetails(mouseX, mouseY)) {
            int maxScroll = maxDetailScroll(config.lineId());
            if (maxScroll > 0) {
                detailScroll = Mth.clamp(detailScroll - (int) Math.signum(delta), 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (lineNameEdit != null && lineNameEdit.isFocused()
                && minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            ConfiguratorLineDetailsPacket.Entry entry = hoveredDetailLocation(mouseX, mouseY);
            if (entry != null) {
                long now = Util.getMillis();
                if (entry.equals(lastCoordinateClick)
                        && now - lastCoordinateClickMillis <= DOUBLE_CLICK_INTERVAL_MS) {
                    lastCoordinateClick = null;
                    focusDetailTarget(entry);
                } else {
                    lastCoordinateClick = entry;
                    lastCoordinateClickMillis = now;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void focusDetailTarget(ConfiguratorLineDetailsPacket.Entry entry) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null
                || isSkyNecklaceEntry(entry)
                || !minecraft.level.dimension().location().toString().equals(entry.dimension())) {
            return;
        }
        onClose();
        ClientKleisOverlays.focusConfiguratorTarget(entry.targetPos());
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
            currentConfig = config();
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
        ConfiguratorItem.ToolConfig currentConfig = config();
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

    private void renderLineDetails(GuiGraphics graphics, ConfiguratorItem.ToolConfig config) {
        List<ConfiguratorLineDetailsPacket.Entry> entries = lineDetailEntries(config.lineId());
        if (entries.size() > DETAIL_VISIBLE_ROWS) {
            int last = Math.min(entries.size(), detailScroll + DETAIL_VISIBLE_ROWS);
            Component range = Component.literal((detailScroll + 1) + "-" + last + "/" + entries.size());
            graphics.drawString(font, range, DETAIL_X + DETAIL_WIDTH - 3 - font.width(range),
                    DETAIL_FOOTER_Y, ConfigPanel.MUTED, false);
        }
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.skylogistics.line_faces_empty"),
                    DETAIL_X + 8, DETAIL_Y + 30, ConfigPanel.MUTED, false);
            return;
        }
        renderDetailHeader(graphics);
        int maxScroll = maxDetailScroll(config.lineId());
        detailScroll = Mth.clamp(detailScroll, 0, maxScroll);
        for (int row = 0; row < DETAIL_VISIBLE_ROWS; row++) {
            int index = detailScroll + row;
            if (index >= entries.size()) {
                break;
            }
            ConfiguratorLineDetailsPacket.Entry entry = entries.get(index);
            int y = DETAIL_Y + DETAIL_HEADER_HEIGHT + 1 + row * DETAIL_ROW_HEIGHT;
            if (row > 0) {
                graphics.fill(DETAIL_X + 1, y - 2, DETAIL_X + DETAIL_WIDTH - 1, y - 1,
                        ConfigPanel.BORDER_DIM);
            }
            ItemStack icon = targetIcon(entry);
            if (icon.isEmpty()) {
                graphics.drawString(font, "?", DETAIL_ICON_X + 5, y + 5, ConfigPanel.MUTED, false);
            } else {
                graphics.renderItem(icon, DETAIL_ICON_X, y);
            }
            int modeX = DETAIL_X + 29;
            int resourceX = DETAIL_X + 59;
            int priorityX = DETAIL_X + 94;
            int redstoneX = DETAIL_X + 124;
            int locationX = DETAIL_X + 151;
            graphics.drawString(font, detailMode(entry), modeX, y + 4, modeColor(entry), false);
            graphics.drawString(font, resourceFlags(entry), resourceX, y + 4, ConfigPanel.TEXT, false);
            ConfigPanel.drawCenteredText(graphics, font, Component.literal(String.valueOf(entry.priority())),
                    priorityX + 14, y + 4, ConfigPanel.TEXT);
            drawRedstoneIcon(graphics, redstoneX + 6, y + 2, entry.redstoneControl());
            graphics.drawString(font, trimToWidth(pos(entry.targetPos()), DETAIL_WIDTH - 155),
                    locationX, y, ConfigPanel.TEXT, false);
            graphics.drawString(font, trimToWidth(entry.dimension(), DETAIL_WIDTH - 155),
                    locationX, y + 9, ConfigPanel.MUTED, false);
        }
    }

    private void renderDetailHeader(GuiGraphics graphics) {
        int y = DETAIL_Y + 2;
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.detail.device"),
                DETAIL_X + 14, y, ConfigPanel.TEXT);
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.detail.mode"),
                DETAIL_X + 38, y, ConfigPanel.TEXT);
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.detail.resources"),
                DETAIL_X + 72, y, ConfigPanel.TEXT);
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.detail.priority"),
                DETAIL_X + 108, y, ConfigPanel.TEXT);
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.detail.redstone"),
                DETAIL_X + 137, y, ConfigPanel.TEXT);
        ConfigPanel.drawCenteredText(graphics, font, Component.translatable("screen.skylogistics.detail.location"),
                DETAIL_X + 197, y, ConfigPanel.TEXT);
        graphics.fill(DETAIL_X + 1, DETAIL_Y + DETAIL_HEADER_HEIGHT - 1,
                DETAIL_X + DETAIL_WIDTH - 1, DETAIL_Y + DETAIL_HEADER_HEIGHT, ConfigPanel.BORDER_DIM);
    }

    private void drawRedstoneIcon(GuiGraphics graphics, int x, int y, RedstoneControl control) {
        ConfigPanel.drawRedstoneIcon(graphics, x, y, control);
    }

    private ResourceLocation guiTexture(String name) {
        return new ResourceLocation("skylogistics", CONFIGURATOR_TEXTURE_ROOT + name);
    }

    private boolean isOverDetails(double mouseX, double mouseY) {
        return mouseX >= leftPos + DETAIL_X && mouseX < leftPos + DETAIL_X + DETAIL_WIDTH
                && mouseY >= topPos + DETAIL_Y && mouseY < topPos + DETAIL_Y + DETAIL_HEIGHT;
    }

    private int maxDetailScroll(UUID lineId) {
        int size = lineDetailEntries(lineId).size();
        return Math.max(0, size - DETAIL_VISIBLE_ROWS);
    }

    private String detailMainLine(ConfiguratorLineDetailsPacket.Entry entry) {
        String displayName = entry.displayName().isEmpty() || isSkyNecklaceEntry(entry)
                ? ""
                : entry.displayName() + " ";
        return detailMode(entry).getString() + " "
                + displayName + resourceFlags(entry) + " P" + entry.priority() + " "
                + Component.translatable(entry.redstoneControl().translationKey()).getString();
    }

    private String detailCoordinateLine(ConfiguratorLineDetailsPacket.Entry entry) {
        return pos(entry.targetPos()) + " " + entry.dimension();
    }

    private String resourceFlags(ConfiguratorLineDetailsPacket.Entry entry) {
        return (entry.itemsEnabled() ? Component.translatable("screen.skylogistics.resource_short.items").getString() : "-")
                + (entry.fluidsEnabled() ? Component.translatable("screen.skylogistics.resource_short.fluids").getString() : "-")
                + (entry.energyEnabled() ? Component.translatable("screen.skylogistics.resource_short.energy").getString() : "-");
    }

    private String pos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private ConfiguratorLineDetailsPacket.Entry hoveredDetailIcon(double mouseX, double mouseY) {
        ConfiguratorItem.ToolConfig config = config();
        if (config == null) {
            return null;
        }
        List<ConfiguratorLineDetailsPacket.Entry> entries = lineDetailEntries(config.lineId());
        for (int row = 0; row < DETAIL_VISIBLE_ROWS; row++) {
            int index = detailScroll + row;
            if (index >= entries.size()) {
                break;
            }
            int x = leftPos + DETAIL_ICON_X;
            int y = topPos + DETAIL_Y + DETAIL_HEADER_HEIGHT + 1 + row * DETAIL_ROW_HEIGHT;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return entries.get(index);
            }
        }
        return null;
    }

    private ConfiguratorLineDetailsPacket.Entry hoveredDetailLocation(double mouseX, double mouseY) {
        ConfiguratorItem.ToolConfig config = config();
        if (config == null) {
            return null;
        }
        List<ConfiguratorLineDetailsPacket.Entry> entries = lineDetailEntries(config.lineId());
        int locationX = leftPos + DETAIL_X + 151;
        for (int row = 0; row < DETAIL_VISIBLE_ROWS; row++) {
            int index = detailScroll + row;
            if (index >= entries.size()) {
                break;
            }
            int y = topPos + DETAIL_Y + DETAIL_HEADER_HEIGHT + 1 + row * DETAIL_ROW_HEIGHT;
            if (mouseX >= locationX && mouseX < leftPos + DETAIL_X + DETAIL_WIDTH
                    && mouseY >= y && mouseY < y + DETAIL_ROW_HEIGHT) {
                return entries.get(index);
            }
        }
        return null;
    }

    private Component targetDisplayName(ConfiguratorLineDetailsPacket.Entry entry) {
        if (!entry.displayName().isEmpty()) {
            return Component.literal(entry.displayName());
        }
        ItemStack icon = targetIcon(entry);
        return icon.isEmpty() ? Component.literal(entry.targetBlockId()) : icon.getHoverName();
    }

    private ItemStack targetIcon(ConfiguratorLineDetailsPacket.Entry entry) {
        return detailIconCache.computeIfAbsent(entry, this::createTargetIcon);
    }

    private ItemStack createTargetIcon(ConfiguratorLineDetailsPacket.Entry entry) {
        if (isSkyNecklaceEntry(entry)) {
            return SkyLogisticsConfig.renderConfiguratorPlayerHeads()
                    ? playerHeadIcon(entry)
                    : ModItems.SKY_NECKLACE.get().getDefaultInstance();
        }
        ResourceLocation id = ResourceLocation.tryParse(entry.targetBlockId());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block != null) {
            ItemStack blockIcon = block.asItem().getDefaultInstance();
            if (!blockIcon.isEmpty()) {
                return blockIcon;
            }
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    private List<ConfiguratorLineDetailsPacket.Entry> lineDetailEntries(UUID lineId) {
        List<ConfiguratorLineDetailsPacket.Entry> entries = ClientConfiguratorLineDetails.entries(lineId);
        if (entries != detailEntries) {
            detailEntries = entries;
            detailIconCache.clear();
        }
        return entries;
    }

    private boolean isSkyNecklaceEntry(ConfiguratorLineDetailsPacket.Entry entry) {
        return SKY_NECKLACE_ID.equals(entry.targetBlockId());
    }

    private ItemStack playerHeadIcon(ConfiguratorLineDetailsPacket.Entry entry) {
        ItemStack icon = Items.PLAYER_HEAD.getDefaultInstance();
        String playerName = entry.displayName();
        if (!playerName.isBlank()) {
            CompoundTag owner = new CompoundTag();
            if (entry.profileId() != null) {
                owner.putUUID("Id", entry.profileId());
            }
            owner.putString("Name", playerName);
            if (!entry.profileTexture().isBlank()) {
                CompoundTag properties = new CompoundTag();
                ListTag textures = new ListTag();
                CompoundTag texture = new CompoundTag();
                texture.putString("Value", entry.profileTexture());
                if (!entry.profileTextureSignature().isBlank()) {
                    texture.putString("Signature", entry.profileTextureSignature());
                }
                textures.add(texture);
                properties.put("textures", textures);
                owner.put("Properties", properties);
            }
            icon.getOrCreateTag().put("SkullOwner", owner);
        }
        return icon;
    }

    private Component detailMode(ConfiguratorLineDetailsPacket.Entry entry) {
        return Component.translatable(entry.maintainMode()
                ? "screen.skylogistics.sky_necklace.mode.maintain"
                : entry.mode().translationKey());
    }

    private int modeColor(ConfiguratorLineDetailsPacket.Entry entry) {
        if (entry.maintainMode()) {
            return ConfigPanel.MAINTAIN_ACCENT;
        }
        NodeFaceMode mode = entry.mode();
        return switch (mode) {
            case INPUT -> ConfigPanel.EXTRACT_ACCENT;
            case OUTPUT -> ConfigPanel.INSERT_ACCENT;
            case NONE -> ConfigPanel.MUTED;
        };
    }

    private String trimToWidth(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
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

    private void previewAction(int action) {
        ItemStack stack = stack();
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.read(stack);
        if (config == null) {
            return;
        }
        if (actionWritesLineSelection(action)) {
            return;
        }
        UUID beforeLine = config.lineId();
        ConfiguratorItem.ToolConfig updated = switch (action) {
            case MenuAction.TOGGLE_ITEMS -> config.withItemsEnabled(!config.itemsEnabled());
            case MenuAction.TOGGLE_FLUIDS -> config.withFluidsEnabled(!config.fluidsEnabled());
            case MenuAction.TOGGLE_ENERGY -> config.withEnergyEnabled(!config.energyEnabled());
            case MenuAction.TOGGLE_AUTO_RESOURCES -> config.withAutoDetectResources();
            case MenuAction.CONFIG_REDSTONE -> config.cycleRedstoneControl();
            case MenuAction.CONFIG_PRIORITY_DOWN -> config.adjustPriority(-1);
            case MenuAction.CONFIG_PRIORITY_UP -> config.adjustPriority(1);
            case MenuAction.CONFIG_SLOT_LIMIT_DOWN -> config.adjustSlotLimit(-1);
            case MenuAction.CONFIG_SLOT_LIMIT_UP -> config.adjustSlotLimit(1);
            case MenuAction.CONFIG_SLOT_LIMIT_DOWN_FAST -> config.adjustSlotLimit(-10);
            case MenuAction.CONFIG_SLOT_LIMIT_UP_FAST -> config.adjustSlotLimit(10);
            default -> null;
        };
        if (updated == null) {
            return;
        }
        ConfiguratorItem.writeConfig(stack, updated);
        if (!beforeLine.equals(updated.lineId())) {
            detailLine = updated.lineId();
            detailScroll = 0;
        }
    }

    private boolean actionWritesLineSelection(int action) {
        return action == MenuAction.LINE_FIRST || action == MenuAction.LINE_PREVIOUS
                || action == MenuAction.LINE_NEXT_OR_CREATE || action == MenuAction.LINE_LAST
                || action == MenuAction.LINE_REMOVE_CURRENT;
    }

    private boolean canRemoveCurrentLine() {
        return menu.getLineInputs() <= 0 && menu.getLineOutputs() <= 0;
    }

    private void borderedBox(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        ConfigPanel.drawBox(graphics, x, y, width, height, fill, border);
    }

    private Component slotLimitDisplay(int slotLimit) {
        return slotLimit == com.skylogistics.block.entity.SkyNodeBlockEntity.ITEM_SLOT_LIMIT_UNLIMITED
                ? Component.translatable("screen.skylogistics.slot_limit.unlimited")
                : Component.literal(String.valueOf(slotLimit));
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
                case MenuAction.LINE_REMOVE_CURRENT -> count > 1 && canRemoveCurrentLine();
                default -> true;
            };
        }

        @Override
        public void onPress() {
            if (active) {
                commitLineNameEdit();
                previewAction(action);
                ModNetworking.sendMenuAction(action);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, isHovered());
            ConfigPanel.drawCenteredButtonText(graphics, font, getMessage(), getX() + width / 2, getY() + 5,
                    active);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class RedstoneButton extends AbstractButton {
        private RedstoneButton(int x, int y) {
            super(x, y, CONTROL_LEFT_WIDTH, BOTTOM_CONTROL_HEIGHT, Component.translatable("screen.skylogistics.redstone"));
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
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, isHovered());
            drawRedstoneIcon(graphics, getX() + 5, getY(), control);
            graphics.drawString(font, Component.translatable(control.translationKey()),
                    getX() + 23, getY() + 4, ConfigPanel.buttonTextColor(active), true);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class PriorityButton extends AbstractButton {
        private final int delta;

        private PriorityButton(int x, int y, int delta, Component message) {
            super(x, y, BOTTOM_CONTROL_WIDTH, BOTTOM_CONTROL_HEIGHT, message);
            this.delta = delta;
        }

        @Override
        public void onPress() {
            if (active) {
                boolean fast = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                int action = delta < 0
                        ? (fast ? MenuAction.CONFIG_PRIORITY_DOWN_FAST : MenuAction.CONFIG_PRIORITY_DOWN)
                        : (fast ? MenuAction.CONFIG_PRIORITY_UP_FAST : MenuAction.CONFIG_PRIORITY_UP);
                ModNetworking.sendMenuAction(action);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, isHovered());
            ConfigPanel.drawCenteredButtonText(graphics, font, getMessage(), getX() + width / 2, getY() + 4,
                    active);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class SlotLimitButton extends AbstractButton {
        private final int delta;

        private SlotLimitButton(int x, int y, int delta, Component message) {
            super(x, y, BOTTOM_CONTROL_WIDTH, BOTTOM_CONTROL_HEIGHT, message);
            this.delta = delta;
        }

        @Override
        public void onPress() {
            if (active) {
                boolean fast = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                int action = delta < 0
                        ? (fast ? MenuAction.CONFIG_SLOT_LIMIT_DOWN_FAST : MenuAction.CONFIG_SLOT_LIMIT_DOWN)
                        : (fast ? MenuAction.CONFIG_SLOT_LIMIT_UP_FAST : MenuAction.CONFIG_SLOT_LIMIT_UP);
                ModNetworking.sendMenuAction(action);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, isHovered());
            ConfigPanel.drawCenteredButtonText(graphics, font, getMessage(), getX() + width / 2, getY() + 4,
                    active);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private enum ResourceType {
        ITEMS("button.skylogistics.items"),
        FLUIDS("button.skylogistics.fluids"),
        ENERGY("button.skylogistics.energy_short"),
        AUTO("screen.skylogistics.configurator.auto");

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
        public void onPress() {
            if (!active) {
                return;
            }
            switch (type) {
                case ITEMS -> ModNetworking.sendMenuAction(MenuAction.TOGGLE_ITEMS);
                case FLUIDS -> ModNetworking.sendMenuAction(MenuAction.TOGGLE_FLUIDS);
                case ENERGY -> ModNetworking.sendMenuAction(MenuAction.TOGGLE_ENERGY);
                case AUTO -> ModNetworking.sendMenuAction(MenuAction.TOGGLE_AUTO_RESOURCES);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean enabled = isEnabled();
            ConfigPanel.drawImageButtonChrome(graphics, getX(), getY(), width, height,
                    active, isHovered(), enabled, ConfigPanel.RESOURCE_ACCENT);
            ConfigPanel.drawResourceIcon(graphics, getX() + 5, getY() + 2, resourceName(type), true);
            graphics.drawString(font, getMessage(), getX() + 25, getY() + 6,
                    ConfigPanel.buttonTextColor(active), true);
        }

        private String resourceName(ResourceType resource) {
            return switch (resource) {
                case ITEMS -> "item";
                case FLUIDS -> "fluid";
                case ENERGY -> "energy";
                case AUTO -> "auto";
            };
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
                case AUTO -> config.autoDetectResources();
            };
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
