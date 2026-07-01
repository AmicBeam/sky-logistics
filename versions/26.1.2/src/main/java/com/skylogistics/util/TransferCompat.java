package com.skylogistics.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class TransferCompat {
    private TransferCompat() {
    }

    public static ResourceHandler<ItemResource> itemResourceHandler(IItemHandler handler) {
        return new LegacyItemResourceHandler(handler);
    }

    public static ResourceHandler<FluidResource> fluidResourceHandler(IFluidHandler handler) {
        return new LegacyFluidResourceHandler(handler);
    }

    public static EnergyHandler energyHandler(IEnergyStorage storage) {
        return new LegacyEnergyHandler(storage);
    }

    public static IItemHandler legacyItemHandler(ResourceHandler<ItemResource> handler) {
        return handler == null ? null : IItemHandler.of(handler);
    }

    public static IFluidHandler legacyFluidHandler(ResourceHandler<FluidResource> handler) {
        return handler == null ? null : IFluidHandler.of(handler);
    }

    public static IEnergyStorage legacyEnergyHandler(EnergyHandler handler) {
        return handler == null ? null : IEnergyStorage.of(handler);
    }

    private static final class LegacyItemResourceHandler extends SnapshotJournal<List<ItemStack>>
            implements ResourceHandler<ItemResource> {
        private final IItemHandler handler;

        private LegacyItemResourceHandler(IItemHandler handler) {
            this.handler = handler;
        }

        @Override
        public int size() {
            return handler.getSlots();
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.of(handler.getStackInSlot(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return handler.getStackInSlot(index).getCount();
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            if (resource.isEmpty()) {
                return handler.getSlotLimit(index);
            }
            return Math.min(handler.getSlotLimit(index), resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return !resource.isEmpty() && handler.isItemValid(index, resource.toStack());
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0 || !isValid(index, resource)) {
                return 0;
            }
            updateSnapshots(transaction);
            ItemStack remainder = handler.insertItem(index, resource.toStack(amount), false);
            return amount - remainder.getCount();
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            ItemStack stored = handler.getStackInSlot(index);
            if (stored.isEmpty() || !resource.matches(stored)) {
                return 0;
            }
            updateSnapshots(transaction);
            return handler.extractItem(index, amount, false).getCount();
        }

        @Override
        protected List<ItemStack> createSnapshot() {
            List<ItemStack> snapshot = new ArrayList<>(handler.getSlots());
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                snapshot.add(handler.getStackInSlot(slot).copy());
            }
            return snapshot;
        }

        @Override
        protected void revertToSnapshot(List<ItemStack> snapshot) {
            if (handler instanceof IItemHandlerModifiable modifiable) {
                for (int slot = 0; slot < snapshot.size(); slot++) {
                    modifiable.setStackInSlot(slot, snapshot.get(slot).copy());
                }
                return;
            }
            for (int slot = 0; slot < snapshot.size(); slot++) {
                while (!handler.getStackInSlot(slot).isEmpty()) {
                    ItemStack extracted = handler.extractItem(slot, Integer.MAX_VALUE, false);
                    if (extracted.isEmpty()) {
                        break;
                    }
                }
                ItemStack remaining = snapshot.get(slot).copy();
                while (!remaining.isEmpty()) {
                    ItemStack next = handler.insertItem(slot, remaining, false);
                    if (next.getCount() == remaining.getCount()) {
                        break;
                    }
                    remaining = next;
                }
            }
        }
    }

    private static final class LegacyFluidResourceHandler extends SnapshotJournal<List<FluidStack>>
            implements ResourceHandler<FluidResource> {
        private final IFluidHandler handler;

        private LegacyFluidResourceHandler(IFluidHandler handler) {
            this.handler = handler;
        }

        @Override
        public int size() {
            return handler.getTanks();
        }

        @Override
        public FluidResource getResource(int index) {
            return FluidResource.of(handler.getFluidInTank(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return handler.getFluidInTank(index).getAmount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return resource.isEmpty() || handler.isFluidValid(index, resource.toStack(1))
                    ? handler.getTankCapacity(index)
                    : 0;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return !resource.isEmpty() && handler.isFluidValid(index, resource.toStack(1));
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0 || !isValid(index, resource)) {
                return 0;
            }
            updateSnapshots(transaction);
            return handler.fill(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            FluidStack stored = handler.getFluidInTank(index);
            if (stored.isEmpty() || !resource.matches(stored)) {
                return 0;
            }
            updateSnapshots(transaction);
            return handler.drain(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE).getAmount();
        }

        @Override
        protected List<FluidStack> createSnapshot() {
            List<FluidStack> snapshot = new ArrayList<>(handler.getTanks());
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                snapshot.add(handler.getFluidInTank(tank).copy());
            }
            return snapshot;
        }

        @Override
        protected void revertToSnapshot(List<FluidStack> snapshot) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack current = handler.getFluidInTank(tank);
                if (!current.isEmpty()) {
                    handler.drain(current.copy(), IFluidHandler.FluidAction.EXECUTE);
                }
            }
            for (FluidStack stack : snapshot) {
                if (!stack.isEmpty()) {
                    handler.fill(stack.copy(), IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }
    }

    private static final class LegacyEnergyHandler extends SnapshotJournal<Integer> implements EnergyHandler {
        private final IEnergyStorage storage;

        private LegacyEnergyHandler(IEnergyStorage storage) {
            this.storage = storage;
        }

        @Override
        public long getAmountAsLong() {
            return storage.getEnergyStored();
        }

        @Override
        public long getCapacityAsLong() {
            return storage.getMaxEnergyStored();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            if (amount <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            return storage.receiveEnergy(amount, false);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            if (amount <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            return storage.extractEnergy(amount, false);
        }

        @Override
        protected Integer createSnapshot() {
            return storage.getEnergyStored();
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            int current = storage.getEnergyStored();
            if (current < snapshot) {
                storage.receiveEnergy(snapshot - current, false);
            } else if (current > snapshot) {
                storage.extractEnergy(current - snapshot, false);
            }
        }
    }
}
