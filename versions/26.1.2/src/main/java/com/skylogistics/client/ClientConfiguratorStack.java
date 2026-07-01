package com.skylogistics.client;

import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class ClientConfiguratorStack {
    private ClientConfiguratorStack() {
    }

    public static void apply(InteractionHand hand, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !(minecraft.player.containerMenu instanceof ConfiguratorMenu menu)
                || menu.getHand() != hand
                || !stack.is(ModItems.CONFIGURATOR.get())) {
            return;
        }
        minecraft.player.setItemInHand(hand, stack);
    }
}
