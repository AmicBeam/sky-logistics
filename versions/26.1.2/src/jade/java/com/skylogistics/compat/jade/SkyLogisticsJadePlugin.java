package com.skylogistics.compat.jade;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.ExternalNetworkInterfaceBlock;
import com.skylogistics.block.FluidVaultBlock;
import com.skylogistics.block.ItemVaultBlock;
import com.skylogistics.block.SkyNodeBlock;
import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.block.SkyDistributorBlock;
import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.block.entity.SimplePipeBlockEntity;
import com.skylogistics.block.entity.SkyDistributorBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(SkyLogistics.MOD_ID)
public class SkyLogisticsJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(SkyNodeJadeProvider.DataProvider.INSTANCE, SkyNodeBlockEntity.class);
        registration.registerBlockDataProvider(SimplePipeJadeProvider.DataProvider.INSTANCE, SimplePipeBlockEntity.class);
        registration.registerBlockDataProvider(ItemVaultJadeProvider.DataProvider.INSTANCE, ItemVaultBlockEntity.class);
        registration.registerBlockDataProvider(FluidVaultJadeProvider.DataProvider.INSTANCE, FluidVaultBlockEntity.class);
        registration.registerBlockDataProvider(SkyDistributorJadeProvider.DataProvider.INSTANCE,
                SkyDistributorBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(SkyNodeJadeProvider.INSTANCE, SkyNodeBlock.class);
        registration.registerBlockComponent(SkyNodeJadeProvider.INSTANCE, ExternalNetworkInterfaceBlock.class);
        registration.registerBlockComponent(SimplePipeJadeProvider.INSTANCE, SimplePipeBlock.class);
        registration.registerBlockComponent(ItemVaultJadeProvider.INSTANCE, ItemVaultBlock.class);
        registration.registerBlockComponent(FluidVaultJadeProvider.INSTANCE, FluidVaultBlock.class);
        registration.registerBlockComponent(SkyDistributorJadeProvider.INSTANCE, SkyDistributorBlock.class);
    }
}
