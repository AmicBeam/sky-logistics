package com.skylogistics.compat.jade;

import com.skylogistics.block.entity.FluidVaultBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class FluidVaultJadeProvider extends BaseSkyLogisticsJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final FluidVaultJadeProvider INSTANCE = new FluidVaultJadeProvider();
    private static final String DATA = "SkyLogisticsFluidVault";

    private FluidVaultJadeProvider() {
        super("fluid_vault");
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData().getCompound(DATA);
        if (data.isEmpty()) {
            if (accessor.getBlockEntity() instanceof FluidVaultBlockEntity vault) {
                data = writeVaultData(vault);
            } else {
                return;
            }
        }
        tooltip.add(Component.translatable("jade.skylogistics.fluid_vault", data.getInt("UsedTypes"),
                data.getInt("TypeLimit"), data.getString("Total")));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof FluidVaultBlockEntity vault) {
            data.put(DATA, writeVaultData(vault));
        }
    }

    private static CompoundTag writeVaultData(FluidVaultBlockEntity vault) {
        CompoundTag data = new CompoundTag();
        data.putInt("UsedTypes", vault.getUsedTypes());
        data.putInt("TypeLimit", vault.getTypeLimit());
        data.putString("Total", compact(vault.getTotalAmount()));
        return data;
    }
}
