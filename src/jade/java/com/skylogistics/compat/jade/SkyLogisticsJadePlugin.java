package com.skylogistics.compat.jade;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class SkyLogisticsJadePlugin implements IWailaPlugin {
    static final ResourceLocation NODE = new ResourceLocation(com.skylogistics.SkyLogistics.MOD_ID, "node");
    static final ResourceLocation ITEM_VAULT = new ResourceLocation(com.skylogistics.SkyLogistics.MOD_ID, "item_vault");
    static final ResourceLocation FLUID_VAULT = new ResourceLocation(com.skylogistics.SkyLogistics.MOD_ID, "fluid_vault");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(SkyLogisticsJadeProvider.INSTANCE, BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(SkyLogisticsJadeProvider.INSTANCE, Block.class);
    }
}
