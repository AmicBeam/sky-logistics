package com.skylogistics.client;

import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.menu.MenuAction;
import com.skylogistics.menu.TagFilterListMenu;
import com.skylogistics.network.ModNetworking;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class TagFilterListScreen extends AbstractContainerScreen<TagFilterListMenu> {
    private static final int EDIT_X = 58;
    private static final int EDIT_Y = 25;
    private static final int EDIT_WIDTH = 135;
    private static final int EDIT_HEIGHT = 18;
    private static final int TAG_PANEL_X = 25;
    private static final int TAG_PANEL_Y = 50;
    private static final int TAG_PANEL_WIDTH = 174;
    private static final int TAG_PANEL_HEIGHT = 78;
    private static final int TAG_ROW_X = TAG_PANEL_X + 6;
    private static final int TAG_ROW_Y = TAG_PANEL_Y + 6;
    private static final int TAG_ROW_WIDTH = TAG_PANEL_WIDTH - 12;
    private static final int TAG_ROW_HEIGHT = 11;
    private static final int CONTROL_Y = 134;
    private static final int SEGMENT_WIDTH = 21;
    private static final int ACTION_BUTTON_SIZE = 20;
    private static final int DROPDOWN_ROW_HEIGHT = 12;
    private static final int DROPDOWN_VISIBLE_ROWS = 5;
    private static final int COLOR_ALLOW = 0xFF8FEF8A;
    private static final int COLOR_DENY = 0xFFFF7979;

    private final TagSlotButton[] tagButtons = new TagSlotButton[TagFilterListItem.TAG_SLOTS];
    private AbstractButton whitelistAllowButton;
    private AbstractButton whitelistDenyButton;
    private AbstractButton clearButton;
    private EditBox tagEdit;
    private int selectedTagSlot;
    private boolean tagEditWasFocused;
    private boolean dropdownOpen;
    private int dropdownScroll;

    public TagFilterListScreen(TagFilterListMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 224, 242);
        inventoryLabelY = 148;
    }

    @Override
    protected void init() {
        super.init();
        tagEdit = new EditBox(font, leftPos + EDIT_X, topPos + EDIT_Y, EDIT_WIDTH, EDIT_HEIGHT,
                Component.translatable("screen.skylogistics.tag_filter_list.tag"));
        tagEdit.setMaxLength(TagFilterListItem.MAX_TAG_LENGTH);
        ConfigPanel.styleEditBox(tagEdit);
        addRenderableWidget(tagEdit);
        for (int slot = 0; slot < TagFilterListItem.TAG_SLOTS; slot++) {
            int y = topPos + TAG_ROW_Y + slot * TAG_ROW_HEIGHT;
            TagSlotButton button = new TagSlotButton(leftPos + TAG_ROW_X, y, slot);
            tagButtons[slot] = button;
            addRenderableWidget(button);
        }
        int controlX = leftPos + 75;
        whitelistAllowButton = addRenderableWidget(ConfigPanel.actionButton(controlX, topPos + CONTROL_Y,
                SEGMENT_WIDTH, Component.empty(), MenuAction.FILTER_SET_WHITELIST));
        whitelistDenyButton = addRenderableWidget(ConfigPanel.actionButton(controlX + SEGMENT_WIDTH,
                topPos + CONTROL_Y, SEGMENT_WIDTH, Component.empty(), MenuAction.FILTER_SET_BLACKLIST));
        clearButton = addRenderableWidget(ConfigPanel.actionButton(controlX + SEGMENT_WIDTH * 2 + 8,
                topPos + CONTROL_Y, ACTION_BUTTON_SIZE, Component.empty(), MenuAction.FILTER_CLEAR));
        refreshEdit(false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButtons();
        refreshEdit(false);
        dropdownScroll = Mth.clamp(dropdownScroll, 0, maxDropdownScroll());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderControlIcons(graphics);
        renderDropdown(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        drawScreenBackground(graphics);
        ConfigPanel.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        if (tagEdit != null) {
            ConfigPanel.drawInputBox(graphics, leftPos + EDIT_X, topPos + EDIT_Y, EDIT_WIDTH, EDIT_HEIGHT,
                    tagEdit.isFocused());
        }
        ConfigPanel.drawContentPanel(graphics, leftPos + TAG_PANEL_X, topPos + TAG_PANEL_Y,
                TAG_PANEL_WIDTH, TAG_PANEL_HEIGHT);
        ConfigPanel.drawSlotBackground(graphics, leftPos + TagFilterListMenu.SAMPLE_SLOT_X,
                topPos + TagFilterListMenu.SAMPLE_SLOT_Y);
        renderMenuSlotBackgrounds(graphics);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, (imageWidth - font.width(title)) / 2, 7, ConfigPanel.ACCENT, false);
        graphics.text(font, Component.translatable("screen.skylogistics.tag_filter_list.sample"),
                31, 17, ConfigPanel.MUTED, false);
        graphics.text(font, Component.translatable("screen.skylogistics.tag_filter_list.tags"),
                TAG_PANEL_X, TAG_PANEL_Y - 10, ConfigPanel.MUTED, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int x, int y) {
        if (renderButtonTooltip(graphics, x, y)) {
            return;
        }
        super.extractTooltip(graphics, x, y);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int option = hoveredDropdownOption(mouseX, mouseY);
        if (option >= 0) {
            selectDropdownOption(option);
            return true;
        }
        boolean clickedEdit = tagEdit != null && tagEdit.isMouseOver(mouseX, mouseY);
        if (tagEdit != null && tagEdit.isFocused() && !clickedEdit) {
            commitEdit();
            tagEdit.setFocused(false);
            setFocused(null);
        }
        if (clickedEdit && !sampleTags().isEmpty()) {
            dropdownOpen = true;
            return true;
        }
        if (!isOverDropdown(mouseX, mouseY)) {
            dropdownOpen = false;
        }
        return super.mouseClicked(event, doubleClick);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return handleMouseScrolled(mouseX, mouseY, delta);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return handleMouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (tagEdit != null && tagEdit.isFocused()
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            commitEdit();
            tagEdit.setFocused(false);
            setFocused(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        commitEdit();
        super.removed();
    }

    public Rect2i getSampleSlotArea() {
        return new Rect2i(leftPos + TagFilterListMenu.SAMPLE_SLOT_X, topPos + TagFilterListMenu.SAMPLE_SLOT_Y,
                18, 18);
    }

    public void setGhostSamplePreview(ItemStack stack) {
        menu.setGhostSample(stack);
        dropdownOpen = !sampleTags().isEmpty();
        dropdownScroll = 0;
        refreshEdit(true);
    }

    private void refreshButtons() {
        setEmptyMessage(whitelistAllowButton);
        setEmptyMessage(whitelistDenyButton);
        setEmptyMessage(clearButton);
    }

    private static void setEmptyMessage(AbstractButton button) {
        if (button != null) {
            button.setMessage(Component.empty());
        }
    }

    private void refreshEdit(boolean force) {
        if (tagEdit == null) {
            return;
        }
        boolean focused = tagEdit.isFocused();
        if (tagEditWasFocused && !focused) {
            commitEdit();
            focused = tagEdit.isFocused();
        }
        tagEditWasFocused = focused;
        boolean hasOptions = !sampleTags().isEmpty();
        tagEdit.setEditable(!hasOptions);
        String tag = menu.getTag(selectedTagSlot);
        if (force || hasOptions || !focused) {
            tagEdit.setValue(tag);
        }
    }

    private void commitEdit() {
        if (tagEdit == null || !sampleTags().isEmpty()) {
            return;
        }
        String normalized = TagFilterListItem.normalizeTag(tagEdit.getValue());
        if (!normalized.equals(menu.getTag(selectedTagSlot))) {
            menu.setTag(selectedTagSlot, normalized);
            ModNetworking.sendTagFilterTag(selectedTagSlot, normalized);
        }
        tagEdit.setValue(normalized);
    }

    private List<String> sampleTags() {
        return menu.sampleTags();
    }

    private void drawScreenBackground(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0xC0101010);
    }

    private boolean handleMouseScrolled(double mouseX, double mouseY, double delta) {
        if (dropdownOpen && isOverDropdown(mouseX, mouseY)) {
            dropdownScroll = Mth.clamp(dropdownScroll - (int) Math.signum(delta), 0, maxDropdownScroll());
            return true;
        }
        return false;
    }

    private int maxDropdownScroll() {
        return Math.max(0, sampleTags().size() - DROPDOWN_VISIBLE_ROWS);
    }

    private boolean isOverDropdown(double mouseX, double mouseY) {
        if (!dropdownOpen || sampleTags().isEmpty()) {
            return false;
        }
        int rows = Math.min(DROPDOWN_VISIBLE_ROWS, sampleTags().size());
        int x = leftPos + EDIT_X;
        int y = topPos + EDIT_Y + EDIT_HEIGHT + 1;
        return mouseX >= x && mouseX < x + EDIT_WIDTH
                && mouseY >= y && mouseY < y + rows * DROPDOWN_ROW_HEIGHT;
    }

    private int hoveredDropdownOption(double mouseX, double mouseY) {
        if (!isOverDropdown(mouseX, mouseY)) {
            return -1;
        }
        int relativeY = (int) mouseY - (topPos + EDIT_Y + EDIT_HEIGHT + 1);
        int index = dropdownScroll + relativeY / DROPDOWN_ROW_HEIGHT;
        return index >= sampleTags().size() ? -1 : index;
    }

    private void selectDropdownOption(int option) {
        String tag = sampleTags().get(option);
        menu.setTag(selectedTagSlot, tag);
        ModNetworking.sendTagFilterTag(selectedTagSlot, tag);
        tagEdit.setValue(tag);
        dropdownOpen = false;
    }

    private void renderDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!dropdownOpen || sampleTags().isEmpty()) {
            return;
        }
        List<String> options = sampleTags();
        int rows = Math.min(DROPDOWN_VISIBLE_ROWS, options.size());
        int x = leftPos + EDIT_X;
        int y = topPos + EDIT_Y + EDIT_HEIGHT + 1;
        ConfigPanel.drawContentPanel(graphics, x, y, EDIT_WIDTH, rows * DROPDOWN_ROW_HEIGHT + 2);
        for (int row = 0; row < rows; row++) {
            int index = dropdownScroll + row;
            if (index >= options.size()) {
                break;
            }
            int rowY = y + 1 + row * DROPDOWN_ROW_HEIGHT;
            boolean hovered = hoveredDropdownOption(mouseX, mouseY) == index;
            if (hovered) {
                graphics.fill(x + 1, rowY, x + EDIT_WIDTH - 1, rowY + DROPDOWN_ROW_HEIGHT, 0x553E8B99);
            }
            graphics.text(font, trimToWidth("#" + options.get(index), EDIT_WIDTH - 8),
                    x + 4, rowY + 2, hovered ? ConfigPanel.ACCENT : ConfigPanel.TEXT, false);
        }
    }

    private boolean renderButtonTooltip(GuiGraphicsExtractor graphics, int x, int y) {
        if (whitelistAllowButton != null && whitelistAllowButton.isMouseOver(x, y)) {
            graphics.setTooltipForNextFrame(font, Component.translatable("tooltip.skylogistics.filter_list.whitelist_button"),
                    x, y);
            return true;
        }
        if (whitelistDenyButton != null && whitelistDenyButton.isMouseOver(x, y)) {
            graphics.setTooltipForNextFrame(font, Component.translatable("tooltip.skylogistics.filter_list.blacklist_button"),
                    x, y);
            return true;
        }
        if (clearButton != null && clearButton.isMouseOver(x, y)) {
            graphics.setTooltipForNextFrame(font, Component.translatable("tooltip.skylogistics.filter_list.clear_button"), x, y);
            return true;
        }
        if (tagEdit != null && tagEdit.isMouseOver(x, y) && !sampleTags().isEmpty()) {
            graphics.setTooltipForNextFrame(font, Component.translatable("tooltip.skylogistics.tag_filter_list.dropdown"),
                    x, y);
            return true;
        }
        return false;
    }

    private void renderControlIcons(GuiGraphicsExtractor graphics) {
        drawSegmentGroup(graphics, whitelistAllowButton);
        drawSegmentState(graphics, whitelistAllowButton, menu.isWhitelist(), COLOR_ALLOW);
        drawSegmentState(graphics, whitelistDenyButton, !menu.isWhitelist(), COLOR_DENY);
        drawSegmentDivider(graphics, whitelistAllowButton);
        drawWhitelistIcon(graphics, whitelistAllowButton, true, menu.isWhitelist());
        drawWhitelistIcon(graphics, whitelistDenyButton, false, !menu.isWhitelist());
        drawClearIcon(graphics, clearButton);
    }

    private static void drawSegmentGroup(GuiGraphicsExtractor graphics, AbstractButton leftButton) {
        if (leftButton == null) {
            return;
        }
        int x = leftButton.getX();
        int y = leftButton.getY();
        graphics.fill(x, y, x + SEGMENT_WIDTH * 2, y + 1, ConfigPanel.BORDER_DIM);
        graphics.fill(x, y + 19, x + SEGMENT_WIDTH * 2, y + 20, ConfigPanel.BORDER_DIM);
        graphics.fill(x, y, x + 1, y + 20, ConfigPanel.BORDER_DIM);
        graphics.fill(x + SEGMENT_WIDTH * 2 - 1, y, x + SEGMENT_WIDTH * 2, y + 20, ConfigPanel.BORDER_DIM);
    }

    private static void drawSegmentState(GuiGraphicsExtractor graphics, AbstractButton button, boolean selected, int color) {
        if (button == null || !selected) {
            return;
        }
        int fill = 0x33000000 | (color & 0x00FFFFFF);
        graphics.fill(button.getX() + 2, button.getY() + 2, button.getX() + SEGMENT_WIDTH - 2,
                button.getY() + 18, fill);
        graphics.fill(button.getX() + 3, button.getY() + 17, button.getX() + SEGMENT_WIDTH - 3,
                button.getY() + 18, color);
    }

    private static void drawSegmentDivider(GuiGraphicsExtractor graphics, AbstractButton leftButton) {
        if (leftButton == null) {
            return;
        }
        int x = leftButton.getX() + SEGMENT_WIDTH;
        graphics.fill(x - 1, leftButton.getY() + 3, x, leftButton.getY() + 17, 0x80344954);
    }

    private static void drawWhitelistIcon(GuiGraphicsExtractor graphics, AbstractButton button, boolean allow, boolean selected) {
        if (button == null) {
            return;
        }
        int color = selected ? (allow ? COLOR_ALLOW : COLOR_DENY) : ConfigPanel.MUTED;
        int x = button.getX();
        int y = button.getY();
        graphics.fill(x + 5, y + 5, x + 13, y + 6, color);
        graphics.fill(x + 5, y + 9, x + 12, y + 10, color);
        graphics.fill(x + 5, y + 13, x + 11, y + 14, color);
        if (allow) {
            drawCheck(graphics, x + 10, y + 8, color);
        } else {
            drawCross(graphics, x + 11, y + 8, color);
        }
    }

    private static void drawClearIcon(GuiGraphicsExtractor graphics, AbstractButton button) {
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

    private static void drawCheck(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int offset = 0; offset < 3; offset++) {
            graphics.fill(x + offset, y + 4 + offset, x + offset + 2, y + 6 + offset, color);
        }
        for (int offset = 0; offset < 5; offset++) {
            graphics.fill(x + 3 + offset, y + 6 - offset, x + 5 + offset, y + 8 - offset, color);
        }
    }

    private static void drawCross(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int offset = 0; offset < 6; offset++) {
            graphics.fill(x + offset, y + offset, x + offset + 2, y + offset + 2, color);
            graphics.fill(x + 5 - offset, y + offset, x + 7 - offset, y + offset + 2, color);
        }
    }

    private static void drawSlash(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int offset = 0; offset < 8; offset++) {
            graphics.fill(x + 6 + offset, y + 13 - offset, x + 8 + offset, y + 15 - offset, color);
        }
    }

    private void renderMenuSlotBackgrounds(GuiGraphicsExtractor graphics) {
        for (int i = 1; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.isActive()) {
                ConfigPanel.drawSlotBackground(graphics, leftPos + slot.x, topPos + slot.y);
            }
        }
    }

    private String trimToWidth(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private final class TagSlotButton extends AbstractButton {
        private final int slot;

        private TagSlotButton(int x, int y, int slot) {
            super(x, y, TAG_ROW_WIDTH, TAG_ROW_HEIGHT, Component.empty());
            this.slot = slot;
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            commitEdit();
            selectedTagSlot = slot;
            dropdownOpen = !sampleTags().isEmpty();
            dropdownScroll = 0;
            refreshEdit(true);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = selectedTagSlot == slot;
            ConfigPanel.drawButtonChrome(graphics, getX(), getY(), width, height, active, selected);
            String prefix = (slot + 1) + ". ";
            String tag = menu.getTag(slot);
            String text = prefix + (tag.isBlank() ? "-" : "#" + tag);
            graphics.text(font, trimToWidth(text, width - 6), getX() + 3, getY() + 2,
                    selected ? ConfigPanel.ACCENT : ConfigPanel.TEXT, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
