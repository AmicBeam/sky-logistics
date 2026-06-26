package com.skylogistics.client;

import com.skylogistics.item.FilterListItem;
import com.skylogistics.menu.FilterListMenu;
import com.skylogistics.menu.MenuAction;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class FilterListScreen extends AbstractContainerScreen<FilterListMenu> {
    private static final int FILTER_GRID_X = 31;
    private static final int FILTER_GRID_Y = 25;
    private static final int FILTER_COLUMNS = 9;
    private static final int FILTER_SLOT_STEP = 18;
    private static final int FILTER_SLOT_SIZE = 18;
    private static final int FILTER_ROWS = (FilterListItem.FILTER_SLOTS + FILTER_COLUMNS - 1) / FILTER_COLUMNS;
    private static final int FILTER_PANEL_PADDING = 6;
    private static final int FILTER_PANEL_X = FILTER_GRID_X - FILTER_PANEL_PADDING;
    private static final int FILTER_PANEL_Y = FILTER_GRID_Y - FILTER_PANEL_PADDING;
    private static final int FILTER_PANEL_WIDTH = FILTER_COLUMNS * FILTER_SLOT_STEP + FILTER_PANEL_PADDING * 2;
    private static final int FILTER_PANEL_HEIGHT = FILTER_ROWS * FILTER_SLOT_STEP + FILTER_PANEL_PADDING * 2;
    private static final int CONTROL_Y = 75;
    private static final int SEGMENT_WIDTH = 21;
    private static final int SEGMENT_GROUP_WIDTH = SEGMENT_WIDTH * 2;
    private static final int CONTROL_GROUP_GAP = 8;
    private static final int ACTION_BUTTON_SIZE = 20;
    private static final int CONTROL_ROW_WIDTH = SEGMENT_GROUP_WIDTH * 3 + CONTROL_GROUP_GAP * 3 + ACTION_BUTTON_SIZE;
    private static final int COLOR_ALLOW = 0xFF8FEF8A;
    private static final int COLOR_DENY = 0xFFFF7979;
    private AbstractButton whitelistAllowButton;
    private AbstractButton whitelistDenyButton;
    private AbstractButton nbtIgnoreButton;
    private AbstractButton nbtMatchButton;
    private AbstractButton durabilityIgnoreButton;
    private AbstractButton durabilityMatchButton;
    private AbstractButton clearButton;

    public FilterListScreen(FilterListMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 224;
        imageHeight = 205;
        inventoryLabelY = 111;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + (imageWidth - CONTROL_ROW_WIDTH) / 2;
        whitelistAllowButton = addSegmentButton(x, MenuAction.FILTER_SET_WHITELIST);
        whitelistDenyButton = addSegmentButton(x + SEGMENT_WIDTH, MenuAction.FILTER_SET_BLACKLIST);
        x += SEGMENT_GROUP_WIDTH + CONTROL_GROUP_GAP;
        nbtIgnoreButton = addSegmentButton(x, MenuAction.FILTER_SET_NBT_OFF);
        nbtMatchButton = addSegmentButton(x + SEGMENT_WIDTH, MenuAction.FILTER_SET_NBT_ON);
        x += SEGMENT_GROUP_WIDTH + CONTROL_GROUP_GAP;
        durabilityIgnoreButton = addSegmentButton(x, MenuAction.FILTER_SET_DURABILITY_OFF);
        durabilityMatchButton = addSegmentButton(x + SEGMENT_WIDTH, MenuAction.FILTER_SET_DURABILITY_ON);
        x += SEGMENT_GROUP_WIDTH + CONTROL_GROUP_GAP;
        clearButton = addActionButton(x, MenuAction.FILTER_CLEAR);
        refreshButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButtons();
    }

    private void refreshButtons() {
        setEmptyMessage(whitelistAllowButton);
        setEmptyMessage(whitelistDenyButton);
        setEmptyMessage(nbtIgnoreButton);
        setEmptyMessage(nbtMatchButton);
        setEmptyMessage(durabilityIgnoreButton);
        setEmptyMessage(durabilityMatchButton);
        setEmptyMessage(clearButton);
    }

    private static void setEmptyMessage(AbstractButton button) {
        if (button != null) {
            button.setMessage(Component.empty());
        }
    }

    private AbstractButton addSegmentButton(int x, int action) {
        return addRenderableWidget(ConfigPanel.actionButton(x, topPos + CONTROL_Y, SEGMENT_WIDTH,
                Component.empty(), action));
    }

    private AbstractButton addActionButton(int x, int action) {
        return addRenderableWidget(ConfigPanel.actionButton(x, topPos + CONTROL_Y, ACTION_BUTTON_SIZE,
                Component.empty(), action));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderControlIcons(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ConfigPanel.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        ConfigPanel.drawContentPanel(graphics, leftPos + FILTER_PANEL_X, topPos + FILTER_PANEL_Y,
                FILTER_PANEL_WIDTH, FILTER_PANEL_HEIGHT);
        for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
            int x = leftPos + filterSlotX(slot);
            int y = topPos + filterSlotY(slot);
            ConfigPanel.drawSlotBackground(graphics, x, y);
            if (menu.isFluidFilter(slot)) {
                graphics.fill(x + 12, y + 12, x + 17, y + 17, 0xFF3FCBFF);
            }
        }
        renderMenuSlotBackgrounds(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 7, ConfigPanel.ACCENT, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        int slot = hoveredFilterSlot(x, y);
        if (slot >= 0 && menu.isFluidFilter(slot)) {
            FluidStack fluid = menu.getFluidFilter(slot);
            if (!fluid.isEmpty()) {
                graphics.renderTooltip(font, fluid.getDisplayName(), x, y);
                return;
            }
        }
        if (renderButtonTooltip(graphics, x, y)) {
            return;
        }
        super.renderTooltip(graphics, x, y);
    }

    public Rect2i getFilterSlotArea(int slot) {
        return new Rect2i(leftPos + filterSlotX(slot), topPos + filterSlotY(slot), FILTER_SLOT_SIZE, FILTER_SLOT_SIZE);
    }

    public boolean canAcceptGhostFilters() {
        return true;
    }

    public void setGhostItemPreview(int slot, ItemStack stack) {
        menu.setGhostItem(slot, stack);
    }

    public void setGhostFluidPreview(int slot, FluidStack stack) {
        menu.setGhostFluid(slot, stack);
    }

    private static int filterSlotX(int slot) {
        return FILTER_GRID_X + (slot % FILTER_COLUMNS) * FILTER_SLOT_STEP;
    }

    private static int filterSlotY(int slot) {
        return FILTER_GRID_Y + (slot / FILTER_COLUMNS) * FILTER_SLOT_STEP;
    }

    private int hoveredFilterSlot(int mouseX, int mouseY) {
        for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
            Rect2i area = getFilterSlotArea(slot);
            if (mouseX >= area.getX() && mouseX < area.getX() + area.getWidth()
                    && mouseY >= area.getY() && mouseY < area.getY() + area.getHeight()) {
                return slot;
            }
        }
        return -1;
    }

    private boolean renderButtonTooltip(GuiGraphics graphics, int x, int y) {
        if (whitelistAllowButton != null && whitelistAllowButton.isMouseOver(x, y)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.skylogistics.filter_list.whitelist_button"),
                    x, y);
            return true;
        }
        if (whitelistDenyButton != null && whitelistDenyButton.isMouseOver(x, y)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.skylogistics.filter_list.blacklist_button"),
                    x, y);
            return true;
        }
        if (nbtIgnoreButton != null && nbtIgnoreButton.isMouseOver(x, y)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.skylogistics.filter_list.nbt_ignore_button"),
                    x, y);
            return true;
        }
        if (nbtMatchButton != null && nbtMatchButton.isMouseOver(x, y)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.skylogistics.filter_list.nbt_match_button"),
                    x, y);
            return true;
        }
        if (durabilityIgnoreButton != null && durabilityIgnoreButton.isMouseOver(x, y)) {
            graphics.renderTooltip(font,
                    Component.translatable("tooltip.skylogistics.filter_list.durability_ignore_button"), x, y);
            return true;
        }
        if (durabilityMatchButton != null && durabilityMatchButton.isMouseOver(x, y)) {
            graphics.renderTooltip(font,
                    Component.translatable("tooltip.skylogistics.filter_list.durability_match_button"), x, y);
            return true;
        }
        if (clearButton != null && clearButton.isMouseOver(x, y)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.skylogistics.filter_list.clear_button"), x, y);
            return true;
        }
        return false;
    }

    private void renderControlIcons(GuiGraphics graphics) {
        drawSegmentGroup(graphics, whitelistAllowButton);
        drawSegmentState(graphics, whitelistAllowButton, menu.isWhitelist(), COLOR_ALLOW);
        drawSegmentState(graphics, whitelistDenyButton, !menu.isWhitelist(), COLOR_DENY);
        drawSegmentDivider(graphics, whitelistAllowButton);
        drawWhitelistIcon(graphics, whitelistAllowButton, true, menu.isWhitelist());
        drawWhitelistIcon(graphics, whitelistDenyButton, false, !menu.isWhitelist());

        drawSegmentGroup(graphics, nbtIgnoreButton);
        drawSegmentState(graphics, nbtIgnoreButton, !menu.matchNbt(), ConfigPanel.MUTED);
        drawSegmentState(graphics, nbtMatchButton, menu.matchNbt(), ConfigPanel.ACCENT);
        drawSegmentDivider(graphics, nbtIgnoreButton);
        drawNbtIcon(graphics, nbtIgnoreButton, false, !menu.matchNbt());
        drawNbtIcon(graphics, nbtMatchButton, true, menu.matchNbt());

        drawSegmentGroup(graphics, durabilityIgnoreButton);
        drawSegmentState(graphics, durabilityIgnoreButton, !menu.matchDurability(), ConfigPanel.MUTED);
        drawSegmentState(graphics, durabilityMatchButton, menu.matchDurability(), ConfigPanel.ACCENT);
        drawSegmentDivider(graphics, durabilityIgnoreButton);
        drawDurabilityIcon(graphics, durabilityIgnoreButton, false, !menu.matchDurability());
        drawDurabilityIcon(graphics, durabilityMatchButton, true, menu.matchDurability());

        drawClearIcon(graphics, clearButton);
    }

    private static void drawSegmentGroup(GuiGraphics graphics, AbstractButton leftButton) {
        if (leftButton == null) {
            return;
        }
        int x = leftButton.getX();
        int y = leftButton.getY();
        graphics.fill(x, y, x + SEGMENT_GROUP_WIDTH, y + 1, ConfigPanel.BORDER_DIM);
        graphics.fill(x, y + 19, x + SEGMENT_GROUP_WIDTH, y + 20, ConfigPanel.BORDER_DIM);
        graphics.fill(x, y, x + 1, y + 20, ConfigPanel.BORDER_DIM);
        graphics.fill(x + SEGMENT_GROUP_WIDTH - 1, y, x + SEGMENT_GROUP_WIDTH, y + 20, ConfigPanel.BORDER_DIM);
    }

    private static void drawSegmentState(GuiGraphics graphics, AbstractButton button, boolean selected, int color) {
        if (button == null) {
            return;
        }
        if (selected) {
            int fill = 0x33000000 | (color & 0x00FFFFFF);
            graphics.fill(button.getX() + 2, button.getY() + 2, button.getX() + SEGMENT_WIDTH - 2,
                    button.getY() + 18, fill);
            graphics.fill(button.getX() + 3, button.getY() + 17, button.getX() + SEGMENT_WIDTH - 3,
                    button.getY() + 18, color);
        }
    }

    private static void drawSegmentDivider(GuiGraphics graphics, AbstractButton leftButton) {
        if (leftButton == null) {
            return;
        }
        int x = leftButton.getX() + SEGMENT_WIDTH;
        graphics.fill(x - 1, leftButton.getY() + 3, x, leftButton.getY() + 17, 0x80344954);
    }

    private static void drawWhitelistIcon(GuiGraphics graphics, AbstractButton button, boolean allow, boolean selected) {
        if (button == null) {
            return;
        }
        int color = selected ? (allow ? COLOR_ALLOW : COLOR_DENY) : ConfigPanel.MUTED;
        int x = button.getX();
        int y = button.getY();
        int listColor = selected ? color : 0xFF6E8D95;
        graphics.fill(x + 5, y + 5, x + 13, y + 6, listColor);
        graphics.fill(x + 5, y + 9, x + 12, y + 10, listColor);
        graphics.fill(x + 5, y + 13, x + 11, y + 14, listColor);
        if (allow) {
            drawCheck(graphics, x + 10, y + 8, color);
        } else {
            drawCross(graphics, x + 11, y + 8, color);
        }
    }

    private static void drawNbtIcon(GuiGraphics graphics, AbstractButton button, boolean enabled, boolean selected) {
        if (button == null) {
            return;
        }
        int color = selected ? (enabled ? ConfigPanel.ACCENT : ConfigPanel.MUTED) : 0xFF6E8D95;
        int x = button.getX();
        int y = button.getY();
        graphics.fill(x + 6, y + 4, x + 14, y + 5, color);
        graphics.fill(x + 6, y + 4, x + 7, y + 16, color);
        graphics.fill(x + 13, y + 4, x + 14, y + 16, color);
        graphics.fill(x + 6, y + 15, x + 14, y + 16, color);
        graphics.fill(x + 8, y + 8, x + 12, y + 9, color);
        graphics.fill(x + 8, y + 11, x + 12, y + 12, color);
        if (!enabled) {
            drawSlash(graphics, x, y, COLOR_DENY);
        }
    }

    private static void drawDurabilityIcon(GuiGraphics graphics, AbstractButton button, boolean enabled, boolean selected) {
        if (button == null) {
            return;
        }
        int color = selected ? (enabled ? ConfigPanel.ACCENT : ConfigPanel.MUTED) : 0xFF6E8D95;
        int x = button.getX();
        int y = button.getY();
        graphics.fill(x + 7, y + 4, x + 13, y + 5, color);
        graphics.fill(x + 6, y + 5, x + 7, y + 16, color);
        graphics.fill(x + 13, y + 5, x + 14, y + 16, color);
        graphics.fill(x + 6, y + 15, x + 14, y + 16, color);
        graphics.fill(x + 8, y + 10, x + 12, y + 14, color);
        if (!enabled) {
            drawSlash(graphics, x, y, COLOR_DENY);
        }
    }

    private static void drawClearIcon(GuiGraphics graphics, AbstractButton button) {
        if (button == null) {
            return;
        }
        int x = button.getX();
        int y = button.getY();
        drawSlash(graphics, x, y, COLOR_DENY);
        for (int offset = 0; offset < 8; offset++) {
            graphics.fill(x + 6 + offset, y + 5 + offset, x + 8 + offset, y + 7 + offset, COLOR_DENY);
        }
    }

    private static void drawCheck(GuiGraphics graphics, int x, int y, int color) {
        for (int offset = 0; offset < 3; offset++) {
            graphics.fill(x + offset, y + 4 + offset, x + offset + 2, y + 6 + offset, color);
        }
        for (int offset = 0; offset < 5; offset++) {
            graphics.fill(x + 3 + offset, y + 6 - offset, x + 5 + offset, y + 8 - offset, color);
        }
    }

    private static void drawCross(GuiGraphics graphics, int x, int y, int color) {
        for (int offset = 0; offset < 6; offset++) {
            graphics.fill(x + offset, y + offset, x + offset + 2, y + offset + 2, color);
            graphics.fill(x + 5 - offset, y + offset, x + 7 - offset, y + offset + 2, color);
        }
    }

    private static void drawSlash(GuiGraphics graphics, int x, int y, int color) {
        for (int offset = 0; offset < 8; offset++) {
            graphics.fill(x + 6 + offset, y + 13 - offset, x + 8 + offset, y + 15 - offset, color);
        }
    }

    private void renderMenuSlotBackgrounds(GuiGraphics graphics) {
        for (int i = FilterListItem.FILTER_SLOTS; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.isActive()) {
                ConfigPanel.drawSlotBackground(graphics, leftPos + slot.x, topPos + slot.y);
            }
        }
    }
}
