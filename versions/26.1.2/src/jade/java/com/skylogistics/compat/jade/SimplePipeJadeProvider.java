package com.skylogistics.compat.jade;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.block.entity.SimplePipeBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class SimplePipeJadeProvider extends BaseSkyLogisticsJadeProvider implements IBlockComponentProvider {
    public static final SimplePipeJadeProvider INSTANCE = new SimplePipeJadeProvider();
    private static final String DATA = "SkyLogisticsPipeFilters";
    private SimplePipeJadeProvider() { super("simple_pipe"); }
    @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        Direction direction = SimplePipeBlock.targetedContainerEndpoint(accessor.getBlockState(), accessor.getLevel(),
                accessor.getPosition(), accessor.getHitResult());
        JadeFilterTooltip.append(tooltip, accessor.getServerData().getCompoundOrEmpty(DATA), direction,
                accessor.getPlayer(), accessor.getLevel().registryAccess());
    }
    public static final class DataProvider extends BaseSkyLogisticsJadeProvider
            implements IServerDataProvider<BlockAccessor> {
        public static final DataProvider INSTANCE = new DataProvider();
        private DataProvider() { super("simple_pipe"); }
        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof SimplePipeBlockEntity pipe)
                data.put(DATA, JadeFilterTooltip.write(pipe, accessor.getLevel().registryAccess()));
        }
    }
}
