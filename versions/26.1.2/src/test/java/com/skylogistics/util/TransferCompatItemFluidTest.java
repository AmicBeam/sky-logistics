package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class TransferCompatItemFluidTest {
    @Test
    void itemResourceAdapterRollsBackAndCommits(MinecraftServer server) {
        MutableItemHandler inventory = new MutableItemHandler(64);
        ResourceHandler<ItemResource> handler = TransferCompat.itemResourceHandler(inventory);
        ItemResource stone = ItemResource.of(new ItemStack(Items.STONE));

        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(24, handler.insert(0, stone, 24, transaction));
        }
        assertTrue(inventory.stack.isEmpty());

        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(24, handler.insert(0, stone, 24, transaction));
            transaction.commit();
        }
        assertEquals(24, inventory.stack.getCount());
    }

    @Test
    void itemSchedulerViewKeepsSimulationSideEffectFree(MinecraftServer server) {
        MutableItemHandler inventory = new MutableItemHandler(64);
        ItemHandler schedulerView =
                TransferCompat.itemHandler(TransferCompat.itemResourceHandler(inventory));
        ItemStack stone = new ItemStack(Items.STONE, 24);

        assertTrue(schedulerView.insertItem(0, stone, true).isEmpty());
        assertTrue(inventory.stack.isEmpty());
        assertTrue(schedulerView.insertItem(0, stone, false).isEmpty());
        assertEquals(24, inventory.stack.getCount());

        assertEquals(10, schedulerView.extractItem(0, 10, true).getCount());
        assertEquals(24, inventory.stack.getCount());
        assertEquals(10, schedulerView.extractItem(0, 10, false).getCount());
        assertEquals(14, inventory.stack.getCount());
    }

    @Test
    void fluidResourceAdapterRollsBackAndCommits(MinecraftServer server) {
        MutableFluidHandler tank = new MutableFluidHandler(4_000);
        ResourceHandler<FluidResource> handler = TransferCompat.fluidResourceHandler(tank);
        FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 1));

        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(1_000, handler.insert(0, water, 1_000, transaction));
        }
        assertTrue(tank.stack.isEmpty());

        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(1_000, handler.insert(0, water, 1_000, transaction));
            transaction.commit();
        }
        assertEquals(1_000, tank.stack.getAmount());
    }

    @Test
    void fluidSchedulerViewKeepsSimulationSideEffectFree(MinecraftServer server) {
        MutableFluidHandler tank = new MutableFluidHandler(4_000);
        FluidHandler schedulerView =
                TransferCompat.fluidHandler(TransferCompat.fluidResourceHandler(tank));
        FluidStack water = new FluidStack(Fluids.WATER, 1_000);

        assertEquals(1_000, schedulerView.fill(water, FluidHandler.FluidAction.SIMULATE));
        assertTrue(tank.stack.isEmpty());
        assertEquals(1_000, schedulerView.fill(water, FluidHandler.FluidAction.EXECUTE));
        assertEquals(1_000, tank.stack.getAmount());

        assertEquals(250, schedulerView.drain(250, FluidHandler.FluidAction.SIMULATE).getAmount());
        assertEquals(1_000, tank.stack.getAmount());
        assertEquals(250, schedulerView.drain(250, FluidHandler.FluidAction.EXECUTE).getAmount());
        assertEquals(750, tank.stack.getAmount());
    }

    private static final class MutableItemHandler implements ItemHandler {
        private final int capacity;
        private ItemStack stack = ItemStack.EMPTY;

        private MutableItemHandler(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack incoming, boolean simulate) {
            if (incoming.isEmpty()
                    || (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, incoming))) {
                return incoming;
            }
            int inserted = Math.min(incoming.getCount(), capacity - stack.getCount());
            if (!simulate && inserted > 0) {
                stack = incoming.copyWithCount(stack.getCount() + inserted);
            }
            return inserted == incoming.getCount()
                    ? ItemStack.EMPTY
                    : incoming.copyWithCount(incoming.getCount() - inserted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (stack.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int extracted = Math.min(amount, stack.getCount());
            ItemStack result = stack.copyWithCount(extracted);
            if (!simulate) {
                stack = extracted == stack.getCount()
                        ? ItemStack.EMPTY
                        : stack.copyWithCount(stack.getCount() - extracted);
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return capacity;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack candidate) {
            return !candidate.isEmpty();
        }
    }

    private static final class MutableFluidHandler implements FluidHandler {
        private final int capacity;
        private FluidStack stack = FluidStack.EMPTY;

        private MutableFluidHandler(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return stack;
        }

        @Override
        public int getTankCapacity(int tank) {
            return capacity;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack candidate) {
            return !candidate.isEmpty();
        }

        @Override
        public int fill(FluidStack incoming, FluidAction action) {
            if (incoming.isEmpty()
                    || (!stack.isEmpty() && !FluidStack.isSameFluidSameComponents(stack, incoming))) {
                return 0;
            }
            int inserted = Math.min(incoming.getAmount(), capacity - stack.getAmount());
            if (action.execute() && inserted > 0) {
                stack = incoming.copyWithAmount(stack.getAmount() + inserted);
            }
            return inserted;
        }

        @Override
        public FluidStack drain(FluidStack requested, FluidAction action) {
            if (requested.isEmpty()
                    || stack.isEmpty()
                    || !FluidStack.isSameFluidSameComponents(stack, requested)) {
                return FluidStack.EMPTY;
            }
            return drain(requested.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (stack.isEmpty() || maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            int extracted = Math.min(maxDrain, stack.getAmount());
            FluidStack result = stack.copyWithAmount(extracted);
            if (action.execute()) {
                stack = extracted == stack.getAmount()
                        ? FluidStack.EMPTY
                        : stack.copyWithAmount(stack.getAmount() - extracted);
            }
            return result;
        }
    }
}
