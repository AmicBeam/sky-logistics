package com.skylogistics.compat.jade;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.block.entity.SimplePipeBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class SimplePipeJadeProvider extends BaseSkyLogisticsJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final SimplePipeJadeProvider INSTANCE = new SimplePipeJadeProvider();
    private static final String DATA = "SkyLogisticsPipe";

    private SimplePipeJadeProvider() { super("simple_pipe"); }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        Direction direction = SimplePipeBlock.targetedContainerEndpoint(accessor.getBlockState(), accessor.getLevel(),
                accessor.getPosition(), accessor.getHitResult());
        if (direction == null) return;
        CompoundTag pipeData = accessor.getServerData().getCompound(DATA);
        boolean active = (pipeData.getInt("ActiveFaces") & (1 << direction.ordinal())) != 0;
        tooltip.add(Component.translatable("jade.skylogistics.status", Component.translatable(active
                ? "jade.skylogistics.status_active" : "jade.skylogistics.status_idle")));
        JadeTransferRateTooltip.append(tooltip,
                pipeData.getCompound("ProgressionRates").getCompound(direction.getSerializedName()));
        JadeFilterTooltip.append(tooltip, pipeData, direction,
                accessor.getPlayer(), accessor.getLevel().registryAccess());
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof SimplePipeBlockEntity pipe) {
            data.put(DATA, writePipeData(pipe, accessor.getLevel().registryAccess()));
        }
    }

    private static CompoundTag writePipeData(SimplePipeBlockEntity pipe,
            net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag data = JadeFilterTooltip.write(pipe, registries);
        int activeFaces = 0;
        for (Direction direction : Direction.values()) {
            if (pipe.hasRecentTransfer(direction)) activeFaces |= 1 << direction.ordinal();
        }
        data.putInt("ActiveFaces", activeFaces);
        CompoundTag rates = new CompoundTag();
        for (Direction direction : Direction.values()) {
            CompoundTag rate = JadeTransferRateTooltip.write(pipe, direction);
            if (!rate.isEmpty()) rates.put(direction.getSerializedName(), rate);
        }
        data.put("ProgressionRates", rates);
        return data;
    }
}
