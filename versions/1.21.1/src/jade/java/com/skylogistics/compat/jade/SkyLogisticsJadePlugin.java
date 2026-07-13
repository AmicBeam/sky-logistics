package com.skylogistics.compat.jade;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.ExternalNetworkInterfaceBlock;
import com.skylogistics.block.FluidVaultBlock;
import com.skylogistics.block.ItemVaultBlock;
import com.skylogistics.block.SkyNodeBlock;
import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(SkyLogistics.MOD_ID)
public class SkyLogisticsJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(SkyNodeJadeProvider.INSTANCE, SkyNodeBlockEntity.class);
        registration.registerBlockDataProvider(ItemVaultJadeProvider.INSTANCE, ItemVaultBlockEntity.class);
        registration.registerBlockDataProvider(FluidVaultJadeProvider.INSTANCE, FluidVaultBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(SkyNodeJadeProvider.INSTANCE, SkyNodeBlock.class);
        registration.registerBlockComponent(SkyNodeJadeProvider.INSTANCE, ExternalNetworkInterfaceBlock.class);
        registration.registerBlockComponent(ItemVaultJadeProvider.INSTANCE, ItemVaultBlock.class);
        registration.registerBlockComponent(FluidVaultJadeProvider.INSTANCE, FluidVaultBlock.class);
    }
}
