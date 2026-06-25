package com.skylogistics.client;

import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.FilterListItem.Attribute;
import com.skylogistics.menu.FilterListMenu;
import com.skylogistics.menu.MenuAction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.FluidStack;

public class FilterListScreen extends AbstractContainerScreen<FilterListMenu> {
    private static final int FILTER_GRID_X = 24;
    private static final int FILTER_GRID_Y = 48;
    private static final int FILTER_SLOT_STEP = 22;
    private static final int FILTER_SLOT_SIZE = 18;
    private Button modeButton;
    private Button whitelistButton;
    private Button tagsButton;
    private Button modsButton;
    private final List<Button> attributeButtons = new ArrayList<>();

    public FilterListScreen(FilterListMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 224;
        imageHeight = 218;
        inventoryLabelY = 118;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = addRenderableWidget(ConfigPanel.actionButton(leftPos + 8, topPos + 18, 44,
                modeLabel(), MenuAction.FILTER_TOGGLE_MODE));
        whitelistButton = addRenderableWidget(ConfigPanel.actionButton(leftPos + 56, topPos + 18, 54,
                whitelistLabel(), MenuAction.FILTER_TOGGLE_WHITELIST));
        tagsButton = addRenderableWidget(ConfigPanel.actionButton(leftPos + 114, topPos + 18, 34,
                tagsLabel(), MenuAction.FILTER_TOGGLE_TAGS));
        modsButton = addRenderableWidget(ConfigPanel.actionButton(leftPos + 152, topPos + 18, 34,
                modsLabel(), MenuAction.FILTER_TOGGLE_MODS));
        addRenderableWidget(ConfigPanel.actionButton(leftPos + 190, topPos + 18, 26,
                Component.translatable("button.skylogistics.clear_short"), MenuAction.FILTER_CLEAR));
        attributeButtons.clear();
        Attribute[] attributes = Attribute.values();
        for (int i = 0; i < attributes.length; i++) {
            Attribute attribute = attributes[i];
            int column = i % 4;
            int row = i / 4;
            Button button = ConfigPanel.actionButton(leftPos + 8 + column * 54, topPos + 50 + row * 24, 50,
                    attributeLabel(attribute), MenuAction.filterAttribute(attribute.ordinal()));
            attributeButtons.add(addRenderableWidget(button));
        }
        refreshButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButtons();
    }

    private void refreshButtons() {
        if (modeButton != null) {
            modeButton.setMessage(modeLabel());
        }
        if (whitelistButton != null) {
            whitelistButton.setMessage(whitelistLabel());
        }
        if (tagsButton != null) {
            tagsButton.setMessage(tagsLabel());
        }
        if (modsButton != null) {
            modsButton.visible = menu.isListMode();
            modsButton.active = menu.isListMode();
            modsButton.setMessage(modsLabel());
        }
        boolean attributeMode = !menu.isListMode();
        Attribute[] attributes = Attribute.values();
        for (int i = 0; i < attributeButtons.size(); i++) {
            Button button = attributeButtons.get(i);
            Attribute attribute = attributes[i];
            button.visible = attributeMode;
            button.active = attributeMode;
            button.setMessage(attributeLabel(attribute));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ConfigPanel.BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, ConfigPanel.BORDER);
        graphics.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, ConfigPanel.BORDER);
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight, ConfigPanel.BORDER);
        graphics.fill(leftPos + imageWidth - 2, topPos, leftPos + imageWidth, topPos + imageHeight, ConfigPanel.BORDER);
        graphics.fill(leftPos + 7, topPos + 43, leftPos + imageWidth - 7, topPos + 112, 0x80101B22);
        if (menu.isListMode()) {
            for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
                int x = leftPos + filterSlotX(slot);
                int y = topPos + filterSlotY(slot);
                graphics.fill(x - 1, y - 1, x + 19, y + 19, 0xFF07101B);
                graphics.fill(x - 1, y - 1, x + 19, y, ConfigPanel.BORDER);
                graphics.fill(x - 1, y + 18, x + 19, y + 19, ConfigPanel.BORDER);
                graphics.fill(x - 1, y - 1, x, y + 19, ConfigPanel.BORDER);
                graphics.fill(x + 18, y - 1, x + 19, y + 19, ConfigPanel.BORDER);
                if (menu.isFluidFilter(slot)) {
                    graphics.fill(x + 12, y + 12, x + 17, y + 17, 0xFF3FCBFF);
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 8, ConfigPanel.ACCENT, false);
        graphics.drawString(font, Component.translatable(menu.isListMode()
                ? "screen.skylogistics.filter_list_mode_hint"
                : "screen.skylogistics.filter_attribute_mode_hint"), 100, 92, ConfigPanel.MUTED, false);
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
        super.renderTooltip(graphics, x, y);
    }

    public Rect2i getFilterSlotArea(int slot) {
        return new Rect2i(leftPos + filterSlotX(slot), topPos + filterSlotY(slot), FILTER_SLOT_SIZE, FILTER_SLOT_SIZE);
    }

    public boolean canAcceptGhostFilters() {
        return menu.isListMode();
    }

    private static int filterSlotX(int slot) {
        return FILTER_GRID_X + (slot % 3) * FILTER_SLOT_STEP;
    }

    private static int filterSlotY(int slot) {
        return FILTER_GRID_Y + (slot / 3) * FILTER_SLOT_STEP;
    }

    private int hoveredFilterSlot(int mouseX, int mouseY) {
        if (!menu.isListMode()) {
            return -1;
        }
        for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
            Rect2i area = getFilterSlotArea(slot);
            if (mouseX >= area.getX() && mouseX < area.getX() + area.getWidth()
                    && mouseY >= area.getY() && mouseY < area.getY() + area.getHeight()) {
                return slot;
            }
        }
        return -1;
    }

    private Component modeLabel() {
        return Component.translatable(menu.isListMode()
                ? "screen.skylogistics.filter_mode_list"
                : "screen.skylogistics.filter_mode_attribute");
    }

    private Component whitelistLabel() {
        return Component.translatable(menu.isWhitelist() ? "screen.skylogistics.filter_whitelist"
                : "screen.skylogistics.filter_blacklist");
    }

    private Component tagsLabel() {
        if (!menu.isListMode()) {
            return Component.translatable(menu.matchAllAttributes() ? "screen.skylogistics.filter_match_all"
                    : "screen.skylogistics.filter_match_any");
        }
        return Component.translatable(menu.matchTags() ? "screen.skylogistics.filter_tags"
                : "screen.skylogistics.filter_exact");
    }

    private Component modsLabel() {
        return Component.translatable(menu.matchMods() ? "screen.skylogistics.filter_mods"
                : "screen.skylogistics.filter_items");
    }

    private Component attributeLabel(Attribute attribute) {
        return Component.literal(menu.hasAttribute(attribute) ? "✓ " : "")
                .append(Component.translatable(attribute.translationKey()));
    }
}
