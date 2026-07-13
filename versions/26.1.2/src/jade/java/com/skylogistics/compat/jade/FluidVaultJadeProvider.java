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
        implements IBlockComponentProvider {
    public static final FluidVaultJadeProvider INSTANCE = new FluidVaultJadeProvider();
    private static final String DATA = "SkyLogisticsFluidVault";

    private FluidVaultJadeProvider() {
        super("fluid_vault");
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData().getCompoundOrEmpty(DATA);
        if (data.isEmpty()) {
            if (accessor.getBlockEntity() instanceof FluidVaultBlockEntity vault) {
                data = writeVaultData(vault);
            } else {
                return;
            }
        }
        tooltip.add(Component.translatable("jade.skylogistics.fluid_vault", data.getIntOr("UsedTypes", 0),
                data.getIntOr("TypeLimit", 0), data.getStringOr("Total", "")));
    }

    private static CompoundTag writeVaultData(FluidVaultBlockEntity vault) {
        CompoundTag data = new CompoundTag();
        data.putInt("UsedTypes", vault.getUsedTypes());
        data.putInt("TypeLimit", vault.getTypeLimit());
        data.putString("Total", compact(vault.getTotalAmount()));
        return data;
    }

    public static final class DataProvider extends BaseSkyLogisticsJadeProvider
            implements IServerDataProvider<BlockAccessor> {
        public static final DataProvider INSTANCE = new DataProvider();

        private DataProvider() {
            super("fluid_vault");
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof FluidVaultBlockEntity vault) {
                data.put(DATA, writeVaultData(vault));
            }
        }
    }
}
