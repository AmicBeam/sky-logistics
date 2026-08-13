package com.skylogistics.client;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
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
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

public class ConfiguratorScreen extends AbstractContainerScreen<ConfiguratorMenu> {
    private static final String SKY_NECKLACE_ID = "skylogistics:sky_necklace";
    private static final int LINE_NAME_LABEL_Y = 28;
    private static final int LINE_NAME_LABEL_GAP = 4;
    private static final int LINE_NAME_EDIT_X = 46;
    private static final int LINE_NAME_EDIT_Y = 24;
    private static final int LINE_NAME_EDIT_WIDTH = 90;
    private static final int LINE_NAME_EDIT_HEIGHT = 15;
    private static final int DETAIL_X = 8;
    private static final int DETAIL_Y = 82;
    private static final int DETAIL_WIDTH = 244;
    private static final int DETAIL_HEIGHT = 91;
    private static final int DETAIL_ROW_HEIGHT = 19;
    private static final int DETAIL_HEADER_HEIGHT = 12;
    private static final int DETAIL_VISIBLE_ROWS = 4;
    private static final int DETAIL_ICON_X = DETAIL_X + 6;
    private static final int CONTROL_START_X = 10;
    private static final int CONTROL_STEP_X = 61;
    private static final int RESOURCE_BUTTON_WIDTH = 56;
    private static final int CONTROL_LEFT_WIDTH = 70;
    private static final int PRIORITY_ROW_Y = 222;
    private static final int PRIORITY_DOWN_X = 174;
    private static final int PRIORITY_VALUE_X = 196;
    private static final int PRIORITY_VALUE_WIDTH = 34;
    private static final int PRIORITY_UP_X = 232;
    private static final int SLOT_LIMIT_ROW_Y = 222;
    private static final int SLOT_LIMIT_LABEL_X = 90;
    private static final int SLOT_LIMIT_DOWN_X = 90;
    private static final int SLOT_LIMIT_VALUE_X = 112;
    private static final int SLOT_LIMIT_VALUE_WIDTH = 34;
    private static final int SLOT_LIMIT_UP_X = 148;
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
        addLineButton(leftPos + 166, topPos + 23, 17, Component.literal("|<"), MenuAction.LINE_FIRST);
        addLineButton(leftPos + 185, topPos + 23, 17, Component.literal("<"), MenuAction.LINE_PREVIOUS);
        addLineButton(leftPos + 204, topPos + 23, 17, Component.literal(">+"), MenuAction.LINE_NEXT_OR_CREATE);
        addLineButton(leftPos + 223, topPos + 23, 17, Component.literal(">|"), MenuAction.LINE_LAST);
        addLineButton(leftPos + 242, topPos + 23, 16, Component.literal("x"), MenuAction.LINE_REMOVE_CURRENT);
        lineNameEdit = new EditBox(font, leftPos + LINE_NAME_EDIT_X, topPos + LINE_NAME_EDIT_Y,
                LINE_NAME_EDIT_WIDTH, LINE_NAME_EDIT_HEIGHT,
                Component.translatable("screen.skylogistics.line_name"));
        lineNameEdit.setMaxLength(48);
        lineNameEdit.setTextColor(ConfigPanel.TEXT);
        lineNameEdit.setTextColorUneditable(ConfigPanel.MUTED);
        addRenderableWidget(lineNameEdit);

