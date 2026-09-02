package com.skylogistics.network;

import com.skylogistics.block.entity.NetworkEndpointBlockEntity;
import com.skylogistics.block.entity.SkyDistributorBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.astages.AStagesTransferLimiter;
import com.skylogistics.compat.astages.TransferResource;
import com.skylogistics.compat.advancements.AdvancementTransferLimiter;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.NodeMode;
import com.skylogistics.util.RedstoneControl;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** A plain runtime endpoint. It has no BlockEntity, BlockState, or world position lifecycle of its own. */
public final class KleisRuntimeEndpoint implements ConfigurableLogisticsEndpoint {
    public static final Direction ENDPOINT_DIRECTION = Direction.NORTH;

    private final Level level;
    private final BlockPos targetPos;
    private final Direction targetFace;
    private final UUID ownerId;
    private final ItemStack[] upgrades = {ItemStack.EMPTY, ItemStack.EMPTY};
    private final int[] targetCursors = new int[NetworkEndpointBlockEntity.TargetResource.values().length];
    private Runnable changeListener = () -> {};
    private UUID lineId;
    private String lineName;
    private String assignedLineName;
    private NodeFaceMode mode;
    private boolean itemsEnabled;
    private boolean fluidsEnabled;
    private boolean energyEnabled;
    private int priority;
    private int slotLimit;
    private boolean limitByItems;
    private ItemStack filter = ItemStack.EMPTY;
    private FilterListItem.CompiledFilter compiledFilter = FilterListItem.CompiledFilter.ALLOW_ALL;
    private int itemCursor;
    private int fluidCursor;
    private long lastTransfer = Long.MIN_VALUE;

    public KleisRuntimeEndpoint(Level level, BlockPos targetPos, Direction targetFace, UUID ownerId,
            ConfiguratorItem.ToolConfig config, NodeFaceMode forcedMode) {
        this.level = level;
        this.targetPos = targetPos.immutable();
        this.targetFace = targetFace;
        this.ownerId = ownerId;
        applyPlacement(config, forcedMode, false);
    }

