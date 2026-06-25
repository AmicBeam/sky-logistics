package com.skylogistics.menu;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModMenus;
import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class ConfiguratorMenu extends AbstractContainerMenu {
    private final InteractionHand hand;

    public ConfiguratorMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(ModMenus.CONFIGURATOR.get(), containerId);
        this.hand = hand;
    }

    public InteractionHand getHand() {
        return hand;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).is(ModItems.CONFIGURATOR.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public void applyAction(Player player, int action) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.CONFIGURATOR.get())) {
            return;
        }
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.readOrCreate(stack);
        switch (action) {
            case MenuAction.NEW_LINE -> config = config.withLine(UUID.randomUUID());
            case MenuAction.TOGGLE_ITEMS -> config = config.withItemsEnabled(!config.itemsEnabled());
            case MenuAction.TOGGLE_FLUIDS -> config = config.withFluidsEnabled(!config.fluidsEnabled());
            case MenuAction.TOGGLE_ENERGY -> config = config.withEnergyEnabled(!config.energyEnabled());
            case MenuAction.CONFIG_REDSTONE -> config = config.cycleRedstoneControl();
            case MenuAction.CONFIG_PRIORITY_DOWN -> config = config.adjustPriority(-1);
            case MenuAction.CONFIG_PRIORITY_UP -> config = config.adjustPriority(1);
            default -> {
                return;
            }
        }
        ConfiguratorItem.writeConfig(stack, config);
        broadcastChanges();
    }
}