        addTypeButton(leftPos + CONTROL_START_X, topPos + 181, ResourceType.ITEMS);
        addTypeButton(leftPos + CONTROL_START_X + CONTROL_STEP_X, topPos + 181, ResourceType.FLUIDS);
        addTypeButton(leftPos + CONTROL_START_X + CONTROL_STEP_X * 2, topPos + 181, ResourceType.ENERGY);
        addTypeButton(leftPos + CONTROL_START_X + CONTROL_STEP_X * 3, topPos + 181, ResourceType.AUTO);
        redstoneButton = addRenderableWidget(new RedstoneButton(leftPos + CONTROL_START_X, topPos + 222));
        addSlotLimitButton(leftPos + SLOT_LIMIT_DOWN_X, topPos + SLOT_LIMIT_ROW_Y, -1, Component.literal("-"));
        addSlotLimitButton(leftPos + SLOT_LIMIT_UP_X, topPos + SLOT_LIMIT_ROW_Y, 1, Component.literal("+"));
        addPriorityButton(leftPos + PRIORITY_DOWN_X, topPos + PRIORITY_ROW_Y, -1, Component.literal("-"));
        addPriorityButton(leftPos + PRIORITY_UP_X, topPos + PRIORITY_ROW_Y, 1, Component.literal("+"));
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
        renderBackground(graphics, mouseX, mouseY, partialTick);
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
        super.renderTooltip(graphics, x, y);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ConfigPanel.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        ConfigPanel.drawContentPanel(graphics, leftPos + 6, topPos + 20, 248, 24);
        drawStatPanels(graphics);
        ConfigPanel.drawContentPanel(graphics, leftPos + DETAIL_X, topPos + DETAIL_Y,
                DETAIL_WIDTH, DETAIL_HEIGHT);
        ConfigPanel.drawContentPanel(graphics, leftPos + 7, topPos + 216, 75, 31);
        ConfigPanel.drawContentPanel(graphics, leftPos + 87, topPos + 216, 75, 31);
        ConfigPanel.drawContentPanel(graphics, leftPos + 167, topPos + 216, 86, 31);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        ConfiguratorItem.ToolConfig config = config();
        graphics.drawString(font, title, 10, 7, ConfigPanel.ACCENT, false);
        if (config == null) {
            graphics.drawString(font, Component.translatable("screen.skylogistics.configurator.unbound"),
                    10, 52, ConfigPanel.MUTED, false);
            return;
        }
        int lineIndex = menu.getLineIndex() + 1;
        int lineCount = Math.max(1, menu.getLineCount());
        Component lineNameLabel = Component.translatable("screen.skylogistics.line_name");
        graphics.drawString(font, lineNameLabel,
                LINE_NAME_EDIT_X - LINE_NAME_LABEL_GAP - font.width(lineNameLabel),
                LINE_NAME_LABEL_Y, ConfigPanel.MUTED, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.line_index", lineIndex, lineCount),
                140, 28, ConfigPanel.TEXT, false);
        drawCenteredLabel(graphics, Component.translatable("screen.skylogistics.stat.nodes", menu.getLineNodes()),
                46, 55, ConfigPanel.MUTED);
        drawCenteredLabel(graphics, Component.translatable("screen.skylogistics.stat.extract", menu.getLineInputs()),
                130, 55, ConfigPanel.MUTED);
        drawCenteredLabel(graphics, Component.translatable("screen.skylogistics.stat.insert", menu.getLineOutputs()),
                214, 55, ConfigPanel.MUTED);
        renderLineDetails(graphics, config);
        graphics.drawString(font, Component.translatable("screen.skylogistics.redstone"),
                11, 215, ConfigPanel.MUTED, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.slot_limit"),
                111, 215, ConfigPanel.MUTED, false);
        graphics.drawCenteredString(font, slotLimitDisplay(config.slotLimit()),
                SLOT_LIMIT_VALUE_X + SLOT_LIMIT_VALUE_WIDTH / 2, SLOT_LIMIT_ROW_Y + 6, ConfigPanel.TEXT);
        graphics.drawString(font, Component.translatable("screen.skylogistics.priority"),
                193, 215, ConfigPanel.MUTED, false);
        graphics.drawCenteredString(font, Component.literal(String.valueOf(config.placement().priority())),
                PRIORITY_VALUE_X + PRIORITY_VALUE_WIDTH / 2, PRIORITY_ROW_Y + 6, ConfigPanel.TEXT);
    }

    private void drawStatPanels(GuiGraphics graphics) {
        ConfigPanel.drawContentPanel(graphics, leftPos + 7, topPos + 48, 79, 20);
        ConfigPanel.drawContentPanel(graphics, leftPos + 90, topPos + 48, 80, 20);
        ConfigPanel.drawContentPanel(graphics, leftPos + 174, topPos + 48, 79, 20);
    }

    private void drawCenteredLabel(GuiGraphics graphics, Component label, int centerX, int y, int color) {
        graphics.drawCenteredString(font, label, centerX, y, color);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ConfiguratorItem.ToolConfig config = config();
        if (config != null && isOverDetails(mouseX, mouseY)) {
            int maxScroll = maxDetailScroll(config.lineId());
            if (maxScroll > 0) {
                detailScroll = Mth.clamp(detailScroll - (int) Math.signum(scrollY), 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        graphics.drawCenteredString(font, Component.translatable("screen.skylogistics.line_faces"),
                DETAIL_X + DETAIL_WIDTH / 2, DETAIL_Y - 10, ConfigPanel.MUTED);
        if (entries.size() > DETAIL_VISIBLE_ROWS) {
            int last = Math.min(entries.size(), detailScroll + DETAIL_VISIBLE_ROWS);
            graphics.drawString(font, Component.literal((detailScroll + 1) + "-" + last + "/" + entries.size()),
                    DETAIL_X + DETAIL_WIDTH - 42, DETAIL_Y - 10, ConfigPanel.MUTED, false);
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
                graphics.fill(DETAIL_X + 1, y - 2, DETAIL_X + DETAIL_WIDTH - 1, y - 1, 0xFF344044);
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
            graphics.drawString(font, Component.translatable(entry.mode().translationKey()),
                    modeX, y + 4, modeColor(entry.mode()), false);
            graphics.drawString(font, resourceFlags(entry), resourceX, y + 4, ConfigPanel.TEXT, false);
            graphics.drawCenteredString(font, Component.literal(String.valueOf(entry.priority())),
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
        graphics.drawCenteredString(font, Component.translatable("screen.skylogistics.detail.device"), DETAIL_X + 14, y, ConfigPanel.MUTED);
        graphics.drawCenteredString(font, Component.translatable("screen.skylogistics.detail.mode"), DETAIL_X + 43, y, ConfigPanel.MUTED);
        graphics.drawCenteredString(font, Component.translatable("screen.skylogistics.detail.resources"), DETAIL_X + 75, y, ConfigPanel.MUTED);
        graphics.drawCenteredString(font, Component.translatable("screen.skylogistics.detail.priority"), DETAIL_X + 108, y, ConfigPanel.MUTED);
        graphics.drawCenteredString(font, Component.translatable("screen.skylogistics.detail.redstone"), DETAIL_X + 137, y, ConfigPanel.MUTED);
        graphics.drawCenteredString(font, Component.translatable("screen.skylogistics.detail.location"), DETAIL_X + 197, y, ConfigPanel.MUTED);
        graphics.fill(DETAIL_X + 1, DETAIL_Y + DETAIL_HEADER_HEIGHT - 1,
                DETAIL_X + DETAIL_WIDTH - 1, DETAIL_Y + DETAIL_HEADER_HEIGHT, 0xFF344044);
    }

    private void drawRedstoneIcon(GuiGraphics graphics, int x, int y, RedstoneControl control) {
        int dark = 0xFF381010;
        int red = 0xFFE32A20;
        int bright = 0xFFFFD740;
        int muted = 0xFF687176;
        int line = switch (control) {
            case HIGH -> red;
            case LOW -> dark;
            case IGNORE -> 0xFF9D3730;
            case DISABLED -> muted;
        };
        graphics.fill(x + 4, y + 5, x + 6, y + 14, control == RedstoneControl.DISABLED ? muted : 0xFF8B5A2B);
        graphics.fill(x + 2, y + 2, x + 8, y + 6, line);
        if (control == RedstoneControl.HIGH) graphics.fill(x + 3, y + 1, x + 7, y + 3, bright);
        else if (control == RedstoneControl.LOW) graphics.fill(x + 3, y + 2, x + 7, y + 4, dark);
        else if (control == RedstoneControl.DISABLED) graphics.fill(x + 1, y + 7, x + 9, y + 9, 0xFFC5322A);
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
        return Component.translatable(entry.mode().translationKey()).getString() + " "
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
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block != null) {
            ItemStack blockIcon = block.asItem().getDefaultInstance();
            if (!blockIcon.isEmpty()) {
                return blockIcon;
            }
        }
        Item item = BuiltInRegistries.ITEM.get(id);
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
            PropertyMap properties = new PropertyMap();
            if (!entry.profileTexture().isBlank()) {
                Property texture = entry.profileTextureSignature().isBlank()
                        ? new Property("textures", entry.profileTexture())
                        : new Property("textures", entry.profileTexture(), entry.profileTextureSignature());
                properties.put("textures", texture);
            }
            icon.set(DataComponents.PROFILE,
                    new ResolvableProfile(Optional.of(playerName), Optional.ofNullable(entry.profileId()), properties));
        }
        return icon;
    }

    private int modeColor(NodeFaceMode mode) {
        return switch (mode) {
            case INPUT -> 0xFFFFB56B;
            case OUTPUT -> 0xFF7DEBFF;
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
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
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
            super(x, y, CONTROL_LEFT_WIDTH, 20, Component.translatable("screen.skylogistics.redstone"));
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
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
            drawRedstoneIcon(graphics, getX() + 7, getY() + 2, control);
            graphics.drawString(font, Component.translatable(control.translationKey()),
                    getX() + 24, getY() + 6, active ? ConfigPanel.TEXT : ConfigPanel.MUTED, false);
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
                boolean fast = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                int action = delta < 0
                        ? (fast ? MenuAction.CONFIG_PRIORITY_DOWN_FAST : MenuAction.CONFIG_PRIORITY_DOWN)
                        : (fast ? MenuAction.CONFIG_PRIORITY_UP_FAST : MenuAction.CONFIG_PRIORITY_UP);
                ModNetworking.sendMenuAction(action);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 6,
                    active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class SlotLimitButton extends AbstractButton {
        private final int delta;

        private SlotLimitButton(int x, int y, int delta, Component message) {
            super(x, y, 22, 20, message);
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
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, false);
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
        ENERGY("button.skylogistics.energy"),
        AUTO("screen.skylogistics.configurator.auto");

        private final String translationKey;

        ResourceType(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private final class TypeToggleButton extends AbstractButton {
        private final ResourceType type;

        private TypeToggleButton(int x, int y, ResourceType type) {
            super(x, y, RESOURCE_BUTTON_WIDTH, 27, Component.translatable(type.translationKey));
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
            drawResourceButtonChrome(graphics, enabled);
            drawResourceIcon(graphics, getX() + 5, getY() + 5, type, enabled);
            graphics.drawString(font, getMessage(), getX() + 23, getY() + 10,
                    enabled ? ConfigPanel.ACCENT : ConfigPanel.MUTED, false);
        }

        private void drawResourceButtonChrome(GuiGraphics graphics, boolean selected) {
            int border = selected ? 0xFFFFB228 : (active ? 0xFF59666A : ConfigPanel.BORDER_DIM);
            ConfigPanel.drawBox(graphics, getX(), getY(), width, height, 0xFF071D24, border);
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 13, 0xFF0B2931);
        }

        private void drawResourceIcon(GuiGraphics graphics, int x, int y, ResourceType resource, boolean selected) {
            int gray = 0xFF899397;
            int color = selected ? switch (resource) {
                case ITEMS -> 0xFFE59A20;
                case FLUIDS -> 0xFF32AEDC;
                case ENERGY -> 0xFFFFCA37;
                case AUTO -> 0xFF5BC448;
            } : gray;
            int shadow = selected ? switch (resource) {
                case ITEMS -> 0xFF70420D;
                case FLUIDS -> 0xFF14536B;
                case ENERGY -> 0xFF7B5B12;
                case AUTO -> 0xFF285F25;
            } : 0xFF3C4548;
            switch (resource) {
                case ITEMS -> {
                    graphics.fill(x + 1, y + 3, x + 15, y + 16, shadow);
                    graphics.fill(x + 2, y + 2, x + 14, y + 14, color);
                    graphics.fill(x + 7, y + 6, x + 10, y + 12, 0xFFE7ECEC);
                }
                case FLUIDS -> {
                    graphics.fill(x + 7, y + 1, x + 10, y + 5, color);
                    graphics.fill(x + 4, y + 5, x + 13, y + 13, color);
                    graphics.fill(x + 6, y + 12, x + 11, y + 16, shadow);
                }
                case ENERGY -> {
                    graphics.fill(x + 8, y + 1, x + 13, y + 7, color);
                    graphics.fill(x + 4, y + 7, x + 11, y + 11, color);
                    graphics.fill(x + 5, y + 11, x + 9, y + 16, shadow);
                }
                case AUTO -> {
                    graphics.fill(x + 3, y + 3, x + 14, y + 5, color);
                    graphics.fill(x + 2, y + 5, x + 5, y + 14, color);
                    graphics.fill(x + 4, y + 14, x + 14, y + 16, shadow);
                    graphics.fill(x + 12, y + 5, x + 15, y + 14, shadow);
                }
            }
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