    public static KleisRuntimeEndpoint fromSavedData(ServerLevel level, BlockPos pos, Direction face,
            UUID ownerId, CompoundTag tag) {
        UUID lineId = tag.hasUUID("LineId") ? tag.getUUID("LineId")
                : UUID.nameUUIDFromBytes("skylogistics:kleis:legacy".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String lineName = tag.contains("LineName", Tag.TAG_STRING) ? tag.getString("LineName") : "Line 1";
        String assignedName = lineName;
        ListTag savedLines = tag.getList("Lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < savedLines.size(); i++) {
            CompoundTag savedLine = savedLines.getCompound(i);
            if (savedLine.hasUUID("Id") && savedLine.getUUID("Id").equals(lineId)) {
                assignedName = savedLine.contains("AssignedName", Tag.TAG_STRING)
                        ? savedLine.getString("AssignedName") : savedLine.getString("Name");
                break;
            }
        }
        NodeFaceMode mode = NodeFaceMode.byName(tag.getString("Mode"));
        if (tag.contains("Faces", Tag.TAG_COMPOUND)) {
            mode = NodeFaceMode.byName(tag.getCompound("Faces").getString(ENDPOINT_DIRECTION.getSerializedName()));
        }
        CompoundTag settings = tag.contains("FaceSettings", Tag.TAG_COMPOUND)
                ? tag.getCompound("FaceSettings").getCompound(ENDPOINT_DIRECTION.getSerializedName()) : new CompoundTag();
        boolean items = settings.contains("ItemsEnabled") ? settings.getBoolean("ItemsEnabled")
                : tag.getBoolean("ItemsEnabled");
        boolean fluids = settings.contains("FluidsEnabled") ? settings.getBoolean("FluidsEnabled")
                : tag.getBoolean("FluidsEnabled");
        boolean energy = settings.contains("EnergyEnabled") ? settings.getBoolean("EnergyEnabled")
                : tag.getBoolean("EnergyEnabled");
        int priority = settings.getInt("Priority");
        int slotLimit = settings.contains("SlotLimit") ? settings.getInt("SlotLimit") : 0;
        List<ItemStack> filters = new ArrayList<>();
        ListTag filterTags = settings.getList("Filters", Tag.TAG_COMPOUND);
        filters.add(filterTags.isEmpty() ? ItemStack.EMPTY
                : ItemStack.of(filterTags.getCompound(0).getCompound("Stack")));
        List<ItemStack> upgrades = new ArrayList<>();
        ListTag upgradeTags = tag.getList("Upgrades", Tag.TAG_COMPOUND);
        for (int i = 0; i < upgradeTags.size(); i++) {
            ItemStack upgrade = ItemStack.of(upgradeTags.getCompound(i).getCompound("Stack"));
            if (!upgrade.isEmpty() && !upgrade.is(ModItems.DIMENSION_UPGRADE.get())) upgrades.add(upgrade);
        }
        ConfiguratorItem.FaceConfig placement = new ConfiguratorItem.FaceConfig(mode, items, fluids, energy,
                false, RedstoneControl.IGNORE, priority, slotLimit, filters);
        KleisRuntimeEndpoint endpoint = new KleisRuntimeEndpoint(level, pos, face, ownerId,
                new ConfiguratorItem.ToolConfig(lineId, lineName, placement, Map.of(), false, upgrades), mode);
        endpoint.assignedLineName = assignedName;
        endpoint.limitByItems = settings.getBoolean("LimitByItems");
        for (int i = 0; i < Math.min(endpoint.upgrades.length, upgrades.size()); i++) {
            endpoint.upgrades[i] = upgrades.get(i).copy();
        }
        return endpoint;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("LineId", lineId);
        tag.putString("LineName", lineName);
        tag.putString("Mode", getMode().name());
        tag.putBoolean("ItemsEnabled", itemsEnabled);
        tag.putBoolean("FluidsEnabled", fluidsEnabled);
        tag.putBoolean("EnergyEnabled", energyEnabled);
        ListTag lines = new ListTag();
        CompoundTag savedLine = new CompoundTag();
        savedLine.putUUID("Id", lineId); savedLine.putString("Name", lineName);
        savedLine.putString("AssignedName", assignedLineName); lines.add(savedLine); tag.put("Lines", lines);
        ListTag savedUpgrades = new ListTag();
        for (int i = 0; i < upgrades.length; i++) {
            if (upgrades[i].isEmpty()) continue;
            CompoundTag entry = new CompoundTag(); entry.putInt("Slot", i);
            entry.put("Stack", upgrades[i].save(new CompoundTag())); savedUpgrades.add(entry);
        }
        if (!savedUpgrades.isEmpty()) tag.put("Upgrades", savedUpgrades);
        CompoundTag faces = new CompoundTag();
        for (Direction direction : Direction.values()) {
            faces.putString(direction.getSerializedName(), getFaceMode(direction).name());
        }
        tag.put("Faces", faces);
        CompoundTag settings = new CompoundTag();
        settings.putString("Redstone", RedstoneControl.IGNORE.name());
        settings.putInt("Priority", priority); settings.putInt("SlotLimit", slotLimit);
        settings.putBoolean("LimitByItems", limitByItems); settings.putBoolean("ItemsEnabled", itemsEnabled);
        settings.putBoolean("FluidsEnabled", fluidsEnabled); settings.putBoolean("EnergyEnabled", energyEnabled);
        if (!filter.isEmpty()) {
            ListTag filters = new ListTag(); CompoundTag entry = new CompoundTag(); entry.putInt("Slot", 0);
            entry.put("Stack", filter.save(new CompoundTag())); filters.add(entry); settings.put("Filters", filters);
        }
        CompoundTag faceSettings = new CompoundTag();
        faceSettings.put(ENDPOINT_DIRECTION.getSerializedName(), settings); tag.put("FaceSettings", faceSettings);
        return tag;
    }

    public void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> {} : listener;
    }

