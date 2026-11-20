package com.skylogistics.menu;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.network.ConfiguratorLineDetailsPacket;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.network.SkyNecklaceTicker;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.network.SkyPlayerLines;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.util.RedstoneControl;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
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
    private int lineIndex;
    private int lineCount = 1;
    private UUID lastDetailLine;
    private long lastDetailSyncTime = Long.MIN_VALUE;
    private boolean initialStackSynced;
    private boolean lineEditable;
    private boolean templateEditable;
    private final ConfiguratorTemplateSlots templateSlots = new ConfiguratorTemplateSlots();
    private boolean templatePage;
    private final Direction templateFace;
    private boolean openedFromCopy;

    public boolean canEditCurrentLine() { return lineEditable; }
    public boolean canEditTemplate() { return templateEditable; }
    public void showTemplatePage(boolean show) { templatePage = show; }
    public ConfiguratorMenu(int containerId, Inventory inventory, InteractionHand hand) {
        this(containerId, inventory, hand, false);
    }

    public ConfiguratorMenu(int containerId, Inventory inventory, InteractionHand hand, boolean openedFromCopy) {
        super(ModMenus.CONFIGURATOR.get(), containerId);
        this.hand = hand;
        this.player = inventory.player;
        this.openedFromCopy = openedFromCopy;
        this.templateFace = openedFromCopy ? ConfiguratorTemplateSlots.firstCopiedFace(player.getItemInHand(hand)) : null;
        templateSlots.add(inventory, this::addSlot, () -> templatePage, () -> templatePage && templateEditable,
                this::boundSlot);
        addDataSlot(new DataSlot() {
            @Override public int get() {
                return (templateEditable ? 1 : 0) | (ConfiguratorMenu.this.openedFromCopy ? 2 : 0);
            }
            @Override public void set(int value) {
                templateEditable = (value & 1) != 0;
                ConfiguratorMenu.this.openedFromCopy = (value & 2) != 0;
            }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return lineEditable ? 1 : 0; }
            @Override public void set(int value) { lineEditable = value != 0; }
        });
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
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return lineIndex;
            }

            @Override
            public void set(int value) {
                lineIndex = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return lineCount;
            }

            @Override
            public void set(int value) {
                lineCount = value;
            }
        });
    }

    private com.skylogistics.network.PermissionSnapshotPacket permissions;
    public com.skylogistics.network.PermissionSnapshotPacket permissions() { return permissions; }
    public void acceptPermissions(com.skylogistics.network.PermissionSnapshotPacket packet) { permissions = packet; }

    public void permissionAction(Player actor, int action, String query, int requestedPage, UUID target) {
        if (!(actor instanceof ServerPlayer serverPlayer) || actor != player || !stillValid(actor)) return;
        var server = actor.level().getServer();
        if (server == null || action < 0 || action > 4) return;
        com.skylogistics.network.SkyLineAccess.rememberPlayers(server);
        var data = SkyPlayerLines.get(server);
        var access = data.permissions();
        UUID owner = actor.getUUID(); // Never trust an owner UUID supplied by the client.
        if (action == 1 || action == 2) { access.setPublic(owner, action == 1); data.setDirty(); }
        if ((action == 3 || action == 4) && target != null && !target.equals(owner)
                && (access.knownPlayers().containsKey(target) || access.granted(owner, target))) {
            access.setGranted(owner, target, action == 3); data.setDirty();
        }
        String search = query.strip().toLowerCase(java.util.Locale.ROOT);
        var candidates = search.isEmpty() ? access.grantedPlayers(owner) : access.knownPlayers().keySet();
        var entries = candidates.stream().filter(id -> !id.equals(owner))
                .filter(id -> search.isEmpty() || access.name(id).toLowerCase(java.util.Locale.ROOT).contains(search)
                        || id.toString().equals(search))
                .sorted(java.util.Comparator.comparing((UUID id) -> access.name(id), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(UUID::toString))
                .map(id -> new com.skylogistics.network.PermissionSnapshotPacket.Entry(id, access.name(id), access.granted(owner, id)))
                .toList();
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, (entries.size() - 1) / 7)));
        ModNetworking.sendToPlayer(serverPlayer, new com.skylogistics.network.PermissionSnapshotPacket(
                containerId, access.isPublic(owner), query, page, entries.size(),
                entries.subList(page * 7, Math.min(entries.size(), page * 7 + 7))));
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

    public int getLineIndex() {
        return lineIndex;
    }

    public int getLineCount() {
        return lineCount;
    }

    @Override
    public boolean stillValid(Player player) {
        return ConfiguratorItem.isConfigurationTool(player.getItemInHand(hand));
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (!templatePage || !stillValid(player)) return;
        if (slotId >= 0 && slotId < ConfiguratorTemplateSlots.FIRST_PLAYER_SLOT) {
            if (!player.level().isClientSide() && input == ContainerInput.PICKUP && (button == 0 || button == 1)) {
                editSample(player, slotId, getCarried(), button == 1);
            }
            return;
        }
        if (input == ContainerInput.SWAP && boundSlot(button)) return;
        super.clicked(slotId, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!templatePage || !stillValid(player)
                || index < ConfiguratorTemplateSlots.FIRST_PLAYER_SLOT || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack sample = slot.getItem();
        if (com.skylogistics.item.FilterListItem.isFilterItem(sample)) {
            if (!player.level().isClientSide()) editSample(player, ConfiguratorTemplateSlots.FILTER, sample, false);
            return ItemStack.EMPTY;
        }
        if (com.skylogistics.block.entity.SkyNodeBlockEntity.isUpgradeItem(sample)) {
            if (!player.level().isClientSide()) {
                int free = -1;
                for (int target = 0; target < ConfiguratorTemplateSlots.UPGRADES; target++) {
                    ItemStack marker = slots.get(target).getItem();
                    if (marker.is(sample.getItem())) {
                        editSample(player, target, sample, false);
                        return ItemStack.EMPTY;
                    }
                    if (marker.isEmpty() && free < 0) free = target;
                }
                if (free >= 0) editSample(player, free, sample, false);
            }
            return ItemStack.EMPTY;
        }
        int hotbar = ConfiguratorTemplateSlots.FIRST_PLAYER_SLOT + 27;
        ItemStack original = sample.copy();
        if (!moveItemStackTo(sample, index < hotbar ? hotbar : ConfiguratorTemplateSlots.FIRST_PLAYER_SLOT,
                index < hotbar ? slots.size() : hotbar, false)) return ItemStack.EMPTY;
        if (sample.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    private boolean boundSlot(int index) {
        return index == (hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND
                : player.getInventory().getSelectedSlot());
    }

    private void editSample(Player player, int slot, ItemStack sample, boolean single) {
        ItemStack tool = player.getItemInHand(hand);
        if (ConfiguratorTemplateSlots.canEdit(player, tool, openedFromCopy)
                && ConfiguratorTemplateSlots.edit(tool, templateFace, openedFromCopy, slot, sample, single)) {
            syncHeldStack(tool);
            broadcastChanges();
        }
    }

    private boolean applyTemplatePageAction(int action) {
        if (action == MenuAction.CONFIG_TEMPLATE_PAGE_OPEN || action == MenuAction.CONFIG_TEMPLATE_PAGE_CLOSE) {
            if (getCarried().isEmpty()) showTemplatePage(action == MenuAction.CONFIG_TEMPLATE_PAGE_OPEN);
            return true;
        }
        return false;
    }

    public void applyAction(Player player, int action) {
        ItemStack stack = player.getItemInHand(hand);
        if (!ConfiguratorItem.isConfigurationTool(stack)) {
            return;
        }
        if (applyTemplatePageAction(action)) {
            broadcastChanges();
            return;
        }
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.readOrCreate(stack, player);
        boolean navigation = action == MenuAction.NEW_LINE || action == MenuAction.LINE_FIRST
                || action == MenuAction.LINE_PREVIOUS || action == MenuAction.LINE_NEXT_OR_CREATE
                || action == MenuAction.LINE_LAST || action == MenuAction.LINE_REMOVE_CURRENT;
        if (!navigation && !com.skylogistics.network.SkyLineAccess.check(player, config.lineId())) {
            syncHeldStack(stack);
            return;
        }
        switch (action) {
            case MenuAction.NEW_LINE, MenuAction.LINE_NEXT_OR_CREATE -> config = selectConfigLine(config,
                    SkyPlayerLines.selectNextOrCreate(player.level().getServer(), player, config.lineId(),
                            ConfiguratorItem.assignedLineName(stack), config.lineName()));
            case MenuAction.LINE_FIRST -> config = selectConfigLine(config,
                    SkyPlayerLines.selectFirst(player.level().getServer(), player, config.lineId(),
                            ConfiguratorItem.assignedLineName(stack), config.lineName()));
            case MenuAction.LINE_PREVIOUS -> config = selectConfigLine(config,
                    SkyPlayerLines.selectPrevious(player.level().getServer(), player, config.lineId(),
                            ConfiguratorItem.assignedLineName(stack), config.lineName()));
            case MenuAction.LINE_LAST -> config = selectConfigLine(config,
                    SkyPlayerLines.selectLast(player.level().getServer(), player, config.lineId(),
                            ConfiguratorItem.assignedLineName(stack), config.lineName()));
            case MenuAction.LINE_REMOVE_CURRENT -> {
                if (currentLineInUse(config)) {
                    player.sendOverlayMessage(Component.translatable(
                            "message.skylogistics.configurator.line_in_use"));
                    syncHeldStack(stack);
                    refreshLineStats();
                    syncLineDetails(true);
                    broadcastChanges();
                    return;
                }
                config = selectConfigLine(config,
                        SkyPlayerLines.removeCurrent(player.level().getServer(), player, config.lineId(),
                                ConfiguratorItem.assignedLineName(stack), config.lineName()));
            }
            case MenuAction.TOGGLE_ITEMS -> config = config.withItemsEnabled(!config.itemsEnabled());
            case MenuAction.TOGGLE_FLUIDS -> config = config.withFluidsEnabled(!config.fluidsEnabled());
            case MenuAction.TOGGLE_ENERGY -> config = config.withEnergyEnabled(!config.energyEnabled());
            case MenuAction.TOGGLE_AUTO_RESOURCES -> config = config.withAutoDetectResources();
            case MenuAction.CONFIG_REDSTONE -> config = config.cycleRedstoneControl();
            case MenuAction.CONFIG_PRIORITY_DOWN -> config = config.adjustPriority(-1);
            case MenuAction.CONFIG_PRIORITY_UP -> config = config.adjustPriority(1);
            case MenuAction.CONFIG_PRIORITY_DOWN_FAST -> config = config.adjustPriority(-10);
            case MenuAction.CONFIG_PRIORITY_UP_FAST -> config = config.adjustPriority(10);
            case MenuAction.CONFIG_SLOT_LIMIT_DOWN -> config = config.adjustSlotLimit(-1);
            case MenuAction.CONFIG_SLOT_LIMIT_UP -> config = config.adjustSlotLimit(1);
            case MenuAction.CONFIG_SLOT_LIMIT_DOWN_FAST -> config = config.adjustSlotLimit(-10);
            case MenuAction.CONFIG_SLOT_LIMIT_UP_FAST -> config = config.adjustSlotLimit(10);
            default -> {
                return;
            }
        }
        ConfiguratorItem.writeConfig(stack, config, playerLineSelection(config).assignedName());
        syncHeldStack(stack);
        refreshLineStats();
        syncLineDetails(true);
        broadcastChanges();
    }

    public void renameCurrentLine(Player player, String lineName) {
        ItemStack stack = player.getItemInHand(hand);
        if (!ConfiguratorItem.isConfigurationTool(stack)) {
            return;
        }
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.readOrCreate(stack, player);
        if (!com.skylogistics.network.SkyLineAccess.check(player, config.lineId())) return;
        if (!player.level().isClientSide() && player.level().getServer() != null) {
            SkyNetworkRegistry.renameLine(player.level().getServer(), config.lineId(), lineName,
                    playerLineSelection(config).assignedName());
        }
        refreshLineStats();
        syncLineDetails(true);
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        var config = ConfiguratorItem.read(player.getItemInHand(hand));
        lineEditable = config != null && com.skylogistics.network.SkyLineAccess.canUse(player, config.lineId());
        templateEditable = ConfiguratorTemplateSlots.canEdit(player, player.getItemInHand(hand), openedFromCopy);
        if (templatePage) templateSlots.refresh(player.getItemInHand(hand), templateFace, openedFromCopy, templateEditable);
        syncPlayerLineSelection();
        refreshLineStats();
        syncInitialHeldStack();
        syncLineDetails(false);
        super.broadcastChanges();
    }

    private ConfiguratorItem.ToolConfig selectConfigLine(ConfiguratorItem.ToolConfig config,
            SkyPlayerLines.LineSelection selection) {
        lineIndex = selection.index();
        lineCount = selection.count();
        return config.withLine(selection.lineId(), selection.displayName());
    }

    private void syncPlayerLineSelection() {
        ItemStack stack = player.getItemInHand(hand);
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.read(stack);
        if (config == null || player.level().isClientSide() || player.level().getServer() == null) {
            return;
        }
        SkyPlayerLines.LineSelection selection = playerLineSelection(config);
        lineIndex = selection.index();
        lineCount = selection.count();
        if (player instanceof ServerPlayer serverPlayer) {
            SkyNetworkRegistry.syncLineName(serverPlayer, selection.lineId(), selection.assignedName(),
                    selection.displayName());
        }
    }

    private SkyPlayerLines.LineSelection playerLineSelection(ConfiguratorItem.ToolConfig config) {
        ItemStack stack = player.getItemInHand(hand);
        return SkyPlayerLines.selection(player.level().getServer(), player, config.lineId(),
                ConfiguratorItem.assignedLineName(stack), config.lineName());
    }

    private boolean currentLineInUse(ConfiguratorItem.ToolConfig config) {
        if (player.level().isClientSide() || player.level().getServer() == null) {
            return false;
        }
        SkyNetworkRegistry.LineStats stats = SkyNetworkRegistry.lineStats(player.level().getServer(), config.lineId());
        return stats.inputs() > 0 || stats.outputs() > 0;
    }

    private void refreshLineStats() {
        if (player.level().isClientSide() || player.level().getServer() == null) {
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
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()
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
                    detail.profileTexture(), detail.profileTextureSignature(), detail.mode(), detail.maintainMode(),
                    true, false, false, RedstoneControl.IGNORE, detail.priority()));
        }
        for (SkyNetworkRegistry.LineFaceDetail detail : details) {
            if (entries.size() >= LINE_DETAIL_LIMIT) {
                break;
            }
            entries.add(new ConfiguratorLineDetailsPacket.Entry(detail.dimension(), detail.nodePos(), detail.face(),
                    detail.targetPos(), detail.targetBlockId(), "", null, "", "", detail.mode(),
                    false, detail.itemsEnabled(), detail.fluidsEnabled(), detail.energyEnabled(),
                    detail.redstoneControl(), detail.priority()));
        }
        ModNetworking.sendToPlayer(serverPlayer, new ConfiguratorLineDetailsPacket(config.lineId(), entries));
        SkyPlayerLines.LineSelection selection = playerLineSelection(config);
        SkyNetworkRegistry.syncLineName(serverPlayer, selection.lineId(), selection.assignedName(),
                selection.displayName());
        lastDetailLine = config.lineId();
        lastDetailSyncTime = gameTime;
    }

    private void syncInitialHeldStack() {
        if (initialStackSynced || !(player instanceof ServerPlayer)) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!ConfiguratorItem.isConfigurationTool(stack)) {
            return;
        }
        // The configurator menu has no inventory slots, so opening-time NBT needs an explicit sync.
        syncHeldStack(stack);
        initialStackSynced = true;
    }

    private void syncHeldStack(ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        int slot = hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().getSelectedSlot();
        player.getInventory().setChanged();
        serverPlayer.connection.send(new ClientboundSetPlayerInventoryPacket(slot, stack.copy()));
    }
}
