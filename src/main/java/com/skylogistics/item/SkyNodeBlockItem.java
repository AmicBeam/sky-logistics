package com.skylogistics.item;

import com.skylogistics.util.NodeMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class SkyNodeBlockItem extends BlockItem {
    public SkyNodeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(UseOnContext context) {
        net.minecraft.world.InteractionResult result = super.useOn(context);
        if (!result.consumesAction()) {
            return result;
        }

        Level level = context.getLevel();
        if (!level.isClientSide && context.getPlayer() != null) {
            NodeMode mode = context.getPlayer().isShiftKeyDown() ? NodeMode.INPUT : NodeMode.OUTPUT;
            context.getPlayer().displayClientMessage(Component.translatable("message.skylogistics.sky_node.placed",
                    Component.translatable(mode.translationKey())), true);
        }
        return result;
    }
}
