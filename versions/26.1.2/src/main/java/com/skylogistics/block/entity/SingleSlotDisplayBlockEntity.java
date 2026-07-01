package com.skylogistics.block.entity;

import com.skylogistics.util.StackData;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public abstract class SingleSlotDisplayBlockEntity extends BlockEntity {
    private static final String DATA_TAG = "SkyLogisticsSingleSlot";
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            markSlotChanged();
        }
    };

    protected SingleSlotDisplayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IItemHandler itemHandler() {
        return items;
    }

    public ItemStack getDisplayedItem() {
        return items.getStackInSlot(0);
    }

    public void setDisplayedItem(ItemStack stack) {
        ItemStack copy = stack.copy();
        if (!copy.isEmpty()) {
            copy.setCount(Math.min(copy.getCount(), Math.min(64, copy.getMaxStackSize())));
        }
        items.setStackInSlot(0, copy);
    }

    public void shrinkDisplayedItem(int count) {
        ItemStack stack = items.getStackInSlot(0);
        if (stack.isEmpty() || count <= 0) {
            return;
        }
        stack.shrink(count);
        if (stack.isEmpty()) {
            items.setStackInSlot(0, ItemStack.EMPTY);
        } else {
            items.setStackInSlot(0, stack);
        }
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
        ItemStack stored = items.extractItem(0, 64, false);
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
        return items.extractItem(0, 64, false);
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

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        CompoundTag tag = input.read(DATA_TAG, CompoundTag.CODEC).orElse(new CompoundTag());
        loadDisplayData(tag, input.lookup());
    }

    private void loadDisplayData(CompoundTag tag, HolderLookup.Provider registries) {
        items.setStackInSlot(0, StackData.loadItem(tag.getCompoundOrEmpty("Item"), registries));
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
}
