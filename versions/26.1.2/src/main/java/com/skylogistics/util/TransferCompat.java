package com.skylogistics.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class TransferCompat {
    private TransferCompat() {
    }

    public static ResourceHandler<ItemResource> itemResourceHandler(ItemHandler handler) {
        return new ItemResourceHandlerAdapter(handler);
    }

    public static ResourceHandler<FluidResource> fluidResourceHandler(FluidHandler handler) {
        return new FluidResourceHandlerAdapter(handler);
    }

    public static EnergyHandler energyHandler(EnergyStorage storage) {
        return new EnergyHandlerAdapter(storage);
    }

    public static ItemHandler itemHandler(ResourceHandler<ItemResource> handler) {
        return handler == null ? null : new ResourceItemHandler(handler);
    }

    public static FluidHandler fluidHandler(ResourceHandler<FluidResource> handler) {
        return handler == null ? null : new ResourceFluidHandler(handler);
    }

    public static EnergyStorage energyStorage(EnergyHandler handler) {
        return handler == null ? null : new TransactionalEnergyStorage(handler);
    }

    public static ItemStack insertItemStacked(ItemHandler handler, ItemStack stack, boolean simulate) {
        if (handler == null || stack.isEmpty()) {
            return stack;
        }
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            ItemStack existing = handler.getStackInSlot(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)) {
                remaining = handler.insertItem(slot, remaining, simulate);
            }
        }
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            if (handler.getStackInSlot(slot).isEmpty()) {
                remaining = handler.insertItem(slot, remaining, simulate);
            }
        }
        return remaining;
    }

    private static final class ItemResourceHandlerAdapter extends SnapshotJournal<List<ItemStack>>
            implements ResourceHandler<ItemResource> {
        private final ItemHandler handler;

        private ItemResourceHandlerAdapter(ItemHandler handler) {
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

    private static final class FluidResourceHandlerAdapter extends SnapshotJournal<List<FluidStack>>
            implements ResourceHandler<FluidResource> {
        private final FluidHandler handler;

        private FluidResourceHandlerAdapter(FluidHandler handler) {
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
            return handler.fill(resource.toStack(amount), FluidHandler.FluidAction.EXECUTE);
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
            return handler.drain(resource.toStack(amount), FluidHandler.FluidAction.EXECUTE).getAmount();
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
                    handler.drain(current.copy(), FluidHandler.FluidAction.EXECUTE);
                }
            }
            for (FluidStack stack : snapshot) {
                if (!stack.isEmpty()) {
                    handler.fill(stack.copy(), FluidHandler.FluidAction.EXECUTE);
                }
            }
        }
    }

    private static final class EnergyHandlerAdapter extends SnapshotJournal<Integer> implements EnergyHandler {
        private final EnergyStorage storage;

        private EnergyHandlerAdapter(EnergyStorage storage) {
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

    private record ResourceItemHandler(ResourceHandler<ItemResource> handler) implements ItemHandler {
        @Override
        public int getSlots() {
            return handler.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemResource resource = handler.getResource(slot);
            return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(handler.getAmountAsInt(slot));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = handler.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
                if (!simulate) {
                    transaction.commit();
                }
                return inserted >= stack.getCount()
                        ? ItemStack.EMPTY
                        : stack.copyWithCount(stack.getCount() - inserted);
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty()) {
                return ItemStack.EMPTY;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int extracted = handler.extract(slot, resource, amount, transaction);
                if (!simulate) {
                    transaction.commit();
                }
                return resource.toStack(extracted);
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return handler.getCapacityAsInt(slot, ItemResource.EMPTY);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && handler.isValid(slot, ItemResource.of(stack));
        }
    }

    private record ResourceFluidHandler(ResourceHandler<FluidResource> handler) implements FluidHandler {
        @Override
        public int getTanks() {
            return handler.size();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            FluidResource resource = handler.getResource(tank);
            return resource.isEmpty() ? FluidStack.EMPTY : resource.toStack(handler.getAmountAsInt(tank));
        }

        @Override
        public int getTankCapacity(int tank) {
            return handler.getCapacityAsInt(tank, FluidResource.EMPTY);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return !stack.isEmpty() && handler.isValid(tank, FluidResource.of(stack));
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = handler.insert(FluidResource.of(resource), resource.getAmount(), transaction);
                if (action.execute()) {
                    transaction.commit();
                }
                return inserted;
            }
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            FluidResource fluid = FluidResource.of(resource);
            try (Transaction transaction = Transaction.openRoot()) {
                int extracted = handler.extract(fluid, resource.getAmount(), transaction);
                if (action.execute()) {
                    transaction.commit();
                }
                return fluid.toStack(extracted);
            }
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            for (int tank = 0; tank < handler.size(); tank++) {
                FluidResource resource = handler.getResource(tank);
                if (resource.isEmpty()) {
                    continue;
                }
                try (Transaction transaction = Transaction.openRoot()) {
                    int extracted = handler.extract(tank, resource, maxDrain, transaction);
                    if (action.execute()) {
                        transaction.commit();
                    }
                    return resource.toStack(extracted);
                }
            }
            return FluidStack.EMPTY;
        }
    }

    private record TransactionalEnergyStorage(EnergyHandler handler) implements EnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = handler.insert(maxReceive, transaction);
                if (!simulate) {
                    transaction.commit();
                }
                return inserted;
            }
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int extracted = handler.extract(maxExtract, transaction);
                if (!simulate) {
                    transaction.commit();
                }
                return extracted;
            }
        }

        @Override
        public int getEnergyStored() {
            return handler.getAmountAsInt();
        }

        @Override
        public int getMaxEnergyStored() {
            return handler.getCapacityAsInt();
        }

        @Override
        public boolean canExtract() {
            return handler.getAmountAsLong() > 0;
        }

        @Override
        public boolean canReceive() {
            return handler.getAmountAsLong() < handler.getCapacityAsLong();
        }
    }
}
