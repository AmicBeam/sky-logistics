package com.skylogistics.client;

import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.menu.FluidVaultMenu;
import com.skylogistics.network.ModNetworking;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.core.registries.BuiltInRegistries;

public class FluidVaultScreen extends AbstractContainerScreen<FluidVaultMenu> {
    private static final int GRID_X = 8;
    private static final int CENTERED_GRID_X = 17;
    private static final int GRID_Y = 44;
    private static final int GRID_COLUMNS = 9;
    private static final int GRID_ROWS = 4;
    private static final int CELL_SIZE = 18;
    private static final int FLUID_ICON_SIZE = 14;
    private static final int VISIBLE_CELLS = GRID_COLUMNS * GRID_ROWS;
    private static final int GRID_BOTTOM = GRID_Y + GRID_ROWS * CELL_SIZE;
    private static final int STATS_Y = GRID_BOTTOM + 10;
    private static final VaultTerminalViewState.State VIEW_STATE = VaultTerminalViewState.fluidVault();

    private EditBox searchBox;
    private AbstractButton sortButton;
    private int scrollRow;
    private SortMode sortMode = SortMode.fromOrdinal(VIEW_STATE.sortModeOrdinal());
    private FluidVaultBlockEntity cachedVault;
    private List<FluidVaultBlockEntity.StoredFluid> filteredCache = List.of();
    private String filteredCacheQuery = "";
    private SortMode filteredCacheSortMode;
    private long filteredCacheVersion = Long.MIN_VALUE;

