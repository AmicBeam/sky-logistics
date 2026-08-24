package com.skylogistics.block.entity;

import com.skylogistics.util.StackData;
import com.skylogistics.util.ItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public abstract class SingleSlotDisplayBlockEntity extends BlockEntity {
    private static final String DATA_TAG = "SkyLogisticsSingleSlot";
    private ItemStack displayedItem = ItemStack.EMPTY;
    private final ItemHandler items = new ItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? displayedItem : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || isDisplaySlotLocked()) {
                return stack;
            }
            if (!canStoreDisplayedItem(stack)) {
                return insertRejectedDisplayedItem(stack, simulate);
            }
            if (!displayedItem.isEmpty() && !ItemStack.isSameItemSameComponents(displayedItem, stack)) {
                return stack;
            }
            int limit = Math.min(64, stack.getMaxStackSize());
            int space = limit - displayedItem.getCount();
            int inserted = Math.min(space, stack.getCount());
            if (inserted <= 0) {
                return stack;
            }
            if (!simulate) {
                displayedItem = displayedItem.isEmpty()
                        ? stack.copyWithCount(inserted)
                        : displayedItem.copyWithCount(displayedItem.getCount() + inserted);
                markSlotChanged();
            }
            return inserted == stack.getCount()
                    ? ItemStack.EMPTY
                    : stack.copyWithCount(stack.getCount() - inserted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || displayedItem.isEmpty() || isDisplaySlotLocked()) {
                return ItemStack.EMPTY;
            }
            int extracted = Math.min(amount, displayedItem.getCount());
            ItemStack result = displayedItem.copyWithCount(extracted);
            if (!simulate) {
                displayedItem = displayedItem.getCount() == extracted
                        ? ItemStack.EMPTY
                        : displayedItem.copyWithCount(displayedItem.getCount() - extracted);
                markSlotChanged();
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? 64 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && !stack.isEmpty() && !isDisplaySlotLocked();
        }
    };

    protected SingleSlotDisplayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ItemHandler itemHandler() {
        return items;
    }

    public ItemStack getDisplayedItem() {
        return items.getStackInSlot(0);
    }

    public ItemStack insertDisplayedItem(ItemStack stack, boolean simulate) {
        return items.insertItem(0, stack, simulate);
    }

    public void setDisplayedItem(ItemStack stack) {
        ItemStack copy = stack.copy();
        if (!copy.isEmpty()) {
            copy.setCount(Math.min(copy.getCount(), Math.min(64, copy.getMaxStackSize())));
        }
        displayedItem = copy;
        markSlotChanged();
    }

    public void shrinkDisplayedItem(int count) {
        ItemStack stack = items.getStackInSlot(0);
        if (stack.isEmpty() || count <= 0) {
            return;
        }
        stack.shrink(count);
        if (stack.isEmpty()) {
            displayedItem = ItemStack.EMPTY;
        } else {
            displayedItem = stack;
        }
        markSlotChanged();
    }

    public boolean insertFromPlayer(Player player, ItemStack held) {
        if (held.isEmpty()) {
            return false;
        }
        ItemStack toInsert = held.copy();
        ItemStack remainder = items.insertItem(0, toInsert, false);
        int inserted = toInsert.getCount() - remainder.getCount();
        if (inserted <= 0) {
            return false;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(inserted);
        }
        return true;
    }

    public boolean extractToPlayer(Player player, InteractionHand hand) {
        ItemStack stored = isDisplaySlotLocked() && canPlayerExtractDisplayWhileLocked(player)
                ? extractDisplayedItemInternal(64)
                : items.extractItem(0, 64, false);
        if (stored.isEmpty()) {
            return false;
        }
        if (player.getItemInHand(hand).isEmpty()) {
            player.setItemInHand(hand, stored);
            return true;
        }
        player.getInventory().add(stored);
        if (!stored.isEmpty()) {
            player.drop(stored, false);
        }
        return true;
    }

    public ItemStack removeDisplayedItem() {
        return extractDisplayedItemInternal(64);
    }

    private ItemStack extractDisplayedItemInternal(int amount) {
        ItemStack stack = items.getStackInSlot(0);
        if (stack.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int extracted = Math.min(amount, stack.getCount());
        ItemStack result = stack.split(extracted);
        displayedItem = stack.isEmpty() ? ItemStack.EMPTY : stack;
        markSlotChanged();
        return result;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide()) {
            ItemStack stored = removeDisplayedItem();
            if (!stored.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stored);
            }
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        saveDisplayData(tag, StackData.builtinRegistries());
        output.store(DATA_TAG, CompoundTag.CODEC, tag);
    }

    private void saveDisplayData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Item", StackData.saveItem(items.getStackInSlot(0), registries));
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        CompoundTag tag = input.read(DATA_TAG, CompoundTag.CODEC).orElse(new CompoundTag());
        loadDisplayData(tag, input.lookup());
    }

    private void loadDisplayData(CompoundTag tag, HolderLookup.Provider registries) {
        displayedItem = StackData.loadItem(tag.getCompoundOrEmpty("Item"), registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        CompoundTag data = new CompoundTag();
        saveDisplayData(data, registries);
        tag.put(DATA_TAG, data);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    protected void markSlotChanged() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        onStoredItemChanged();
    }

    protected void onStoredItemChanged() {
    }

    protected boolean isDisplaySlotLocked() {
        return false;
    }

    protected boolean canStoreDisplayedItem(ItemStack stack) {
        return true;
    }

    protected ItemStack insertRejectedDisplayedItem(ItemStack stack, boolean simulate) {
        return stack;
    }

    protected boolean canPlayerExtractDisplayWhileLocked(Player player) {
        return false;
    }
}
