package com.skylogistics.compat.jade;

import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.block.entity.SingleSlotDisplayBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.util.NodeFaceMode;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum SkyLogisticsJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data.contains("SkyNode")) {
            tooltip.append(Component.translatable("jade.skylogistics.line_name", data.getString("Line")));
            tooltip.append(Component.translatable("jade.skylogistics.faces", data.getInt("Faces")));
            tooltip.append(Component.translatable("jade.skylogistics.types",
                    onOff(data.getBoolean("Items")), onOff(data.getBoolean("Fluids")),
                    onOff(data.getBoolean("Energy"))));
            if (data.getBoolean("FilterUpgrade")) {
                tooltip.append(Component.translatable("jade.skylogistics.upgrade_filter"));
            }
            if (data.getBoolean("SpeedUpgrade")) {
                tooltip.append(Component.translatable("jade.skylogistics.upgrade_speed"));
            }
            if (data.getBoolean("DimensionUpgrade")) {
                tooltip.append(Component.translatable("jade.skylogistics.upgrade_dimension"));
            }
            for (Direction direction : Direction.values()) {
                String key = "Face" + direction.get3DDataValue();
                if (data.contains(key)) {
                    tooltip.append(Component.translatable("jade.skylogistics.face", direction.getName(),
                            Component.translatable(NodeFaceMode.byName(data.getString(key)).translationKey())));
                }
            }
        } else if (data.contains("SkyItemVault")) {
            tooltip.append(Component.translatable("jade.skylogistics.item_vault", data.getInt("UsedTypes"),
                    data.getInt("TypeLimit"), data.getString("Total")));
        } else if (data.contains("SkyFluidVault")) {
            tooltip.append(Component.translatable("jade.skylogistics.fluid_vault", data.getInt("UsedTypes"),
                    data.getInt("TypeLimit"), data.getString("Total")));
        } else if (data.contains("SkyDisplaySlot")) {
            appendDisplayedItem(tooltip, ItemStack.of(data.getCompound("Stack")));
        } else {
            appendClientFallback(tooltip, accessor.getBlockEntity());
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof SkyNodeBlockEntity node) {
            data.putBoolean("SkyNode", true);
            data.putString("Line", ConfiguratorItem.shortLine(node.getLineId()));
            data.putInt("Faces", node.getConnectedFaces());
            data.putBoolean("Items", node.isItemsEnabled());
            data.putBoolean("Fluids", node.isFluidsEnabled());
            data.putBoolean("Energy", node.isEnergyEnabled());
            data.putBoolean("FilterUpgrade", node.hasFilterList());
            data.putBoolean("SpeedUpgrade", node.hasSpeedUpgrade());
            data.putBoolean("DimensionUpgrade", node.hasDimensionUpgrade());
            for (Direction direction : Direction.values()) {
                NodeFaceMode faceMode = node.getFaceMode(direction);
                if (faceMode != NodeFaceMode.NONE) {
                    data.putString("Face" + direction.get3DDataValue(), faceMode.name());
                }
            }
        } else if (accessor.getBlockEntity() instanceof ItemVaultBlockEntity vault) {
            data.putBoolean("SkyItemVault", true);
            data.putInt("UsedTypes", vault.getUsedTypes());
            data.putInt("TypeLimit", vault.getTypeLimit());
            data.putString("Total", compact(vault.getTotalAmount()));
        } else if (accessor.getBlockEntity() instanceof FluidVaultBlockEntity vault) {
            data.putBoolean("SkyFluidVault", true);
            data.putInt("UsedTypes", vault.getUsedTypes());
            data.putInt("TypeLimit", vault.getTypeLimit());
            data.putString("Total", compact(vault.getTotalAmount()));
        } else if (accessor.getBlockEntity() instanceof SingleSlotDisplayBlockEntity display) {
            data.putBoolean("SkyDisplaySlot", true);
            data.put("Stack", display.getDisplayedItem().save(new CompoundTag()));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return SkyLogisticsJadePlugin.NODE;
    }

    private static Component onOff(boolean enabled) {
        return Component.literal(enabled ? "ON" : "OFF");
    }

    private static void appendClientFallback(ITooltip tooltip, BlockEntity blockEntity) {
        if (blockEntity instanceof SkyNodeBlockEntity node) {
            tooltip.append(Component.translatable("jade.skylogistics.line_name", ConfiguratorItem.shortLine(node.getLineId())));
            tooltip.append(Component.translatable("jade.skylogistics.faces", node.getConnectedFaces()));
        } else if (blockEntity instanceof ItemVaultBlockEntity vault) {
            tooltip.append(Component.translatable("jade.skylogistics.item_vault", vault.getUsedTypes(),
                    vault.getTypeLimit(), compact(vault.getTotalAmount())));
        } else if (blockEntity instanceof FluidVaultBlockEntity vault) {
            tooltip.append(Component.translatable("jade.skylogistics.fluid_vault", vault.getUsedTypes(),
                    vault.getTypeLimit(), compact(vault.getTotalAmount())));
        } else if (blockEntity instanceof SingleSlotDisplayBlockEntity display) {
            appendDisplayedItem(tooltip, display.getDisplayedItem());
        }
    }

    private static void appendDisplayedItem(ITooltip tooltip, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        tooltip.append(Component.translatable("jade.skylogistics.display_item", stack.getHoverName(), stack.getCount()));
    }

    private static String compact(long value) {
        if (value >= 1_000_000_000L) {
            return (value / 1_000_000_000L) + "B";
        }
        if (value >= 1_000_000L) {
            return (value / 1_000_000L) + "M";
        }
        if (value >= 1_000L) {
            return (value / 1_000L) + "K";
        }
        return Long.toString(value);
    }
}