    @Override public Level getLevel() { return level; }
    @Override public BlockPos getBlockPos() { return targetPos; }
    @Override public boolean isRemoved() { return false; }
    @Override public UUID getLineId() { return lineId; }
    @Override public String getLineName() { return lineName; }
    @Override public String getAssignedLineName() { return assignedLineName; }
    @Override public NodeMode getMode() { return mode == NodeFaceMode.INPUT ? NodeMode.INPUT : NodeMode.OUTPUT; }
    @Override public NodeFaceMode getFaceMode(Direction direction) {
        return direction == ENDPOINT_DIRECTION ? mode : NodeFaceMode.NONE;
    }
    @Override public boolean isItemsEnabled(Direction direction) { return direction == ENDPOINT_DIRECTION && itemsEnabled; }
    @Override public boolean isFluidsEnabled(Direction direction) { return direction == ENDPOINT_DIRECTION && fluidsEnabled; }
    @Override public boolean isEnergyEnabled(Direction direction) { return direction == ENDPOINT_DIRECTION && energyEnabled; }
    @Override public BlockPos getTargetPos(Direction direction) { return targetPos; }
    @Override public Direction getAccessSide(Direction direction) { return targetFace; }
    @Override public Direction getTargetDirection() { return ENDPOINT_DIRECTION; }
    @Override public boolean usesSingleEndpoint() { return true; }
    @Override public Direction getSingleEndpointDirection() { return ENDPOINT_DIRECTION; }
    @Override public boolean canConfigureFace(Direction direction) { return direction == ENDPOINT_DIRECTION; }
    @Override public boolean hasConfigurableTarget(Direction direction) {
        return direction == ENDPOINT_DIRECTION && level.isLoaded(targetPos) && !level.getBlockState(targetPos).isAir();
    }
    @Override public ItemStack getTargetIcon(Direction direction) {
        return hasConfigurableTarget(direction) ? level.getBlockState(targetPos).getBlock().asItem().getDefaultInstance()
                : ItemStack.EMPTY;
    }
    @Override public Component getTargetName(Direction direction) {
        return hasConfigurableTarget(direction) ? level.getBlockState(targetPos).getBlock().getName()
                : Component.translatable("screen.skylogistics.no_target");
    }

    private SkyDistributorBlockEntity distributor() {
        return level.isLoaded(targetPos) && level.getBlockEntity(targetPos) instanceof SkyDistributorBlockEntity value
                ? value : null;
    }
    @Override public IItemHandler getEndpointItemHandler(Direction direction, long gameTime) {
        SkyDistributorBlockEntity value = distributor(); return value == null ? null : value.itemHandler(targetFace);
    }
    @Override public IFluidHandler getEndpointFluidHandler(Direction direction, long gameTime) {
        SkyDistributorBlockEntity value = distributor(); return value == null ? null : value.fluidHandler(targetFace);
    }
    @Override public ChemicalHandlerBridge getEndpointChemicalHandler(Direction direction, long gameTime) {
        SkyDistributorBlockEntity value = distributor(); return value == null ? null : value.chemicalHandler(targetFace);
    }
    @Override public IEnergyStorage getEndpointEnergyHandler(Direction direction, long gameTime) {
        SkyDistributorBlockEntity value = distributor(); return value == null ? null : value.energyHandler(targetFace);
    }
    @Override public ManaHandlerBridge getEndpointManaHandler(Direction direction, long gameTime) {
        SkyDistributorBlockEntity value = distributor(); return value == null ? null : value.manaHandler(targetFace);
    }
    @Override public SourceHandlerBridge getEndpointSourceHandler(Direction direction, long gameTime) {
        SkyDistributorBlockEntity value = distributor(); return value == null ? null : value.sourceHandler(targetFace);
    }

    @Override public boolean allowsItem(Direction direction, ItemStack stack) { return compiledFilter.matches(stack); }
    @Override public boolean allowsFluid(Direction direction, FluidStack stack) { return compiledFilter.matchesFluid(stack); }
    @Override public boolean allowsChemical(Direction direction, ChemicalStackView stack) {
        return !compiledFilter.hasChemicalRules() || compiledFilter.matchesChemical(stack) == compiledFilter.whitelist();
    }
    private boolean allowsEnergyType(String modId) {
        return !compiledFilter.hasEnergyRules() || compiledFilter.matchesEnergy(modId) == compiledFilter.whitelist();
    }
    @Override public boolean allowsEnergy(Direction direction) { return allowsEnergyType(TagFilterListItem.FORGE_ENERGY_MOD_ID); }
    @Override public boolean allowsMana(Direction direction) { return allowsEnergyType(TagFilterListItem.BOTANIA_MANA_MOD_ID); }
    @Override public boolean allowsSource(Direction direction) { return allowsEnergyType(TagFilterListItem.ARS_NOUVEAU_SOURCE_MOD_ID); }
    @Override public ItemStack getFaceFilter(Direction direction, int slot) {
        return direction == ENDPOINT_DIRECTION && slot == 0 ? filter : ItemStack.EMPTY;
    }
    @Override public boolean isFaceRedstoneAllowed(Direction direction) { return true; }
    @Override public RedstoneControl getRedstoneControl(Direction direction) { return RedstoneControl.IGNORE; }
    @Override public boolean consumeRedstonePulse(Direction direction) { return false; }
    @Override public boolean supportsRedstoneControl() { return false; }
    @Override public int getPriority(Direction direction) { return direction == ENDPOINT_DIRECTION ? priority : 0; }
    @Override public int getItemSlotLimit(Direction direction) { return direction == ENDPOINT_DIRECTION ? slotLimit : 0; }
    @Override public long getMaintainAmount(Direction direction) { return getItemSlotLimit(direction); }
    @Override public boolean isMaintainByAmount(Direction direction) { return direction == ENDPOINT_DIRECTION && limitByItems; }
    @Override public boolean isItemLimitByItems(Direction direction) { return isMaintainByAmount(direction); }
    @Override public int getOperationRate() { return 1 + speedUpgradeCount(); }
    @Override public boolean hasDimensionUpgrade() { return true; }
    @Override public UUID getTransferOwnerId() { return ownerId; }

