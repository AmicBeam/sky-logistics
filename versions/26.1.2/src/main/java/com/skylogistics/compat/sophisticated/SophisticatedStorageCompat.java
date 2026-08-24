package com.skylogistics.compat.sophisticated;

import com.skylogistics.SkyLogistics;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.util.StackData;
import java.lang.reflect.Method;
import java.util.Collection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

public final class SophisticatedStorageCompat {
    private static final String MOD_ID = "sophisticatedstorage";
    private static final String PACKAGE_PREFIX = "net.p3pp3rf1y.sophisticatedstorage.";
    private static boolean initialized;
    private static boolean directAccessAvailable;
    private static boolean warned;
    private static Class<?> storageBlockEntityClass;
    private static Method getStorageWrapper;
    private static Method getInventoryHandler;
    private static Method getUpgradeHandler;
    private static Method getExtractResponseUpgrades;
    private static Method getSlots;
    private static Method getStackInSlot;
    private static Method setStackInSlot;
    private static Method getStackLimit;
    private static Method isSlotAccessible;
    private static Method isInfinite;
    private static Method getInventoryPartitioner;
    private static Method getPartBySlot;
    private static Method getPartName;
    private static Class<?> extractResponseUpgradeClass;

    private SophisticatedStorageCompat() {
    }

    public static boolean supports(BlockEntity blockEntity) {
        return blockEntity != null
                && SkyLogisticsConfig.allowSophisticatedStorageStackUpgradeTransfer()
                && ModList.get().isLoaded(MOD_ID)
                && blockEntity.getClass().getName().startsWith(PACKAGE_PREFIX);
    }

    public static ItemStack fullSlotCandidate(BlockEntity blockEntity, int slot, ItemStack simulated,
            int transferLimit) {
        DirectInventory direct = directInventory(blockEntity, slot);
        if (direct == null || simulated.isEmpty()) return simulated;
        try {
            ItemStack stored = (ItemStack) getStackInSlot.invoke(direct.inventory(), slot);
            if (stored.isEmpty() || !StackData.sameItemAndComponents(stored, simulated)
                    || stored.getCount() > (int) getStackLimit.invoke(direct.inventory(), slot, stored)) {
                return simulated;
            }
            int count = Math.min(stored.getCount(), transferLimit);
            return count > simulated.getCount() ? stored.copyWithCount(count) : simulated;
        } catch (ReflectiveOperationException | RuntimeException error) {
            warnOnce(error);
            return simulated;
        }
    }

    public static DirectExtraction extractDirect(BlockEntity blockEntity, int slot, ItemStack expected, int amount) {
        DirectInventory direct = directInventory(blockEntity, slot);
        if (direct == null) return DirectExtraction.UNSUPPORTED;
        try {
            ItemStack current = (ItemStack) getStackInSlot.invoke(direct.inventory(), slot);
            if (amount <= 0 || current.isEmpty() || amount > current.getCount()
                    || !StackData.sameItemAndComponents(current, expected)) {
                return DirectExtraction.FAILED;
            }
            ItemStack remainder = current.getCount() == amount
                    ? ItemStack.EMPTY : current.copyWithCount(current.getCount() - amount);
            setStackInSlot.invoke(direct.inventory(), slot, remainder);
            ItemStack after = (ItemStack) getStackInSlot.invoke(direct.inventory(), slot);
            if (!sameStack(after, remainder)) {
                setStackInSlot.invoke(direct.inventory(), slot, current);
                return DirectExtraction.FAILED;
            }
            return new DirectExtraction(current.copyWithCount(amount), true);
        } catch (ReflectiveOperationException | RuntimeException error) {
            warnOnce(error);
            return DirectExtraction.FAILED;
        }
    }

    public static boolean restoreDirect(BlockEntity blockEntity, int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        DirectInventory direct = directInventory(blockEntity, slot);
        if (direct == null) return false;
        try {
            ItemStack current = (ItemStack) getStackInSlot.invoke(direct.inventory(), slot);
            if (!current.isEmpty() && !StackData.sameItemAndComponents(current, stack)) return false;
            int restoredCount = current.getCount() + stack.getCount();
            ItemStack restored = stack.copyWithCount(restoredCount);
            if (restoredCount > (int) getStackLimit.invoke(direct.inventory(), slot, restored)) return false;
            setStackInSlot.invoke(direct.inventory(), slot, restored);
            return sameStack((ItemStack) getStackInSlot.invoke(direct.inventory(), slot), restored);
        } catch (ReflectiveOperationException | RuntimeException error) {
            warnOnce(error);
            return false;
        }
    }

