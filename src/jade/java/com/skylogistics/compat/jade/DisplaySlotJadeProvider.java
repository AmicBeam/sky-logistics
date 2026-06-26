package com.skylogistics.compat.jade;

import com.skylogistics.block.entity.SingleSlotDisplayBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class DisplaySlotJadeProvider extends BaseSkyLogisticsJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final DisplaySlotJadeProvider INSTANCE = new DisplaySlotJadeProvider();
    private static final String DATA = "SkyLogisticsDisplaySlot";

    private DisplaySlotJadeProvider() {
        super("display_slot");
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData().getCompound(DATA);
        if (data.isEmpty()) {
            if (accessor.getBlockEntity() instanceof SingleSlotDisplayBlockEntity display) {
                data = writeDisplayData(display);
            } else {
                return;
            }
        }
        appendDisplayedItem(tooltip, ItemStack.of(data.getCompound("Stack")));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof SingleSlotDisplayBlockEntity display) {
            data.put(DATA, writeDisplayData(display));
        }
    }

    private static CompoundTag writeDisplayData(SingleSlotDisplayBlockEntity display) {
        CompoundTag data = new CompoundTag();
        data.put("Stack", display.getDisplayedItem().save(new CompoundTag()));
        return data;
    }
}
