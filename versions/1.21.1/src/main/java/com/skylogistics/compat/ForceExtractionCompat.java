package com.skylogistics.compat;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.util.StackData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Opt-in atomic transfer for oversized slots exposed through a modifiable item capability.
 */
public final class ForceExtractionCompat {
    private ForceExtractionCompat() {
    }

    public static ItemStack fullSlotCandidate(SkyNodeBlockEntity node, BlockEntity blockEntity,
            IItemHandler handler, int slot, ItemStack simulated, int transferLimit) {
        if (!supports(node, blockEntity, handler) || simulated.isEmpty()) return simulated;
        ItemStack stored = handler.getStackInSlot(slot);
        if (stored.isEmpty() || !StackData.sameItemAndComponents(stored, simulated)
                || stored.getCount() > handler.getSlotLimit(slot)) {
            return simulated;
        }
        int count = Math.min(stored.getCount(), transferLimit);
        return count > simulated.getCount() ? stored.copyWithCount(count) : simulated;
    }

    public static DirectExtraction extractDirect(SkyNodeBlockEntity node, BlockEntity blockEntity,
            IItemHandler handler, int slot, ItemStack expected, int amount) {
        if (!supports(node, blockEntity, handler)) return DirectExtraction.UNSUPPORTED;
        IItemHandlerModifiable modifiable = (IItemHandlerModifiable) handler;
        ItemStack current = handler.getStackInSlot(slot);
        if (amount <= 0 || current.isEmpty() || amount > current.getCount()
                || !StackData.sameItemAndComponents(current, expected)) {
            return DirectExtraction.FAILED;
        }
        ItemStack probe = handler.extractItem(slot, 1, true);
        if (probe.isEmpty() || !StackData.sameItemAndComponents(probe, current)) {
            return DirectExtraction.FAILED;
        }
        ItemStack remainder = current.getCount() == amount
                ? ItemStack.EMPTY : current.copyWithCount(current.getCount() - amount);
        modifiable.setStackInSlot(slot, remainder);
        if (!sameStack(handler.getStackInSlot(slot), remainder)) {
            modifiable.setStackInSlot(slot, current);
            return DirectExtraction.FAILED;
        }
        return new DirectExtraction(current.copyWithCount(amount), true);
    }

    public static boolean restoreDirect(SkyNodeBlockEntity node, BlockEntity blockEntity,
            IItemHandler handler, int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (!supports(node, blockEntity, handler)) return false;
        IItemHandlerModifiable modifiable = (IItemHandlerModifiable) handler;
        ItemStack current = handler.getStackInSlot(slot);
        if (!current.isEmpty() && !StackData.sameItemAndComponents(current, stack)) return false;
        ItemStack restored = stack.copyWithCount(current.getCount() + stack.getCount());
        if (restored.getCount() > handler.getSlotLimit(slot)) return false;
        modifiable.setStackInSlot(slot, restored);
        return sameStack(handler.getStackInSlot(slot), restored);
    }

    public static InsertionCandidate insertionCandidate(SkyNodeBlockEntity node, BlockEntity blockEntity,
            IItemHandler handler, int slot, ItemStack stack, int standardMovable) {
        if (!supports(node, blockEntity, handler)) {
            return new InsertionCandidate(standardMovable, false);
        }
        return insertionCandidate(handler, slot, stack, standardMovable);
    }

    static InsertionCandidate insertionCandidate(IItemHandler handler, int slot, ItemStack stack,
            int standardMovable) {
        if (stack.isEmpty() || !handler.isItemValid(slot, stack)) {
            return new InsertionCandidate(standardMovable, false);
        }
        ItemStack current = handler.getStackInSlot(slot);
        if (!current.isEmpty() && !StackData.sameItemAndComponents(current, stack)) {
            return new InsertionCandidate(standardMovable, false);
        }
        ItemStack probe = stack.copyWithCount(1);
        if (handler.insertItem(slot, probe, true).getCount() == probe.getCount()) {
            return new InsertionCandidate(standardMovable, false);
        }
        int capacity = Math.max(0, handler.getSlotLimit(slot) - current.getCount());
        int movable = Math.min(stack.getCount(), capacity);
        return movable > standardMovable
                ? new InsertionCandidate(movable, true)
                : new InsertionCandidate(standardMovable, false);
    }

    public static DirectInsertion insertDirect(SkyNodeBlockEntity node, BlockEntity blockEntity,
            IItemHandler handler, int slot, ItemStack stack) {
        if (!supports(node, blockEntity, handler)) return DirectInsertion.UNSUPPORTED;
        return insertDirect(handler, slot, stack);
    }

    static DirectInsertion insertDirect(IItemHandler handler, int slot, ItemStack stack) {
        if (stack.isEmpty() || !handler.isItemValid(slot, stack)) return DirectInsertion.FAILED.apply(stack);
        ItemStack normalRemainder = handler.insertItem(slot, stack, true);
        if (normalRemainder.isEmpty()) return DirectInsertion.UNSUPPORTED;
        ItemStack current = handler.getStackInSlot(slot);
        if (!current.isEmpty() && !StackData.sameItemAndComponents(current, stack)) {
            return DirectInsertion.FAILED.apply(stack);
        }
        ItemStack probe = stack.copyWithCount(1);
        if (handler.insertItem(slot, probe, true).getCount() == probe.getCount()) {
            return DirectInsertion.FAILED.apply(stack);
        }
        long total = (long) current.getCount() + stack.getCount();
        if (total > handler.getSlotLimit(slot)) return DirectInsertion.FAILED.apply(stack);
        ItemStack updated = stack.copyWithCount((int) total);
        IItemHandlerModifiable modifiable = (IItemHandlerModifiable) handler;
        modifiable.setStackInSlot(slot, updated);
        if (!sameStack(handler.getStackInSlot(slot), updated)) {
            modifiable.setStackInSlot(slot, current);
            return DirectInsertion.FAILED.apply(stack);
        }
        return DirectInsertion.SUCCESS;
    }

    private static boolean supports(SkyNodeBlockEntity node, BlockEntity blockEntity, IItemHandler handler) {
        if (!node.hasForceExtractionUpgrade() || blockEntity == null
                || !(handler instanceof IItemHandlerModifiable)) {
            return false;
        }
        String modId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()).getNamespace();
        return SkyLogisticsConfig.forceExtractionDeviceModAllowed(modId);
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount()
                && (first.isEmpty() ? second.isEmpty() : StackData.sameItemAndComponents(first, second));
    }

    public record DirectExtraction(ItemStack stack, boolean supported) {
        private static final DirectExtraction UNSUPPORTED = new DirectExtraction(ItemStack.EMPTY, false);
        private static final DirectExtraction FAILED = new DirectExtraction(ItemStack.EMPTY, true);
    }

    public record InsertionCandidate(int movable, boolean forced) {
    }

    public record DirectInsertion(ItemStack remainder, boolean supported) {
        private static final DirectInsertion UNSUPPORTED = new DirectInsertion(ItemStack.EMPTY, false);
        private static final DirectInsertion SUCCESS = new DirectInsertion(ItemStack.EMPTY, true);
        private static final DirectInsertion FAILED = new DirectInsertion(ItemStack.EMPTY, true);

        private DirectInsertion apply(ItemStack stack) {
            return new DirectInsertion(stack.copy(), supported);
        }
    }
}
