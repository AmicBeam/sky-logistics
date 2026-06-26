package com.skylogistics.compat.jade;

import com.skylogistics.block.FluidVaultBlock;
import com.skylogistics.block.ItemVaultBlock;
import com.skylogistics.block.OfferingAltarBlock;
import com.skylogistics.block.OfferingTableBlock;
import com.skylogistics.block.SkyNodeBlock;
import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.block.entity.SingleSlotDisplayBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class SkyLogisticsJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(SkyNodeJadeProvider.INSTANCE, SkyNodeBlockEntity.class);
        registration.registerBlockDataProvider(ItemVaultJadeProvider.INSTANCE, ItemVaultBlockEntity.class);
        registration.registerBlockDataProvider(FluidVaultJadeProvider.INSTANCE, FluidVaultBlockEntity.class);
        registration.registerBlockDataProvider(DisplaySlotJadeProvider.INSTANCE, SingleSlotDisplayBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(SkyNodeJadeProvider.INSTANCE, SkyNodeBlock.class);
        registration.registerBlockComponent(ItemVaultJadeProvider.INSTANCE, ItemVaultBlock.class);
        registration.registerBlockComponent(FluidVaultJadeProvider.INSTANCE, FluidVaultBlock.class);
        registration.registerBlockComponent(DisplaySlotJadeProvider.INSTANCE, OfferingAltarBlock.class);
        registration.registerBlockComponent(DisplaySlotJadeProvider.INSTANCE, OfferingTableBlock.class);
    }
}
