package com.skylogistics.menu;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.network.ConfiguratorLineDetailsPacket;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.network.SkyNecklaceTicker;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.util.RedstoneControl;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

public class ConfiguratorMenu extends AbstractContainerMenu {
    private static final int LINE_DETAIL_LIMIT = 64;
    private static final int LINE_DETAIL_SYNC_INTERVAL = 20;
    private final InteractionHand hand;
    private final Player player;
    private int lineNodes;
    private int lineInputs;
    private int lineOutputs;
    private UUID lastDetailLine;
    private long lastDetailSyncTime = Long.MIN_VALUE;

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
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.readOrCreate(stack, player);
        switch (action) {
            case MenuAction.NEW_LINE -> config = ConfiguratorItem.selectNextOrCreateLine(stack, player);
            case MenuAction.LINE_FIRST -> config = ConfiguratorItem.selectFirstLine(stack);
            case MenuAction.LINE_PREVIOUS -> config = ConfiguratorItem.selectPreviousLine(stack);
            case MenuAction.LINE_NEXT_OR_CREATE -> config = ConfiguratorItem.selectNextOrCreateLine(stack, player);
            case MenuAction.LINE_LAST -> config = ConfiguratorItem.selectLastLine(stack);
            case MenuAction.LINE_REMOVE_CURRENT -> {
                if (currentLineInUse(config)) {
                    player.displayClientMessage(Component.translatable(
                            "message.skylogistics.configurator.line_in_use"), true);
                    syncHeldStack(stack);
                    refreshLineStats();
                    syncLineDetails(true);
                    broadcastChanges();
                    return;
                }
                config = ConfiguratorItem.removeCurrentLine(stack, player);
            }
            case MenuAction.TOGGLE_ITEMS -> config = config.withItemsEnabled(!config.itemsEnabled());
            case MenuAction.TOGGLE_FLUIDS -> config = config.withFluidsEnabled(!config.fluidsEnabled());
            case MenuAction.TOGGLE_ENERGY -> config = config.withEnergyEnabled(!config.energyEnabled());
            case MenuAction.CONFIG_REDSTONE -> config = config.cycleRedstoneControl();
            case MenuAction.CONFIG_PRIORITY_DOWN -> config = config.adjustPriority(-1);
            case MenuAction.CONFIG_PRIORITY_UP -> config = config.adjustPriority(1);
            case MenuAction.CONFIG_PRIORITY_DOWN_FAST -> config = config.adjustPriority(-10);
            case MenuAction.CONFIG_PRIORITY_UP_FAST -> config = config.adjustPriority(10);
            default -> {
                return;
            }
        }
        ConfiguratorItem.writeConfig(stack, config);
        syncHeldStack(stack);
        refreshLineStats();
        syncLineDetails(true);
        broadcastChanges();
    }

    public void renameCurrentLine(Player player, String lineName) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.CONFIGURATOR.get())) {
            return;
        }
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.readOrCreate(stack, player);
        if (!player.level().isClientSide && player.level().getServer() != null) {
            SkyNetworkRegistry.renameLine(player.level().getServer(), config.lineId(), lineName,
                    ConfiguratorItem.assignedLineName(stack));
        }
        refreshLineStats();
        syncLineDetails(true);
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        refreshLineStats();
        syncLineDetails(false);
        super.broadcastChanges();
    }

    private boolean currentLineInUse(ConfiguratorItem.ToolConfig config) {
        if (player.level().isClientSide || player.level().getServer() == null) {
            return false;
        }
        SkyNetworkRegistry.LineStats stats = SkyNetworkRegistry.lineStats(player.level().getServer(), config.lineId());
        return stats.inputs() > 0 || stats.outputs() > 0;
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

    private void syncLineDetails(boolean immediate) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide
                || player.level().getServer() == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.read(stack);
        if (config == null) {
            return;
        }
        long gameTime = player.level().getGameTime();
        boolean lineChanged = !config.lineId().equals(lastDetailLine);
        if (!immediate && !lineChanged && gameTime - lastDetailSyncTime < LINE_DETAIL_SYNC_INTERVAL) {
            return;
        }
        List<SkyNetworkRegistry.LineFaceDetail> details =
                SkyNetworkRegistry.lineDetails(player.level().getServer(), config.lineId(), LINE_DETAIL_LIMIT);
        List<SkyNecklaceTicker.ActiveNecklaceDetail> necklaceDetails =
                SkyNecklaceTicker.activeDetails(config.lineId());
        List<ConfiguratorLineDetailsPacket.Entry> entries =
                new ArrayList<>(Math.min(LINE_DETAIL_LIMIT, details.size() + necklaceDetails.size()));
        for (SkyNecklaceTicker.ActiveNecklaceDetail detail : necklaceDetails) {
            if (entries.size() >= LINE_DETAIL_LIMIT) {
                break;
            }
            entries.add(new ConfiguratorLineDetailsPacket.Entry(detail.dimension(), detail.pos(), Direction.UP,
                    detail.pos(), "skylogistics:sky_necklace", detail.playerName(), detail.profileId(),
                    detail.profileTexture(), detail.profileTextureSignature(), detail.mode(), true, false, false,
                    RedstoneControl.IGNORE, detail.priority()));
        }
        for (SkyNetworkRegistry.LineFaceDetail detail : details) {
            if (entries.size() >= LINE_DETAIL_LIMIT) {
                break;
            }
            entries.add(new ConfiguratorLineDetailsPacket.Entry(detail.dimension(), detail.nodePos(), detail.face(),
                    detail.targetPos(), detail.targetBlockId(), "", null, "", "", detail.mode(),
                    detail.itemsEnabled(), detail.fluidsEnabled(), detail.energyEnabled(), detail.redstoneControl(),
                    detail.priority()));
        }
        ModNetworking.sendToPlayer(serverPlayer, new ConfiguratorLineDetailsPacket(config.lineId(), entries));
        SkyNetworkRegistry.syncLineName(serverPlayer, config.lineId(),
                ConfiguratorItem.assignedLineName(stack), config.lineName());
        lastDetailLine = config.lineId();
        lastDetailSyncTime = gameTime;
    }

    private void syncHeldStack(ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        int slot = hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().selected;
        player.getInventory().setChanged();
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, stack.copy()));
    }
}