    private static DirectInventory directInventory(BlockEntity blockEntity, int slot) {
        if (!supports(blockEntity) || !initDirectAccess() || !storageBlockEntityClass.isInstance(blockEntity)) {
            return null;
        }
        try {
            Object wrapper = getStorageWrapper.invoke(blockEntity);
            Object upgradeHandler = getUpgradeHandler.invoke(wrapper);
            Object responseUpgrades = getExtractResponseUpgrades.invoke(upgradeHandler, extractResponseUpgradeClass);
            if (!(responseUpgrades instanceof Collection<?> collection) || !collection.isEmpty()) return null;
            Object inventory = getInventoryHandler.invoke(wrapper);
            if (slot < 0 || slot >= (int) getSlots.invoke(inventory)
                    || !(boolean) isSlotAccessible.invoke(inventory, slot)
                    || (boolean) isInfinite.invoke(inventory, slot)) {
                return null;
            }
            Object partitioner = getInventoryPartitioner.invoke(inventory);
            Object part = getPartBySlot.invoke(partitioner, slot);
            return "default".equals(getPartName.invoke(part)) ? new DirectInventory(inventory) : null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            warnOnce(error);
            return null;
        }
    }

    private static boolean initDirectAccess() {
        if (initialized) return directAccessAvailable;
        initialized = true;
        if (!ModList.get().isLoaded(MOD_ID)) return false;
        try {
            storageBlockEntityClass = Class.forName(
                    "net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity");
            Class<?> storageWrapperClass = Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper");
            Class<?> inventoryHandlerClass = Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler");
            Class<?> partitionerClass = Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.inventory.InventoryPartitioner");
            Class<?> partClass = Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.inventory.IInventoryPartHandler");
            Class<?> upgradeHandlerClass = Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler");
            extractResponseUpgradeClass = Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.upgrades.IExtractResponseUpgrade");
            getStorageWrapper = storageBlockEntityClass.getMethod("getStorageWrapper");
            getInventoryHandler = storageWrapperClass.getMethod("getInventoryHandler");
            getUpgradeHandler = storageWrapperClass.getMethod("getUpgradeHandler");
            getExtractResponseUpgrades = upgradeHandlerClass.getMethod(
                    "getWrappersThatImplementFromMainStorage", Class.class);
            getSlots = inventoryHandlerClass.getMethod("getSlots");
            getStackInSlot = inventoryHandlerClass.getMethod("getStackInSlot", int.class);
            setStackInSlot = inventoryHandlerClass.getMethod("setStackInSlot", int.class, ItemStack.class);
            getStackLimit = inventoryHandlerClass.getMethod("getStackLimit", int.class, ItemStack.class);
            isSlotAccessible = inventoryHandlerClass.getMethod("isSlotAccessible", int.class);
            isInfinite = inventoryHandlerClass.getMethod("isInfinite", int.class);
            getInventoryPartitioner = inventoryHandlerClass.getMethod("getInventoryPartitioner");
            getPartBySlot = partitionerClass.getMethod("getPartBySlot", int.class);
            getPartName = partClass.getMethod("getName");
            directAccessAvailable = true;
        } catch (ReflectiveOperationException | LinkageError error) {
            warnOnce(error);
        }
        return directAccessAvailable;
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount()
                && (first.isEmpty() ? second.isEmpty() : StackData.sameItemAndComponents(first, second));
    }

    private static void warnOnce(Throwable error) {
        if (warned) return;
        warned = true;
        SkyLogistics.LOGGER.warn("Sophisticated Storage atomic slot transfer disabled; API lookup failed.", error);
    }

    public record DirectExtraction(ItemStack stack, boolean supported) {
        private static final DirectExtraction UNSUPPORTED = new DirectExtraction(ItemStack.EMPTY, false);
        private static final DirectExtraction FAILED = new DirectExtraction(ItemStack.EMPTY, true);
    }

    private record DirectInventory(Object inventory) {
    }
}
