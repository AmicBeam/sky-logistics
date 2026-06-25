package com.skylogistics.menu;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModMenus;
import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

public class ConfiguratorMenu extends AbstractContainerMenu {
    private final InteractionHand hand;
    private final Player player;
    private int lineNodes;
    private int lineInputs;
    private int lineOutputs;

    public ConfiguratorMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(ModMenus.CONFIGURATOR.get(), containerId);
        this.hand = hand;
        this.player = inventory.player;
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return lineNodes;
            }

            @Override
            public void set(int value) {
                lineNodes = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return lineInputs;
            }

            @Override
            public void set(int value) {
                lineInputs = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return lineOutputs;
            }

            @Override
            public void set(int value) {
                lineOutputs = value;
            }
        });
    }

    public InteractionHand getHand() {
        return hand;
    }

    public int getLineNodes() {
        return lineNodes;
    }

    public int getLineInputs() {
        return lineInputs;
    }

    public int getLineOutputs() {
        return lineOutputs;
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
            case MenuAction.LINE_FIRST -> config = ConfiguratorItem.selectFirstLine(stack);
            case MenuAction.LINE_PREVIOUS -> config = ConfiguratorItem.selectPreviousLine(stack);
            case MenuAction.LINE_NEXT_OR_CREATE -> config = ConfiguratorItem.selectNextOrCreateLine(stack);
            case MenuAction.LINE_LAST -> config = ConfiguratorItem.selectLastLine(stack);
            case MenuAction.LINE_REMOVE_CURRENT -> config = ConfiguratorItem.removeCurrentLine(stack);
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
        refreshLineStats();
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        refreshLineStats();
        super.broadcastChanges();
    }

    private void refreshLineStats() {
        if (player.level().isClientSide || player.level().getServer() == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.read(stack);
        if (config == null) {
            lineNodes = 0;
            lineInputs = 0;
            lineOutputs = 0;
            return;
        }
        SkyNetworkRegistry.LineStats stats = SkyNetworkRegistry.lineStats(player.level().getServer(), config.lineId());
        lineNodes = Math.min(9999, stats.nodes());
        lineInputs = Math.min(9999, stats.inputs());
        lineOutputs = Math.min(9999, stats.outputs());
    }
}
