package com.skylogistics.menu;

import com.skylogistics.item.FilterListItem;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModMenus;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public class FilterListMenu extends AbstractContainerMenu {
    private static final int FILTER_GRID_X = 31;
    private static final int FILTER_GRID_Y = 25;
    private static final int FILTER_COLUMNS = 9;
    private static final int FILTER_SLOT_STEP = 18;
    private final InteractionHand hand;
    private final FilterGhostContainer filters;

    public FilterListMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(ModMenus.FILTER_LIST.get(), containerId);
        this.hand = hand;
        this.filters = new FilterGhostContainer(inventory.player, hand);
        for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
            int column = slot % FILTER_COLUMNS;
            int row = slot / FILTER_COLUMNS;
            addSlot(new Slot(filters, slot, FILTER_GRID_X + column * FILTER_SLOT_STEP,
                    FILTER_GRID_Y + row * FILTER_SLOT_STEP) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }
        addPlayerInventory(inventory, 31, 107);
    }

    public InteractionHand getHand() {
        return hand;
    }

    public boolean isWhitelist() {
        return FilterListItem.isWhitelist(filterStack());
    }

    public boolean matchNbt() {
        return FilterListItem.matchNbt(filterStack());
    }

    public boolean matchDurability() {
        return FilterListItem.matchDurability(filterStack());
    }

    public FluidStack getFluidFilter(int slot) {
        return FilterListItem.getFluidFilter(filterStack(), slot);
    }

    public boolean isFluidFilter(int slot) {
        return !getFluidFilter(slot).isEmpty();
    }

    public void setGhostItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < FilterListItem.FILTER_SLOTS) {
            filters.setGhost(slot, stack);
            broadcastChanges();
        }
    }

    public void setGhostFluid(int slot, FluidStack stack) {
        if (slot >= 0 && slot < FilterListItem.FILTER_SLOTS) {
            filters.setFluidGhost(slot, stack);
            broadcastChanges();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).is(ModItems.FILTER_LIST.get());
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < FilterListItem.FILTER_SLOTS) {
            ItemStack carried = getCarried();
            setGhostItem(slotId, carried);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= FilterListItem.FILTER_SLOTS && index < slots.size()) {
            ItemStack stack = slots.get(index).getItem();
            if (!stack.isEmpty()) {
                filters.addFirstEmpty(stack);
                broadcastChanges();
            }
        }
        return ItemStack.EMPTY;
    }

    public void applyAction(Player player, int action) {
        ItemStack stack = filterStack();
        if (!stack.is(ModItems.FILTER_LIST.get())) {
            return;
        }
        switch (action) {
            case MenuAction.FILTER_SET_WHITELIST -> FilterListItem.setWhitelist(stack, true);
            case MenuAction.FILTER_SET_BLACKLIST -> FilterListItem.setWhitelist(stack, false);
            case MenuAction.FILTER_SET_NBT_ON -> FilterListItem.setMatchNbt(stack, true);
            case MenuAction.FILTER_SET_NBT_OFF -> FilterListItem.setMatchNbt(stack, false);
            case MenuAction.FILTER_SET_DURABILITY_ON -> FilterListItem.setMatchDurability(stack, true);
            case MenuAction.FILTER_SET_DURABILITY_OFF -> FilterListItem.setMatchDurability(stack, false);
            case MenuAction.FILTER_CLEAR -> FilterListItem.clearFilters(stack);
            default -> {
                return;
            }
        }
        filters.setChanged();
        broadcastChanges();
    }

    private ItemStack filterStack() {
        return filters.stack();
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, x + column * 18, y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, x + column * 18, y + 58));
        }
    }

    private static final class FilterGhostContainer extends SimpleContainer {
        private final Player player;
        private final InteractionHand hand;

        private FilterGhostContainer(Player player, InteractionHand hand) {
            super(FilterListItem.FILTER_SLOTS);
            this.player = player;
            this.hand = hand;
        }

        private ItemStack stack() {
            return player.getItemInHand(hand);
        }

        @Override
        public ItemStack getItem(int slot) {
            return FilterListItem.getDisplayFilter(stack(), slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            setGhost(slot, stack);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack current = getItem(slot);
            FilterListItem.setFilter(stack(), slot, ItemStack.EMPTY);
            FilterListItem.setFluidFilter(stack(), slot, FluidStack.EMPTY);
            return current;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        private void setGhost(int slot, ItemStack stack) {
            ItemStack ghost = stack.copy();
            if (!ghost.isEmpty()) {
                ghost.setCount(1);
            }
            FilterListItem.setFilter(stack(), slot, ghost);
            if (ghost.isEmpty()) {
                FilterListItem.setFluidFilter(stack(), slot, FluidStack.EMPTY);
            }
            setChanged();
        }

        private void setFluidGhost(int slot, FluidStack stack) {
            FluidStack ghost = stack.copy();
            if (!ghost.isEmpty()) {
                ghost.setAmount(1);
            }
            FilterListItem.setFluidFilter(stack(), slot, ghost);
            if (ghost.isEmpty()) {
                FilterListItem.setFilter(stack(), slot, ItemStack.EMPTY);
            }
            setChanged();
        }

        private void addFirstEmpty(ItemStack stack) {
            for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
                if (getItem(slot).isEmpty()) {
                    setGhost(slot, stack);
                    return;
                }
            }
            setGhost(0, stack);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
