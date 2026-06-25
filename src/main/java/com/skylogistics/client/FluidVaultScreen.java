package com.skylogistics.client;

import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.menu.FluidVaultMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

public class FluidVaultScreen extends AbstractContainerScreen<FluidVaultMenu> {
    private static final int GRID_X = 8;
    private static final int GRID_Y = 44;
    private static final int GRID_COLUMNS = 10;
    private static final int GRID_ROWS = 5;
    private static final int CELL_SIZE = 18;
    private static final int VISIBLE_CELLS = GRID_COLUMNS * GRID_ROWS;

    private EditBox searchBox;
    private Button nonEmptyButton;
    private Button sortButton;
    private int scrollRow;
    private boolean nonEmptyOnly = true;
    private boolean sortByAmount = true;
    private FluidVaultBlockEntity cachedVault;
    private List<FluidVaultBlockEntity.StoredFluid> filteredCache = List.of();
    private String filteredCacheQuery = "";
    private boolean filteredCacheNonEmptyOnly;
    private boolean filteredCacheSortByAmount;
    private long filteredCacheVersion = Long.MIN_VALUE;

    public FluidVaultScreen(FluidVaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 238;
        imageHeight = 250;
        inventoryLabelY = 156;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, leftPos + 8, topPos + 22, 114, 18,
                Component.translatable("screen.skylogistics.search"));
        searchBox.setHint(Component.translatable("screen.skylogistics.search"));
        addRenderableWidget(searchBox);
        nonEmptyButton = addRenderableWidget(Button.builder(nonEmptyLabel(), ignored -> {
                    nonEmptyOnly = !nonEmptyOnly;
                    scrollRow = 0;
                    invalidateFilteredCache();
                    refreshButtons();
                })
                .bounds(leftPos + 126, topPos + 22, 44, 18)
                .build());
        sortButton = addRenderableWidget(Button.builder(sortLabel(), ignored -> {
                    sortByAmount = !sortByAmount;
                    scrollRow = 0;
                    invalidateFilteredCache();
                    refreshButtons();
                })
                .bounds(leftPos + 174, topPos + 22, 56, 18)
                .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButtons();
    }

