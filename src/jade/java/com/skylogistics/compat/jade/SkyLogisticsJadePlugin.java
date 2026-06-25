package com.skylogistics.compat.jade;

import com.skylogistics.block.FluidVaultBlock;
import com.skylogistics.block.ItemVaultBlock;
import com.skylogistics.block.OfferingAltarBlock;
import com.skylogistics.block.OfferingTableBlock;
import com.skylogistics.block.SkyNodeBlock;
import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.block.entity.OfferingAltarBlockEntity;
import com.skylogistics.block.entity.OfferingTableBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class SkyLogisticsJadePlugin implements IWailaPlugin {
    static final ResourceLocation NODE = new ResourceLocation(com.skylogistics.SkyLogistics.MOD_ID, "node");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(SkyLogisticsJadeProvider.INSTANCE, SkyNodeBlockEntity.class);
        registration.registerBlockDataProvider(SkyLogisticsJadeProvider.INSTANCE, ItemVaultBlockEntity.class);
        registration.registerBlockDataProvider(SkyLogisticsJadeProvider.INSTANCE, FluidVaultBlockEntity.class);
        registration.registerBlockDataProvider(SkyLogisticsJadeProvider.INSTANCE, OfferingAltarBlockEntity.class);
        registration.registerBlockDataProvider(SkyLogisticsJadeProvider.INSTANCE, OfferingTableBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(SkyLogisticsJadeProvider.INSTANCE, SkyNodeBlock.class);
        registration.registerBlockComponent(SkyLogisticsJadeProvider.INSTANCE, ItemVaultBlock.class);
        registration.registerBlockComponent(SkyLogisticsJadeProvider.INSTANCE, FluidVaultBlock.class);
        registration.registerBlockComponent(SkyLogisticsJadeProvider.INSTANCE, OfferingAltarBlock.class);
        registration.registerBlockComponent(SkyLogisticsJadeProvider.INSTANCE, OfferingTableBlock.class);
    }
}
