package com.skylogistics.compat;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.config.SkyLogisticsConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Opt-in atomic extraction for oversized slots exposed through a modifiable item capability.
 */
public final class ForceExtractionCompat {
    private ForceExtractionCompat() {
    }

    public static ItemStack fullSlotCandidate(SkyNodeBlockEntity node, BlockEntity blockEntity,
            IItemHandler handler, int slot, ItemStack simulated, int transferLimit) {
        if (!supports(node, blockEntity, handler) || simulated.isEmpty()) return simulated;
        ItemStack stored = handler.getStackInSlot(slot);
        if (stored.isEmpty() || !ItemStack.isSameItemSameTags(stored, simulated)
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
                || !ItemStack.isSameItemSameTags(current, expected)) {
            return DirectExtraction.FAILED;
        }
        ItemStack probe = handler.extractItem(slot, 1, true);
        if (probe.isEmpty() || !ItemStack.isSameItemSameTags(probe, current)) {
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
        if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, stack)) return false;
        ItemStack restored = stack.copyWithCount(current.getCount() + stack.getCount());
        if (restored.getCount() > handler.getSlotLimit(slot)) return false;
        modifiable.setStackInSlot(slot, restored);
        return sameStack(handler.getStackInSlot(slot), restored);
    }

    private static boolean supports(SkyNodeBlockEntity node, BlockEntity blockEntity, IItemHandler handler) {
        if (!SkyLogisticsConfig.enableForceExtractionUpgrade() || !node.hasForceExtractionUpgrade()
                || blockEntity == null || !(handler instanceof IItemHandlerModifiable)) {
            return false;
        }
        String modId = ForgeRegistries.BLOCKS.getKey(blockEntity.getBlockState().getBlock()).getNamespace();
        return SkyLogisticsConfig.forceExtractionDeviceModAllowed(modId);
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount()
                && (first.isEmpty() ? second.isEmpty() : ItemStack.isSameItemSameTags(first, second));
    }

    public record DirectExtraction(ItemStack stack, boolean supported) {
        private static final DirectExtraction UNSUPPORTED = new DirectExtraction(ItemStack.EMPTY, false);
        private static final DirectExtraction FAILED = new DirectExtraction(ItemStack.EMPTY, true);
    }
}
