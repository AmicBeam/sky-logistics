package com.skylogistics.client;

import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.properties.Property;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.menu.MenuAction;
import com.skylogistics.network.ConfiguratorLineDetailsPacket;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.RedstoneControl;
import java.util.ArrayList;
import java.util.List;
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
    private static final int LINE_NAME_LABEL_Y = 12;
    private static final int LINE_NAME_LABEL_GAP = 4;
    private static final int LINE_NAME_EDIT_X = 146;
    private static final int LINE_NAME_EDIT_Y = 7;
    private static final int LINE_NAME_EDIT_WIDTH = 92;
    private static final int LINE_NAME_EDIT_HEIGHT = 16;
    private static final int DETAIL_X = 14;
    private static final int DETAIL_Y = 76;
    private static final int DETAIL_WIDTH = 226;
    private static final int DETAIL_HEIGHT = 76;
    private static final int DETAIL_ROW_HEIGHT = 18;
    private static final int DETAIL_VISIBLE_ROWS = DETAIL_HEIGHT / DETAIL_ROW_HEIGHT;
    private static final int DETAIL_ICON_X = DETAIL_X + 5;
    private static final int DETAIL_TEXT_X = DETAIL_X + 25;
    private static final int CONTROL_START_X = 48;
    private static final int CONTROL_STEP_X = 44;
    private static final int RESOURCE_BUTTON_WIDTH = 42;
    private static final int CONTROL_LEFT_WIDTH = 76;
    private static final int STEPPER_BUTTON_WIDTH = 20;
    private static final int STEPPER_HEIGHT = 20;
    private static final int PRIORITY_ROW_Y = 218;
    private static final int PRIORITY_DOWN_X = CONTROL_START_X;
    private static final int PRIORITY_UP_X = 104;
    private static final int PRIORITY_VALUE_X = PRIORITY_DOWN_X + STEPPER_BUTTON_WIDTH;
    private static final int PRIORITY_VALUE_WIDTH = PRIORITY_UP_X - PRIORITY_VALUE_X;
    private static final int SLOT_LIMIT_ROW_Y = 192;
    private static final int SLOT_LIMIT_LABEL_X = 138;
    private static final int SLOT_LIMIT_DOWN_X = 172;
    private static final int SLOT_LIMIT_VALUE_X = SLOT_LIMIT_DOWN_X + STEPPER_BUTTON_WIDTH;
    private static final int SLOT_LIMIT_VALUE_WIDTH = 26;
    private static final int SLOT_LIMIT_UP_X = SLOT_LIMIT_VALUE_X + SLOT_LIMIT_VALUE_WIDTH;
    private final List<LineButton> lineButtons = new ArrayList<>();
    private final List<TypeToggleButton> typeButtons = new ArrayList<>();
    private final List<PriorityButton> priorityButtons = new ArrayList<>();
    private final List<SlotLimitButton> slotLimitButtons = new ArrayList<>();
    private RedstoneButton redstoneButton;
    private EditBox lineNameEdit;
    private boolean lineNameEditWasFocused;
    private UUID lineNameEditLine;
    private UUID detailLine;
    private int detailScroll;

    public ConfiguratorScreen(ConfiguratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 254;
        imageHeight = 244;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        lineButtons.clear();
        typeButtons.clear();
        priorityButtons.clear();
        slotLimitButtons.clear();
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

        addTypeButton(leftPos + CONTROL_START_X, topPos + 166, ResourceType.ITEMS);
        addTypeButton(leftPos + CONTROL_START_X + CONTROL_STEP_X, topPos + 166, ResourceType.FLUIDS);
        addTypeButton(leftPos + CONTROL_START_X + CONTROL_STEP_X * 2, topPos + 166, ResourceType.ENERGY);
        addTypeButton(leftPos + CONTROL_START_X + CONTROL_STEP_X * 3, topPos + 166, ResourceType.AUTO);
        redstoneButton = addRenderableWidget(new RedstoneButton(leftPos + CONTROL_START_X, topPos + 192));
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
        if (lineNameEdit != null && lineNameEdit.visible) {
            ConfigPanel.drawInputBox(graphics, leftPos + LINE_NAME_EDIT_X, topPos + LINE_NAME_EDIT_Y,
                    LINE_NAME_EDIT_WIDTH, LINE_NAME_EDIT_HEIGHT, lineNameEdit.isFocused());
        }
        ConfigPanel.drawContentPanel(graphics, leftPos + 8, topPos + 158, imageWidth - 16, 76);
        boolean active = config() != null;
        ConfigPanel.drawStepperValue(graphics, leftPos + SLOT_LIMIT_VALUE_X, topPos + SLOT_LIMIT_ROW_Y,
                SLOT_LIMIT_VALUE_WIDTH, STEPPER_HEIGHT, active);
        ConfigPanel.drawStepperValue(graphics, leftPos + PRIORITY_VALUE_X, topPos + PRIORITY_ROW_Y,
                PRIORITY_VALUE_WIDTH, STEPPER_HEIGHT, active);
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
        int lineIndex = menu.getLineIndex() + 1;
        int lineCount = Math.max(1, menu.getLineCount());
        Component lineNameLabel = Component.translatable("screen.skylogistics.line_name");
        graphics.drawString(font, lineNameLabel,
                LINE_NAME_EDIT_X - LINE_NAME_LABEL_GAP - font.width(lineNameLabel),
                LINE_NAME_LABEL_Y, ConfigPanel.MUTED, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.line_index", lineIndex, lineCount),
                14, 34, ConfigPanel.TEXT, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.line_monitor",
                        menu.getLineNodes(), menu.getLineInputs(), menu.getLineOutputs()),
                14, 56, ConfigPanel.MUTED, false);
        renderLineDetails(graphics, config);
        graphics.drawString(font, Component.translatable("screen.skylogistics.resources"),
                14, 172, ConfigPanel.MUTED, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.redstone"),
                14, 198, ConfigPanel.MUTED, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.slot_limit"),
                SLOT_LIMIT_LABEL_X, ConfigPanel.textCenterY(SLOT_LIMIT_ROW_Y, STEPPER_HEIGHT),
                ConfigPanel.MUTED, false);
        graphics.drawCenteredString(font, slotLimitDisplay(config.slotLimit()),
                SLOT_LIMIT_VALUE_X + SLOT_LIMIT_VALUE_WIDTH / 2,
                ConfigPanel.textCenterY(SLOT_LIMIT_ROW_Y, STEPPER_HEIGHT), ConfigPanel.TEXT);
        graphics.drawString(font, Component.translatable("screen.skylogistics.priority"),
                14, ConfigPanel.textCenterY(PRIORITY_ROW_Y, STEPPER_HEIGHT), ConfigPanel.MUTED, false);
        graphics.drawCenteredString(font, Component.literal(String.valueOf(config.placement().priority())),
                PRIORITY_VALUE_X + PRIORITY_VALUE_WIDTH / 2,
                ConfigPanel.textCenterY(PRIORITY_ROW_Y, STEPPER_HEIGHT), ConfigPanel.TEXT);
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
        List<ConfiguratorLineDetailsPacket.Entry> entries = ClientConfiguratorLineDetails.entries(config.lineId());
        graphics.drawString(font, Component.translatable("screen.skylogistics.line_faces"),
                DETAIL_X, DETAIL_Y - 11, ConfigPanel.MUTED, false);
        if (entries.size() > DETAIL_VISIBLE_ROWS) {
            int last = Math.min(entries.size(), detailScroll + DETAIL_VISIBLE_ROWS);
            graphics.drawString(font, Component.literal((detailScroll + 1) + "-" + last + "/" + entries.size()),
                    DETAIL_X + DETAIL_WIDTH - 44, DETAIL_Y - 11, ConfigPanel.MUTED, false);
        }
        ConfigPanel.drawContentPanel(graphics, DETAIL_X, DETAIL_Y, DETAIL_WIDTH, DETAIL_HEIGHT);
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.skylogistics.line_faces_empty"),
                    DETAIL_X + 8, DETAIL_Y + 30, ConfigPanel.MUTED, false);
            return;
        }
        int maxScroll = maxDetailScroll(config.lineId());
        detailScroll = Mth.clamp(detailScroll, 0, maxScroll);
        for (int row = 0; row < DETAIL_VISIBLE_ROWS; row++) {
            int index = detailScroll + row;
            if (index >= entries.size()) {
                break;
            }
            ConfiguratorLineDetailsPacket.Entry entry = entries.get(index);
            int y = DETAIL_Y + 2 + row * DETAIL_ROW_HEIGHT;
            ConfigPanel.drawListRow(graphics, DETAIL_X + 3, y - 1, DETAIL_WIDTH - 6,
                    DETAIL_ROW_HEIGHT, false, modeColor(entry.mode()));
            ItemStack icon = targetIcon(entry);
            if (icon.isEmpty()) {
                graphics.drawString(font, "?", DETAIL_ICON_X + 5, y + 5, ConfigPanel.MUTED, false);
            } else {
                graphics.renderItem(icon, DETAIL_ICON_X, y);
            }
            graphics.drawString(font, trimToWidth(detailMainLine(entry), DETAIL_WIDTH - 36),
                    DETAIL_TEXT_X, y, modeColor(entry.mode()), false);
            graphics.drawString(font, trimToWidth(detailCoordinateLine(entry), DETAIL_WIDTH - 36),
                    DETAIL_TEXT_X, y + 9, ConfigPanel.MUTED, false);
        }
    }

    private boolean isOverDetails(double mouseX, double mouseY) {
        return mouseX >= leftPos + DETAIL_X && mouseX < leftPos + DETAIL_X + DETAIL_WIDTH
                && mouseY >= topPos + DETAIL_Y && mouseY < topPos + DETAIL_Y + DETAIL_HEIGHT;
    }

    private int maxDetailScroll(UUID lineId) {
        int size = ClientConfiguratorLineDetails.entries(lineId).size();
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
        List<ConfiguratorLineDetailsPacket.Entry> entries = ClientConfiguratorLineDetails.entries(config.lineId());
        for (int row = 0; row < DETAIL_VISIBLE_ROWS; row++) {
            int index = detailScroll + row;
            if (index >= entries.size()) {
                break;
            }
            int x = leftPos + DETAIL_ICON_X;
            int y = topPos + DETAIL_Y + 2 + row * DETAIL_ROW_HEIGHT;
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
        if (isSkyNecklaceEntry(entry)) {
            return playerHeadIcon(entry);
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
            super(x, y, width, 18, message);
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
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2,
                    ConfigPanel.textCenterY(getY(), height),
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
            graphics.drawCenteredString(font, Component.translatable(control.translationKey()),
                    getX() + width / 2, ConfigPanel.textCenterY(getY(), height),
                    active ? ConfigPanel.TEXT : ConfigPanel.MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class PriorityButton extends AbstractButton {
        private final int delta;

        private PriorityButton(int x, int y, int delta, Component message) {
            super(x, y, STEPPER_BUTTON_WIDTH, STEPPER_HEIGHT, message);
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
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2,
                    ConfigPanel.textCenterY(getY(), height),
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
            super(x, y, STEPPER_BUTTON_WIDTH, STEPPER_HEIGHT, message);
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
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2,
                    ConfigPanel.textCenterY(getY(), height),
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
        AUTO("button.skylogistics.auto_detect");

        private final String translationKey;

        ResourceType(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private final class TypeToggleButton extends AbstractButton {
        private final ResourceType type;

        private TypeToggleButton(int x, int y, ResourceType type) {
            super(x, y, RESOURCE_BUTTON_WIDTH, 20, Component.translatable(type.translationKey));
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
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, enabled);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2,
                    ConfigPanel.textCenterY(getY(), height),
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
                case AUTO -> config.autoDetectResources();
            };
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
