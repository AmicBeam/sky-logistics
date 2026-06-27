package com.skylogistics.compat.refinedstorage;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.api.network.node.INetworkNodeProxy;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.StackListEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

final class RefinedStorageApiBridge {
    private RefinedStorageApiBridge() {
    }

    static IItemHandler createItemHandler(BlockEntity host) {
        return new ItemHandler(host);
    }

    static IFluidHandler createFluidHandler(BlockEntity host) {
        return new FluidHandler(host);
    }

    private static INetwork network(BlockEntity host) {
        Level level = host.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }
        BlockPos pos = host.getBlockPos();
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = pos.relative(direction);
            if (!level.isLoaded(targetPos)) {
                continue;
            }
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target instanceof INetworkNodeProxy<?> proxy) {
                INetworkNode node = proxy.getNode();
                INetwork network = node == null ? null : node.getNetwork();
                if (network != null && node.isActive() && network.canRun()) {
                    return network;
                }
            }
        }
        return null;
    }

    private static Action action(boolean simulate) {
        return simulate ? Action.SIMULATE : Action.PERFORM;
    }

    private static Action action(IFluidHandler.FluidAction action) {
        return action.simulate() ? Action.SIMULATE : Action.PERFORM;
    }

    private static List<ItemStack> itemEntries(INetwork network) {
        if (network == null) {
            return List.of();
        }
        List<ItemStack> entries = new ArrayList<>();
        for (StackListEntry<ItemStack> entry : network.getItemStorageCache().getList().getStacks()) {
            ItemStack stack = entry.getStack();
            if (!stack.isEmpty() && stack.getCount() > 0) {
                entries.add(stack.copy());
            }
        }
        entries.sort(Comparator.comparing(RefinedStorageApiBridge::itemSortKey));
        return entries;
    }

    private static List<FluidStack> fluidEntries(INetwork network) {
        if (network == null) {
            return List.of();
        }
        List<FluidStack> entries = new ArrayList<>();
        for (StackListEntry<FluidStack> entry : network.getFluidStorageCache().getList().getStacks()) {
            FluidStack stack = entry.getStack();
            if (!stack.isEmpty() && stack.getAmount() > 0) {
                entries.add(stack.copy());
            }
        }
        entries.sort(Comparator.comparing(RefinedStorageApiBridge::fluidSortKey));
        return entries;
    }

    private static String itemSortKey(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return (id == null ? "unknown" : id.toString()) + "|" + stack.getTag();
    }

    private static String fluidSortKey(FluidStack stack) {
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
        return (id == null ? "unknown" : id.toString()) + "|" + stack.getTag();
    }

    private static ItemStack displayItem(ItemStack stack) {
        return ItemHandlerHelper.copyStackWithSize(stack, Math.min(stack.getCount(), stack.getMaxStackSize()));
    }

    private static FluidStack displayFluid(FluidStack stack) {
        FluidStack copy = stack.copy();
        copy.setAmount(Math.min(stack.getAmount(), Integer.MAX_VALUE));
        return copy;
    }

    private record ItemHandler(BlockEntity host) implements IItemHandler {
        @Override
        public int getSlots() {
            return itemEntries(network(host)).size() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            List<ItemStack> entries = itemEntries(network(host));
            return slot >= 0 && slot < entries.size() ? displayItem(entries.get(slot)) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            INetwork network = network(host);
            return network == null ? stack : network.insertItem(stack, stack.getCount(), action(simulate));
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            INetwork network = network(host);
            List<ItemStack> entries = itemEntries(network);
            if (network == null || slot < 0 || slot >= entries.size()) {
                return ItemStack.EMPTY;
            }
            ItemStack template = entries.get(slot);
            int requested = Math.min(amount, Math.min(template.getCount(), template.getMaxStackSize()));
            return network.extractItem(ItemHandlerHelper.copyStackWithSize(template, 1), requested, action(simulate));
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && insertItem(slot, stack, true).getCount() < stack.getCount();
        }
    }

    private record FluidHandler(BlockEntity host) implements IFluidHandler {
        @Override
        public int getTanks() {
            return fluidEntries(network(host)).size() + 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            List<FluidStack> entries = fluidEntries(network(host));
            return tank >= 0 && tank < entries.size() ? displayFluid(entries.get(tank)) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return !stack.isEmpty() && fill(stack, FluidAction.SIMULATE) > 0;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            INetwork network = network(host);
            if (network == null) {
                return 0;
            }
            FluidStack remainder = network.insertFluid(resource, resource.getAmount(), action(action));
            return resource.getAmount() - remainder.getAmount();
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            INetwork network = network(host);
            return network == null ? FluidStack.EMPTY
                    : network.extractFluid(resource.copy(), resource.getAmount(), action(action));
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            INetwork network = network(host);
            List<FluidStack> entries = fluidEntries(network);
            if (network == null || entries.isEmpty()) {
                return FluidStack.EMPTY;
            }
            FluidStack template = entries.get(0);
            int requested = Math.min(maxDrain, template.getAmount());
            FluidStack request = template.copy();
            request.setAmount(1);
            return network.extractFluid(request, requested, action(action));
        }
    }
}