    public FluidVaultScreen(FluidVaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 196;
        imageHeight = 222;
        inventoryLabelY = 128;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, leftPos + 8, topPos + 22, 114, 18,
                Component.translatable("screen.skylogistics.search"));
        searchBox.setHint(Component.translatable("screen.skylogistics.search"));
        ConfigPanel.styleEditBox(searchBox);
        addRenderableWidget(searchBox);
        sortButton = addRenderableWidget(ConfigPanel.button(leftPos + 128, topPos + 22, 60, 18, sortLabel(), () -> {
            setSortMode(sortMode.next());
            scrollRow = 0;
            invalidateFilteredCache();
            refreshButtons();
        }));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButtons();
    }

    private void refreshButtons() {
        if (sortButton != null) {
            sortButton.setMessage(sortLabel());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderTerminalTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ConfigPanel.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        if (searchBox != null) {
            ConfigPanel.drawInputBox(graphics, leftPos + 8, topPos + 22, 114, 18, searchBox.isFocused());
        }
        ConfigPanel.drawContentPanel(graphics, leftPos + 7, topPos + 42, imageWidth - 14, GRID_BOTTOM - GRID_Y + 5);
        ConfigPanel.drawInventoryPanel(graphics, leftPos + 13, topPos + 134, 170, 84);
        FluidVaultBlockEntity vault = vault();
        int gridX = gridX(vault);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int column = 0; column < GRID_COLUMNS; column++) {
                int visibleIndex = row * GRID_COLUMNS + column;
                drawVaultSlotBackground(graphics, vault, visibleIndex, leftPos + gridX + column * CELL_SIZE,
                        topPos + GRID_Y + row * CELL_SIZE);
            }
        }
        drawScrollbar(graphics);
        renderMenuSlotBackgrounds(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        FluidVaultBlockEntity vault = vault();
        graphics.drawString(font, title, 8, 8, ConfigPanel.ACCENT, false);
        if (vault == null) {
            graphics.drawString(font, Component.translatable("screen.skylogistics.missing_vault"), 8, 48,
                    ConfigPanel.MUTED, false);
            return;
        }
        List<FluidVaultBlockEntity.StoredFluid> entries = filtered(vault);
        scrollRow = Math.min(scrollRow, maxScrollRow(entries.size()));
        int start = scrollRow * GRID_COLUMNS;
        for (int visible = 0; visible < VISIBLE_CELLS && start + visible < entries.size(); visible++) {
            FluidVaultBlockEntity.StoredFluid fluid = entries.get(start + visible);
            int column = visible % GRID_COLUMNS;
            int row = visible / GRID_COLUMNS;
            int x = gridX(vault) + column * CELL_SIZE;
            int y = GRID_Y + row * CELL_SIZE;
            renderFluidCell(graphics, fluid.stack(), x, y);
            renderAmountLabel(graphics, ConfigPanel.amount(fluid.amount()), x, y);
        }
        graphics.drawString(font, Component.translatable("screen.skylogistics.types_used",
                vault.getUsedTypes(), vault.getTypeLimit()), 8, STATS_Y, ConfigPanel.TEXT, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.total_fluids",
                ConfigPanel.amount(vault.getTotalAmount())), 116, STATS_Y, ConfigPanel.TEXT, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isOverGrid(mouseX, mouseY)) {
            FluidVaultBlockEntity vault = vault();
            int size = vault == null ? 0 : filtered(vault).size();
            if (vault != null && shouldShowScrollbar(vault)) {
                scrollRow = Math.max(0, Math.min(maxScrollRow(size), scrollRow - (int) Math.signum(scrollY)));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == 0 || button == 1) && isOverGrid(mouseX, mouseY)) {
            FluidVaultBlockEntity.StoredFluid hovered = hoveredEntry(mouseX, mouseY);
            if (!menu.getCarried().isEmpty()) {
                ModNetworking.sendFluidVaultTerminalClick(hovered == null ? FluidStack.EMPTY : hovered.stack(),
                        button, hasShiftDown());
                return true;
            }
            return hovered != null;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderTerminalTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        FluidVaultBlockEntity.StoredFluid hovered = hoveredEntry(mouseX, mouseY);
        if (hovered == null) {
            return;
        }
        graphics.renderComponentTooltip(font, List.of(hovered.stack().getDisplayName(),
                Component.translatable("screen.skylogistics.total_fluids", fullAmount(hovered.amount()))),
                mouseX, mouseY);
    }

    private FluidVaultBlockEntity.StoredFluid hoveredEntry(double mouseX, double mouseY) {
        if (!isOverGrid(mouseX, mouseY)) {
            return null;
        }
        FluidVaultBlockEntity vault = vault();
        if (vault == null) {
            return null;
        }
        int gridX = gridX(vault);
        int column = ((int) mouseX - leftPos - gridX) / CELL_SIZE;
        int row = ((int) mouseY - topPos - GRID_Y) / CELL_SIZE;
        int index = scrollRow * GRID_COLUMNS + row * GRID_COLUMNS + column;
        List<FluidVaultBlockEntity.StoredFluid> entries = filtered(vault);
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private boolean isOverGrid(double mouseX, double mouseY) {
        int gridX = gridX();
        return mouseX >= leftPos + gridX && mouseX < leftPos + gridX + GRID_COLUMNS * CELL_SIZE
                && mouseY >= topPos + GRID_Y && mouseY < topPos + GRID_Y + GRID_ROWS * CELL_SIZE;
    }

    private List<FluidVaultBlockEntity.StoredFluid> filtered(FluidVaultBlockEntity vault) {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        long snapshotVersion = vault.getClientSnapshotVersion();
        if (vault == cachedVault && snapshotVersion == filteredCacheVersion && query.equals(filteredCacheQuery)
                && sortMode == filteredCacheSortMode) {
            return filteredCache;
        }
        List<FluidVaultBlockEntity.StoredFluid> result = new ArrayList<>();
        for (FluidVaultBlockEntity.StoredFluid fluid : vault.getStoredFluids(256)) {
            if (!query.isEmpty()
                    && !fluid.stack().getDisplayName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            result.add(fluid);
        }
        sort(result);
        cachedVault = vault;
        filteredCache = result;
        filteredCacheQuery = query;
        filteredCacheSortMode = sortMode;
        filteredCacheVersion = snapshotVersion;
        return filteredCache;
    }

    private void invalidateFilteredCache() {
        filteredCacheVersion = Long.MIN_VALUE;
    }

    private void setSortMode(SortMode sortMode) {
        this.sortMode = sortMode;
        VIEW_STATE.setSortModeOrdinal(sortMode.ordinal());
    }

    private int maxScrollRow(int size) {
        int rows = (size + GRID_COLUMNS - 1) / GRID_COLUMNS;
        return Math.max(0, rows - GRID_ROWS);
    }

    private Component sortLabel() {
        return Component.translatable(sortMode.translationKey);
    }

    private void drawScrollbar(GuiGraphics graphics) {
        FluidVaultBlockEntity vault = vault();
        if (vault == null || !shouldShowScrollbar(vault)) {
            return;
        }
        int max = maxScrollRow(filtered(vault).size());
        int x = leftPos + gridX(vault) + GRID_COLUMNS * CELL_SIZE + 5;
        int y = topPos + GRID_Y;
        int height = GRID_ROWS * CELL_SIZE;
        if (max <= 0) {
            ConfigPanel.drawScrollbar(graphics, x, y, height, y + 2, height - 4, false);
            return;
        }
        int thumbHeight = Math.max(14, height / (max + 1));
        int thumbY = y + 2 + (height - thumbHeight - 4) * scrollRow / max;
        ConfigPanel.drawScrollbar(graphics, x, y, height, thumbY, thumbHeight, true);
    }

    private void sort(List<FluidVaultBlockEntity.StoredFluid> result) {
        switch (sortMode) {
            case AMOUNT -> result.sort(Comparator.comparingLong(FluidVaultBlockEntity.StoredFluid::amount).reversed()
                    .thenComparing(FluidVaultScreen::fluidName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(FluidVaultScreen::fluidModId, String.CASE_INSENSITIVE_ORDER));
            case NAME -> result.sort(Comparator.comparing(FluidVaultScreen::fluidName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(FluidVaultScreen::fluidModId, String.CASE_INSENSITIVE_ORDER));
            case MOD -> result.sort(Comparator.comparing(FluidVaultScreen::fluidModId, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(FluidVaultScreen::fluidName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Comparator.comparingLong(FluidVaultBlockEntity.StoredFluid::amount).reversed()));
        }
    }

    private boolean shouldShowScrollbar(FluidVaultBlockEntity vault) {
        return vault.getTypeLimit() > VISIBLE_CELLS;
    }

    private int gridX() {
        return gridX(vault());
    }

    private int gridX(FluidVaultBlockEntity vault) {
        return vault != null && shouldShowScrollbar(vault) ? GRID_X : CENTERED_GRID_X;
    }

    private static String fluidName(FluidVaultBlockEntity.StoredFluid fluid) {
        return fluid.stack().getDisplayName().getString();
    }

    private static String fluidModId(FluidVaultBlockEntity.StoredFluid fluid) {
        var id = BuiltInRegistries.FLUID.getKey(fluid.stack().getFluid());
        return id == null ? "" : id.getNamespace();
    }

    private void drawVaultSlotBackground(GuiGraphics graphics, FluidVaultBlockEntity vault, int visibleIndex,
            int x, int y) {
        int slotIndex = scrollRow * GRID_COLUMNS + visibleIndex;
        if (vault != null && slotIndex >= vault.getTypeLimit()) {
            ConfigPanel.drawLockedSlotBackground(graphics, x, y);
        } else {
            ConfigPanel.drawSlotBackground(graphics, x, y);
        }
    }

    private void renderMenuSlotBackgrounds(GuiGraphics graphics) {
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConfigPanel.drawSlotBackground(graphics, leftPos + slot.x, topPos + slot.y);
            }
        }
    }

    private void renderFluidCell(GuiGraphics graphics, FluidStack stack, int x, int y) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(stack.getFluid());
        int tint = extensions.getTintColor(stack);
        ResourceLocation texture = extensions.getStillTexture(stack);
        int iconX = x + 2;
        int iconY = y + 2;
        if (texture == null) {
            graphics.fill(iconX, iconY, iconX + FLUID_ICON_SIZE, iconY + FLUID_ICON_SIZE,
                    0xFF000000 | (tint & 0x00FFFFFF));
        } else {
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(texture);
            int alpha = tint >>> 24;
            if (alpha == 0) {
                alpha = 0xFF;
            }
            graphics.blit(iconX, iconY, 0, FLUID_ICON_SIZE, FLUID_ICON_SIZE, sprite,
                    ((tint >> 16) & 0xFF) / 255.0F,
                    ((tint >> 8) & 0xFF) / 255.0F,
                    (tint & 0xFF) / 255.0F,
                    alpha / 255.0F);
        }
        graphics.fill(iconX + 1, iconY + 1, iconX + FLUID_ICON_SIZE - 1, iconY + 5, 0x33FFFFFF);
    }

    private void renderAmountLabel(GuiGraphics graphics, String text, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.pose().scale(0.5F, 0.5F, 1);
        int labelX = (int) ((x + 17 - font.width(text) * 0.5F) * 2);
        int labelY = (y + 12) * 2;
        graphics.drawString(font, text, labelX, labelY, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }

    private static String fullAmount(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }

    private FluidVaultBlockEntity vault() {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(menu.getPos());
        return blockEntity instanceof FluidVaultBlockEntity vault ? vault : null;
    }

    private enum SortMode {
        AMOUNT("button.skylogistics.sort_amount"),
        NAME("button.skylogistics.sort_name"),
        MOD("button.skylogistics.sort_mod");

        private final String translationKey;

        SortMode(String translationKey) {
            this.translationKey = translationKey;
        }

        private SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private static SortMode fromOrdinal(int ordinal) {
            SortMode[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : AMOUNT;
        }
    }
}
