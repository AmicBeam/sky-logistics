package com.skylogistics.compat.jade;

import com.skylogistics.block.entity.SkyDistributorBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class SkyDistributorJadeProvider extends BaseSkyLogisticsJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final SkyDistributorJadeProvider INSTANCE = new SkyDistributorJadeProvider();
    private static final String DATA = "SkyLogisticsDistributor";

    private SkyDistributorJadeProvider() { super("sky_distributor"); }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag distributorData = accessor.getServerData().getCompound(DATA);
        Component status = distributorData.getBoolean("Indexing")
                ? Component.translatable("jade.skylogistics.distributor_status_indexing")
                : Component.translatable("jade.skylogistics.distributor_status_bound",
                        distributorData.getInt("BoundDevices"));
        tooltip.add(Component.translatable("jade.skylogistics.status", status));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof SkyDistributorBlockEntity distributor)) return;
        SkyDistributorBlockEntity.IndexStatus status = distributor.indexStatus();
        CompoundTag distributorData = new CompoundTag();
        distributorData.putBoolean("Indexing", status.indexing());
        distributorData.putInt("BoundDevices", status.boundDevices());
        data.put(DATA, distributorData);
    }
}
