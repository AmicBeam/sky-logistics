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

public final class SimplePipeJadeProvider extends BaseSkyLogisticsJadeProvider implements IBlockComponentProvider {
    public static final SimplePipeJadeProvider INSTANCE = new SimplePipeJadeProvider();
    private static final String DATA = "SkyLogisticsPipe";
    private SimplePipeJadeProvider() { super("simple_pipe"); }
    @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        Direction direction = SimplePipeBlock.targetedContainerEndpoint(accessor.getBlockState(), accessor.getLevel(),
                accessor.getPosition(), accessor.getHitResult());
        if (direction == null) return;
        CompoundTag pipeData = accessor.getServerData().getCompoundOrEmpty(DATA);
        boolean active = (pipeData.getIntOr("ActiveFaces", 0) & (1 << direction.ordinal())) != 0;
        tooltip.add(Component.translatable("jade.skylogistics.status", Component.translatable(active
                ? "jade.skylogistics.status_active" : "jade.skylogistics.status_idle")));
        JadeTransferRateTooltip.append(tooltip, pipeData.getCompoundOrEmpty("ProgressionRates"));
        JadeFilterTooltip.append(tooltip, pipeData, direction,
                accessor.getPlayer(), accessor.getLevel().registryAccess());
    }
    public static final class DataProvider extends BaseSkyLogisticsJadeProvider
            implements IServerDataProvider<BlockAccessor> {
        public static final DataProvider INSTANCE = new DataProvider();
        private DataProvider() { super("simple_pipe"); }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof SimplePipeBlockEntity pipe)
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
        data.put("ProgressionRates", JadeTransferRateTooltip.write(pipe, pipe.pipeType()));
        return data;
    }
}