    private void refreshButtons() {
        if (nonEmptyButton != null) {
            nonEmptyButton.setMessage(nonEmptyLabel());
        }
        if (sortButton != null) {
            sortButton.setMessage(sortLabel());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderTerminalTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ConfigPanel.BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, ConfigPanel.BORDER);
        graphics.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight,
                ConfigPanel.BORDER);
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight, ConfigPanel.BORDER);
        graphics.fill(leftPos + imageWidth - 2, topPos, leftPos + imageWidth, topPos + imageHeight,
                ConfigPanel.BORDER);
        graphics.fill(leftPos + 7, topPos + 42, leftPos + imageWidth - 7, topPos + 137, 0x80101B22);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int column = 0; column < GRID_COLUMNS; column++) {
                drawSlot(graphics, leftPos + GRID_X + column * CELL_SIZE, topPos + GRID_Y + row * CELL_SIZE);
            }
        }
        drawScrollbar(graphics);
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
            int x = GRID_X + column * CELL_SIZE;
            int y = GRID_Y + row * CELL_SIZE;
            renderFluidCell(graphics, fluid.stack(), x, y);
            renderAmountLabel(graphics, ConfigPanel.amount(fluid.amount()), x - 1, y - 1);
        }
        graphics.drawString(font, Component.translatable("screen.skylogistics.types_used",
                vault.getUsedTypes(), vault.getTypeLimit()), 8, 144, ConfigPanel.TEXT, false);
        graphics.drawString(font, Component.translatable("screen.skylogistics.total_fluids",
                ConfigPanel.amount(vault.getTotalAmount())), 116, 144, ConfigPanel.TEXT, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isOverGrid(mouseX, mouseY)) {
            FluidVaultBlockEntity vault = vault();
            int size = vault == null ? 0 : filtered(vault).size();
            scrollRow = Math.max(0, Math.min(maxScrollRow(size), scrollRow - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
        int column = ((int) mouseX - leftPos - GRID_X) / CELL_SIZE;
        int row = ((int) mouseY - topPos - GRID_Y) / CELL_SIZE;
        int index = scrollRow * GRID_COLUMNS + row * GRID_COLUMNS + column;
        List<FluidVaultBlockEntity.StoredFluid> entries = filtered(vault);
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private boolean isOverGrid(double mouseX, double mouseY) {
        return mouseX >= leftPos + GRID_X && mouseX < leftPos + GRID_X + GRID_COLUMNS * CELL_SIZE
                && mouseY >= topPos + GRID_Y && mouseY < topPos + GRID_Y + GRID_ROWS * CELL_SIZE;
    }

    private List<FluidVaultBlockEntity.StoredFluid> filtered(FluidVaultBlockEntity vault) {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        long snapshotVersion = vault.getClientSnapshotVersion();
        if (vault == cachedVault && snapshotVersion == filteredCacheVersion && query.equals(filteredCacheQuery)
                && nonEmptyOnly == filteredCacheNonEmptyOnly && sortByAmount == filteredCacheSortByAmount) {
            return filteredCache;
        }
        List<FluidVaultBlockEntity.StoredFluid> result = new ArrayList<>();
        for (FluidVaultBlockEntity.StoredFluid fluid : vault.getStoredFluids(256)) {
            if (nonEmptyOnly && fluid.amount() <= 0) {
                continue;
            }
            if (!query.isEmpty()
                    && !fluid.stack().getDisplayName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            result.add(fluid);
        }
        if (sortByAmount) {
            result.sort(Comparator.comparingLong(FluidVaultBlockEntity.StoredFluid::amount).reversed()
                    .thenComparing(fluid -> fluid.stack().getDisplayName().getString(),
                            String.CASE_INSENSITIVE_ORDER));
        } else {
            result.sort(Comparator.comparing(fluid -> fluid.stack().getDisplayName().getString(),
                    String.CASE_INSENSITIVE_ORDER));
        }
        cachedVault = vault;
        filteredCache = result;
        filteredCacheQuery = query;
        filteredCacheNonEmptyOnly = nonEmptyOnly;
        filteredCacheSortByAmount = sortByAmount;
        filteredCacheVersion = snapshotVersion;
        return filteredCache;
    }

    private void invalidateFilteredCache() {
        filteredCacheVersion = Long.MIN_VALUE;
    }

    private int maxScrollRow(int size) {
        int rows = (size + GRID_COLUMNS - 1) / GRID_COLUMNS;
        return Math.max(0, rows - GRID_ROWS);
    }

    private Component nonEmptyLabel() {
        return Component.translatable("button.skylogistics.filter_nonempty");
    }

    private Component sortLabel() {
        return Component.translatable(sortByAmount
                ? "button.skylogistics.sort_amount"
                : "button.skylogistics.sort_name");
    }

    private void drawScrollbar(GuiGraphics graphics) {
        FluidVaultBlockEntity vault = vault();
        int max = vault == null ? 0 : maxScrollRow(filtered(vault).size());
        int x = leftPos + 222;
        int y = topPos + GRID_Y;
        int height = GRID_ROWS * CELL_SIZE;
        graphics.fill(x, y, x + 5, y + height, 0xFF07101B);
        graphics.fill(x, y, x + 5, y + 1, ConfigPanel.BORDER);
        graphics.fill(x, y + height - 1, x + 5, y + height, ConfigPanel.BORDER);
        if (max <= 0) {
            graphics.fill(x + 1, y + 1, x + 4, y + height - 1, 0xFF2D4D5A);
            return;
        }
        int thumbHeight = Math.max(14, height / (max + 1));
        int thumbY = y + 1 + (height - thumbHeight - 2) * scrollRow / max;
        graphics.fill(x + 1, thumbY, x + 4, thumbY + thumbHeight, ConfigPanel.BORDER);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 18, y + 18, 0xFF07101B);
        graphics.fill(x - 1, y - 1, x + 18, y, ConfigPanel.BORDER);
        graphics.fill(x - 1, y + 17, x + 18, y + 18, ConfigPanel.BORDER);
        graphics.fill(x - 1, y - 1, x, y + 18, ConfigPanel.BORDER);
        graphics.fill(x + 17, y - 1, x + 18, y + 18, ConfigPanel.BORDER);
    }

    private void renderFluidCell(GuiGraphics graphics, FluidStack stack, int x, int y) {
        int tint = IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack);
        int color = 0xFF000000 | (tint & 0x00FFFFFF);
        graphics.fill(x + 2, y + 2, x + 16, y + 16, color);
        graphics.fill(x + 3, y + 3, x + 15, y + 7, 0x55FFFFFF);
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
}