    private long limit(TransferResource resource, long amount) {
        if (!(level instanceof ServerLevel serverLevel)) return amount;
        long staged = AStagesTransferLimiter.limit(ownerId, resource, amount, level.getGameTime());
        return AdvancementTransferLimiter.limit(serverLevel.getServer(), ownerId, resource, staged, level.getGameTime());
    }
    @Override public long limitItemTransfer(long amount) { return limit(TransferResource.ITEMS, amount); }
    @Override public long limitFluidTransfer(long amount) { return limit(TransferResource.FLUIDS, amount); }
    @Override public long limitEnergyTransfer(long amount) { return limit(TransferResource.ENERGY, amount); }
    @Override public long limitChemicalTransfer(long amount) { return limit(TransferResource.CHEMICALS, amount); }
    @Override public long limitManaTransfer(long amount) { return limit(TransferResource.MANA, amount); }
    @Override public long limitSourceTransfer(long amount) { return limit(TransferResource.SOURCE, amount); }
    @Override public long getProgressionTransferLimit(TransferResource resource) { return limit(resource, Long.MAX_VALUE); }
    @Override public long getConfiguredTransferLimit(TransferResource resource) {
        return switch (resource) {
            case ITEMS -> SkyLogisticsConfig.nodeItemTransferLimit();
            case ENERGY, MANA, SOURCE -> SkyLogisticsConfig.nodeEnergyTransferLimit();
            default -> Long.MAX_VALUE;
        };
    }
    @Override public boolean supportsChemicalEndpoint(Direction direction) {
        return LogisticsTargetCapabilities.detect(level, targetPos, targetFace).chemical();
    }
    @Override public boolean supportsManaEndpoint(Direction direction) {
        return LogisticsTargetCapabilities.detect(level, targetPos, targetFace).mana();
    }
    @Override public boolean supportsSourceEndpoint(Direction direction) {
        return LogisticsTargetCapabilities.detect(level, targetPos, targetFace).source();
    }
    @Override public TransferResource firstEnabledTransferResource(Direction direction) {
        if (direction != ENDPOINT_DIRECTION || !hasConfigurableTarget(direction)) return null;
        LogisticsTargetCapabilities capabilities = LogisticsTargetCapabilities.detect(level, targetPos, targetFace);
        if (itemsEnabled && capabilities.items()) return TransferResource.ITEMS;
        if (fluidsEnabled && capabilities.fluid()) return TransferResource.FLUIDS;
        if (fluidsEnabled && capabilities.chemical()) return TransferResource.CHEMICALS;
        if (energyEnabled && capabilities.nativeEnergy()) return TransferResource.ENERGY;
        if (energyEnabled && capabilities.mana()) return TransferResource.MANA;
        if (energyEnabled && capabilities.source()) return TransferResource.SOURCE;
        return null;
    }

    @Override public int nextItemStart(int slots) { int start = slots <= 0 ? 0 : Math.floorMod(itemCursor, slots); if (slots > 0) itemCursor = (start + 1) % slots; return start; }
    @Override public int nextFluidStart(int tanks) { int start = tanks <= 0 ? 0 : Math.floorMod(fluidCursor, tanks); if (tanks > 0) fluidCursor = (start + 1) % tanks; return start; }
    @Override public int targetCursor(NetworkEndpointBlockEntity.TargetResource resource) { return targetCursors[resource.ordinal()]; }
    @Override public void advanceTargetCursor(NetworkEndpointBlockEntity.TargetResource resource) { int i=resource.ordinal(); targetCursors[i]=targetCursors[i]==Integer.MAX_VALUE?0:targetCursors[i]+1; }
    @Override public void recordRecentTransfer() { lastTransfer = level.getGameTime(); }
    @Override public void recordRecentTransfer(Direction direction) { recordRecentTransfer(); }
    @Override public boolean hasRecentTransfer() { return lastTransfer != Long.MIN_VALUE && level.getGameTime()-lastTransfer<=40L; }
    @Override public boolean hasRecentTransfer(Direction direction) { return hasRecentTransfer(); }

    @Override public ItemStack getUpgrade(int slot) { return slot >= 0 && slot < upgrades.length ? upgrades[slot] : ItemStack.EMPTY; }
    @Override public boolean canAcceptUpgrade(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        return slot >= 0 && slot < upgrades.length && (stack.is(ModItems.SPEED_UPGRADE.get())
                || stack.is(ModItems.FORCE_EXTRACTION_UPGRADE.get()));
    }
    @Override public void setUpgrade(int slot, ItemStack stack) {
        if (slot < 0 || slot >= upgrades.length) return;
        ItemStack next = canAcceptUpgrade(slot, stack) ? stack.copy() : ItemStack.EMPTY;
        if (!next.isEmpty()) next.setCount(Math.min(next.getCount(), SkyNodeBlockEntity.maxUpgradeStackSize(next)));
        upgrades[slot] = next; changed();
    }
    private int speedUpgradeCount() { return Arrays.stream(upgrades).filter(s -> s.is(ModItems.SPEED_UPGRADE.get())).mapToInt(ItemStack::getCount).sum(); }
    @Override public boolean rejectsTagFaceFilter(Direction direction, ItemStack stack) { return false; }
    @Override public boolean hasTagFaceFilterRestriction(Direction direction) { return false; }
    @Override public boolean hasValidItemWhitelistFaceFilter(Direction direction) { return true; }
    @Override public void setFaceFilter(Direction direction, int slot, ItemStack stack) {
        if (direction != ENDPOINT_DIRECTION || slot != 0 || !SkyNodeBlockEntity.isFaceFilterItem(stack) && !stack.isEmpty()) return;
        filter = stack.copy(); if (!filter.isEmpty()) filter.setCount(1); compiledFilter = FilterListItem.compile(filter); changed();
    }
    @Override public void setFaceMode(Direction direction, NodeFaceMode value) { if (direction==ENDPOINT_DIRECTION && value!=mode) { mode=value; changed(); } }
    @Override public void setItemsEnabled(Direction direction, boolean value) { if(direction==ENDPOINT_DIRECTION&&itemsEnabled!=value){itemsEnabled=value;changed();} }
    @Override public void setFluidsEnabled(Direction direction, boolean value) { if(direction==ENDPOINT_DIRECTION&&fluidsEnabled!=value){fluidsEnabled=value;changed();} }
    @Override public void setEnergyEnabled(Direction direction, boolean value) { if(direction==ENDPOINT_DIRECTION&&energyEnabled!=value){energyEnabled=value;changed();} }
    @Override public void adjustPriority(Direction direction, int delta) { if(direction==ENDPOINT_DIRECTION){priority=Math.max(-99,Math.min(99,priority+delta));changed();} }
    @Override public void adjustItemSlotLimit(Direction direction, int delta) { if(direction==ENDPOINT_DIRECTION)setItemSlotLimit(direction,slotLimit+delta); }
    @Override public void setItemSlotLimit(Direction direction, int value) { if(direction==ENDPOINT_DIRECTION){slotLimit=SkyNodeBlockEntity.clampItemSlotLimit(value);changed();} }
    @Override public void toggleItemLimitUnit(Direction direction) { if(direction==ENDPOINT_DIRECTION){limitByItems=!limitByItems;changed();} }
    @Override public void cycleRedstoneControl(Direction direction) {}
    @Override public void setMode(NodeMode value) { setFaceMode(ENDPOINT_DIRECTION,value==NodeMode.INPUT?NodeFaceMode.INPUT:NodeFaceMode.OUTPUT); }
    @Override public void selectPlayerLine(UUID id, String assigned, String display) { lineId=id;assignedLineName=assigned;lineName=display;changed(); }
    @Override public void applyPlacementToolConfig(ConfiguratorItem.ToolConfig config, boolean includeMode) {
        applyPlacement(config, includeMode ? config.placement().mode() : mode, true);
    }
    @Override public void applySingleEndpointToolConfig(ConfiguratorItem.ToolConfig config, Player player) {
        applyPlacementToolConfig(config, true);
        installCopiedUpgrades(config, player);
    }
    public boolean hasEnabledTargetCapability() {
        LogisticsTargetCapabilities capabilities = LogisticsTargetCapabilities.detect(level, targetPos, targetFace);
        return KleisEndpointPolicy.hasEnabledCapability(itemsEnabled, fluidsEnabled, energyEnabled,
                capabilities.items(), capabilities.fluids(), capabilities.energy());
    }
    public boolean supportsToolConfig(ConfiguratorItem.ToolConfig config) {
        LogisticsTargetCapabilities capabilities = LogisticsTargetCapabilities.detect(level, targetPos, targetFace);
        ConfiguratorItem.FaceConfig face = config.placement();
        return KleisEndpointPolicy.supportsConfiguration(face.autoDetectResources(),
                face.itemsEnabled(), face.fluidsEnabled(), face.energyEnabled(),
                capabilities.items(), capabilities.fluids(), capabilities.energy());
    }
    private void installCopiedUpgrades(ConfiguratorItem.ToolConfig config, Player player) {
        for (ItemStack requested : config.upgrades()) {
            if (!canAcceptKleisUpgrade(requested)) continue;
            int targetCount = Math.min(requested.getCount(), SkyNodeBlockEntity.maxUpgradeStackSize(requested));
            while (upgradeCount(requested.getItem()) < targetCount) {
                int slot = upgradeSlot(requested);
                if (slot < 0 || !consumeUpgradeFromPlayer(player, requested.getItem())) break;
                ItemStack installed = upgrades[slot];
                if (installed.isEmpty()) {
                    installed = requested.copy();
                    installed.setCount(1);
                } else {
                    installed = installed.copy();
                    installed.grow(1);
                }
                upgrades[slot] = installed;
            }
        }
        changed();
    }
    private boolean canAcceptKleisUpgrade(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ModItems.SPEED_UPGRADE.get())
                || stack.is(ModItems.FORCE_EXTRACTION_UPGRADE.get()));
    }
    private int upgradeCount(Item item) {
        return Arrays.stream(upgrades).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private int upgradeSlot(ItemStack requested) {
        for (int i = 0; i < upgrades.length; i++) if (ItemStack.isSameItem(upgrades[i], requested)) return i;
        for (int i = 0; i < upgrades.length; i++) if (upgrades[i].isEmpty()) return i;
        return -1;
    }
    private static boolean consumeUpgradeFromPlayer(Player player, Item item) {
        if (player == null) return false;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) continue;
            stack.shrink(1);
            inventory.setChanged();
            return true;
        }
        return false;
    }
    private void applyPlacement(ConfiguratorItem.ToolConfig config, NodeFaceMode selectedMode, boolean notify) {
        lineId=config.lineId(); lineName=config.lineName(); assignedLineName=config.lineName(); mode=selectedMode;
        ConfiguratorItem.FaceConfig face=config.placement(); priority=face.priority(); slotLimit=face.slotLimit(); limitByItems=false;
        if(face.autoDetectResources()) detectResources(); else {itemsEnabled=face.itemsEnabled();fluidsEnabled=face.fluidsEnabled();energyEnabled=face.energyEnabled();}
        List<ItemStack> filters=face.filters(); filter=filters.isEmpty()?ItemStack.EMPTY:filters.get(0).copy(); compiledFilter=FilterListItem.compile(filter);
        if(notify)changed();
    }
    private void detectResources() {
        LogisticsTargetCapabilities capabilities = LogisticsTargetCapabilities.detect(level, targetPos, targetFace);
        itemsEnabled = capabilities.items();
        fluidsEnabled = capabilities.fluids();
        energyEnabled = capabilities.energy();
    }
    @Override public void setChanged() { changed(); }
    private void changed() { changeListener.run(); }
}
