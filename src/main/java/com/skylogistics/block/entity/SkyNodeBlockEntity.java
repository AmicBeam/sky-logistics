package com.skylogistics.block.entity;

import com.skylogistics.block.SkyNodeBlock;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.NodeMode;
import com.skylogistics.util.RedstoneControl;
import java.util.EnumMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

public class SkyNodeBlockEntity extends BlockEntity {
    public static final int UPGRADE_SLOTS = 2;
    public static final int FACE_FILTER_SLOTS = 1;

    private UUID lineId = UUID.randomUUID();
    private NodeMode mode = NodeMode.OUTPUT;
    private final EnumMap<Direction, NodeFaceMode> faceModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, RedstoneControl> redstoneControls = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> priorities = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Boolean> faceItemsEnabled = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Boolean> faceFluidsEnabled = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Boolean> faceEnergyEnabled = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, NonNullList<ItemStack>> faceFilters = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, FilterListItem.CompiledFilter[]> compiledFaceFilters = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, boolean[]> compiledFaceFilterDirty = new EnumMap<>(Direction.class);
    private boolean itemsEnabled = true;
    private boolean fluidsEnabled = true;
    private boolean energyEnabled = true;
    private final NonNullList<ItemStack> upgrades = NonNullList.withSize(UPGRADE_SLOTS, ItemStack.EMPTY);
    private int itemCursor;
    private int fluidCursor;
    private int targetCursor;
    private long redstoneCacheTick = Long.MIN_VALUE;
    private boolean redstonePoweredCache;

    public SkyNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKY_NODE.get(), pos, state);
        for (Direction direction : Direction.values()) {
            faceModes.put(direction, NodeFaceMode.NONE);
            redstoneControls.put(direction, RedstoneControl.IGNORE);
            priorities.put(direction, 0);
            faceItemsEnabled.put(direction, true);
            faceFluidsEnabled.put(direction, true);
            faceEnergyEnabled.put(direction, true);
            faceFilters.put(direction, NonNullList.withSize(FACE_FILTER_SLOTS, ItemStack.EMPTY));
            FilterListItem.CompiledFilter[] compiled = new FilterListItem.CompiledFilter[FACE_FILTER_SLOTS];
            boolean[] dirty = new boolean[FACE_FILTER_SLOTS];
            for (int slot = 0; slot < FACE_FILTER_SLOTS; slot++) {
                compiled[slot] = FilterListItem.CompiledFilter.ALLOW_ALL;
                dirty[slot] = true;
            }
            compiledFaceFilters.put(direction, compiled);
            compiledFaceFilterDirty.put(direction, dirty);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            SkyNetworkRegistry.register(serverLevel, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            SkyNetworkRegistry.unregister(serverLevel, worldPosition);
        }
        super.setRemoved();
    }

    public UUID getLineId() {
        return lineId;
    }

    public NodeMode getMode() {
        return mode;
    }

    public boolean isItemsEnabled() {
        return itemsEnabled;
    }

    public boolean isItemsEnabled(Direction direction) {
        return faceItemsEnabled.getOrDefault(direction, itemsEnabled);
    }

    public boolean isFluidsEnabled() {
        return fluidsEnabled;
    }

    public boolean isFluidsEnabled(Direction direction) {
        return faceFluidsEnabled.getOrDefault(direction, fluidsEnabled);
    }

    public boolean isEnergyEnabled() {
        return energyEnabled;
    }

    public boolean isEnergyEnabled(Direction direction) {
        return faceEnergyEnabled.getOrDefault(direction, energyEnabled);
    }

    public int getOperationRate() {
        return hasSpeedUpgrade() ? 2 : 1;
    }

    public ItemStack getFilterList() {
        return getFaceFilter(getTargetDirection(), 0);
    }

    public boolean hasFilterList() {
        for (Direction direction : Direction.values()) {
            if (hasFaceFilter(direction)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowsItem(ItemStack stack) {
        return allowsItem(getTargetDirection(), stack);
    }

    public boolean allowsItem(Direction direction, ItemStack stack) {
        boolean hasWhitelist = false;
        boolean whitelistMatched = false;
        for (int slot = 0; slot < FACE_FILTER_SLOTS; slot++) {
            ItemStack filter = getFaceFilter(direction, slot);
            if (filter.isEmpty()) {
                continue;
            }
            FilterListItem.CompiledFilter compiled = compiledFaceFilter(direction, slot);
            if (!compiled.hasItemRules()) {
                continue;
            }
            if (compiled.whitelist()) {
                hasWhitelist = true;
                whitelistMatched |= compiled.matches(stack);
            } else if (!compiled.matches(stack)) {
                return false;
            }
        }
        return !hasWhitelist || whitelistMatched;
    }

    public boolean allowsFluid(Direction direction, FluidStack stack) {
        boolean hasWhitelist = false;
        boolean whitelistMatched = false;
        for (int slot = 0; slot < FACE_FILTER_SLOTS; slot++) {
            ItemStack filter = getFaceFilter(direction, slot);
            if (filter.isEmpty()) {
                continue;
            }
            FilterListItem.CompiledFilter compiled = compiledFaceFilter(direction, slot);
            if (!compiled.hasFluidRules()) {
                continue;
            }
            if (compiled.whitelist()) {
                hasWhitelist = true;
                whitelistMatched |= compiled.matchesFluid(stack);
            } else if (!compiled.matchesFluid(stack)) {
                return false;
            }
        }
        return !hasWhitelist || whitelistMatched;
    }

    public ItemStack getUpgrade(int slot) {
        if (slot < 0 || slot >= upgrades.size()) {
            return ItemStack.EMPTY;
        }
        return upgrades.get(slot);
    }

    public ItemStack getFaceFilter(Direction direction, int slot) {
        NonNullList<ItemStack> filters = faceFilters.get(direction);
        if (filters == null || slot < 0 || slot >= filters.size()) {
            return ItemStack.EMPTY;
        }
        return filters.get(slot);
    }

    public boolean hasFaceFilter(Direction direction) {
        NonNullList<ItemStack> filters = faceFilters.get(direction);
        if (filters == null) {
            return false;
        }
        for (ItemStack filter : filters) {
            if (!filter.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean canAcceptUpgrade(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (slot < 0 || slot >= upgrades.size() || !isUpgradeItem(stack)) {
            return false;
        }
        for (int i = 0; i < upgrades.size(); i++) {
            if (i != slot && !upgrades.get(i).isEmpty() && ItemStack.isSameItem(upgrades.get(i), stack)) {
                return false;
            }
        }
        return true;
    }

    public boolean canAcceptFaceFilter(int slot, ItemStack stack) {
        return stack.isEmpty() || (slot >= 0 && slot < FACE_FILTER_SLOTS && isFaceFilterItem(stack));
    }

    public void setUpgrade(int slot, ItemStack stack) {
        if (slot < 0 || slot >= upgrades.size()) {
            return;
        }
        if (!canAcceptUpgrade(slot, stack)) {
            stack = ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        if (!copy.isEmpty()) {
            copy.setCount(1);
        }
        ItemStack previous = upgrades.get(slot);
        if (ItemStack.isSameItemSameTags(previous, copy)) {
            return;
        }
        upgrades.set(slot, copy);
        markRuntimeChanged();
    }

    public void setFaceFilter(Direction direction, int slot, ItemStack stack) {
        if (!canAcceptFaceFilter(slot, stack)) {
            stack = ItemStack.EMPTY;
        }
        NonNullList<ItemStack> filters = faceFilters.get(direction);
        if (filters == null || slot < 0 || slot >= filters.size()) {
            return;
        }
        ItemStack copy = stack.copy();
        if (!copy.isEmpty()) {
            copy.setCount(1);
        }
        ItemStack previous = filters.get(slot);
        if (ItemStack.isSameItemSameTags(previous, copy)) {
            return;
        }
        filters.set(slot, copy);
        markFaceFilterDirty(direction, slot);
        markRuntimeChanged();
    }

    public boolean hasSpeedUpgrade() {
        return hasUpgrade(ModItems.SPEED_UPGRADE.get());
    }

    public boolean hasDimensionUpgrade() {
        return hasUpgrade(ModItems.DIMENSION_UPGRADE.get());
    }

    public boolean hasUpgrade(Item item) {
        for (ItemStack upgrade : upgrades) {
            if (!upgrade.isEmpty() && upgrade.is(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUpgradeItem(ItemStack stack) {
        return stack.is(ModItems.SPEED_UPGRADE.get())
                || stack.is(ModItems.DIMENSION_UPGRADE.get());
    }

    public static boolean isFaceFilterItem(ItemStack stack) {
        return stack.is(ModItems.FILTER_LIST.get());
    }

    public Direction getTargetDirection() {
        BlockState state = getBlockState();
        if (state.hasProperty(SkyNodeBlock.TARGET)) {
            return state.getValue(SkyNodeBlock.TARGET);
        }
        return Direction.NORTH;
    }

    public BlockPos getTargetPos() {
        return worldPosition.relative(getTargetDirection());
    }

    public BlockPos getTargetPos(Direction direction) {
        return worldPosition.relative(direction);
    }

    public Direction getAccessSide() {
        return getTargetDirection().getOpposite();
    }

    public Direction getAccessSide(Direction direction) {
        return direction.getOpposite();
    }

    public NodeFaceMode getFaceMode(Direction direction) {
        return faceModes.getOrDefault(direction, NodeFaceMode.NONE);
    }

    public RedstoneControl getRedstoneControl(Direction direction) {
        return redstoneControls.getOrDefault(direction, RedstoneControl.IGNORE);
    }

    public int getPriority(Direction direction) {
        return priorities.getOrDefault(direction, 0);
    }

    public boolean isFaceRedstoneAllowed(Direction direction) {
        RedstoneControl control = getRedstoneControl(direction);
        if (control == RedstoneControl.IGNORE) {
            return true;
        }
        if (control == RedstoneControl.DISABLED) {
            return false;
        }
        boolean powered = isPoweredCached();
        return control == RedstoneControl.HIGH ? powered : !powered;
    }

    public int getConnectedFaces() {
        int connected = 0;
        for (NodeFaceMode faceMode : faceModes.values()) {
            if (faceMode != NodeFaceMode.NONE) {
                connected++;
            }
        }
        return connected;
    }

    public int nextItemStart(int slots) {
        if (slots <= 0) {
            return 0;
        }
        int start = Math.floorMod(itemCursor, slots);
        itemCursor = (start + 1) % slots;
        return start;
    }

    public int nextFluidStart(int tanks) {
        if (tanks <= 0) {
            return 0;
        }
        int start = Math.floorMod(fluidCursor, tanks);
        fluidCursor = (start + 1) % tanks;
        return start;
    }

    public int nextTargetCursor() {
        int start = targetCursor;
        targetCursor = targetCursor == Integer.MAX_VALUE ? 0 : targetCursor + 1;
        return start;
    }

    public void applyPlacementToolConfig(ConfiguratorItem.ToolConfig config, boolean includeMode) {
        boolean topologyChanged = !lineId.equals(config.lineId());
        boolean runtimeChanged = false;
        boolean priorityChanged = false;
        lineId = config.lineId();
        Direction direction = getTargetDirection();
        ConfiguratorItem.FaceConfig face = config.placement();
        if (includeMode && getFaceMode(direction) != face.mode()) {
            faceModes.put(direction, face.mode());
            if (face.mode() == NodeFaceMode.INPUT) {
                mode = NodeMode.INPUT;
            } else if (face.mode() == NodeFaceMode.OUTPUT) {
                mode = NodeMode.OUTPUT;
            }
            topologyChanged = true;
        }
        if (isItemsEnabled(direction) != face.itemsEnabled()) {
            faceItemsEnabled.put(direction, face.itemsEnabled());
            runtimeChanged = true;
        }
        if (isFluidsEnabled(direction) != face.fluidsEnabled()) {
            faceFluidsEnabled.put(direction, face.fluidsEnabled());
            runtimeChanged = true;
        }
        if (isEnergyEnabled(direction) != face.energyEnabled()) {
            faceEnergyEnabled.put(direction, face.energyEnabled());
            runtimeChanged = true;
        }
        if (getRedstoneControl(direction) != face.redstoneControl()) {
            redstoneControls.put(direction, face.redstoneControl());
            runtimeChanged = true;
        }
        if (getPriority(direction) != face.priority()) {
            priorities.put(direction, face.priority());
            priorityChanged = true;
        }
        refreshGlobalToggles();
        markCompositeChanged(topologyChanged, priorityChanged, runtimeChanged);
    }

    public void applyCopiedToolConfig(ConfiguratorItem.ToolConfig config) {
        if (!config.hasCopiedFaces()) {
            applyPlacementToolConfig(config, true);
            return;
        }
        boolean topologyChanged = !lineId.equals(config.lineId());
        boolean runtimeChanged = false;
        boolean priorityChanged = false;
        lineId = config.lineId();
        for (Direction direction : Direction.values()) {
            ConfiguratorItem.FaceConfig face = config.face(direction);
            if (getFaceMode(direction) != face.mode()) {
                faceModes.put(direction, face.mode());
                topologyChanged = true;
            }
            if (isItemsEnabled(direction) != face.itemsEnabled()) {
                faceItemsEnabled.put(direction, face.itemsEnabled());
                runtimeChanged = true;
            }
            if (isFluidsEnabled(direction) != face.fluidsEnabled()) {
                faceFluidsEnabled.put(direction, face.fluidsEnabled());
                runtimeChanged = true;
            }
            if (isEnergyEnabled(direction) != face.energyEnabled()) {
                faceEnergyEnabled.put(direction, face.energyEnabled());
                runtimeChanged = true;
            }
            if (getRedstoneControl(direction) != face.redstoneControl()) {
                redstoneControls.put(direction, face.redstoneControl());
                runtimeChanged = true;
            }
            if (getPriority(direction) != face.priority()) {
                priorities.put(direction, face.priority());
                priorityChanged = true;
            }
        }
        mode = visualMode();
        refreshGlobalToggles();
        markCompositeChanged(topologyChanged, priorityChanged, runtimeChanged);
    }

    public ConfiguratorItem.ToolConfig toToolConfig() {
        return ConfiguratorItem.ToolConfig.fromNode(this);
    }

    public void cycleMode() {
        setMode(mode.next());
    }

    public void setLineId(UUID lineId) {
        if (this.lineId.equals(lineId)) {
            return;
        }
        this.lineId = lineId;
        markTopologyChanged();
    }

    public void setMode(NodeMode mode) {
        if (this.mode == mode) {
            return;
        }
        this.mode = mode;
        markTopologyChanged();
    }

    public void setItemsEnabled(boolean itemsEnabled) {
        if (this.itemsEnabled == itemsEnabled && allFacesMatch(faceItemsEnabled, itemsEnabled)) {
            return;
        }
        this.itemsEnabled = itemsEnabled;
        for (Direction direction : Direction.values()) {
            faceItemsEnabled.put(direction, itemsEnabled);
        }
        markRuntimeChanged();
    }

    public void setItemsEnabled(Direction direction, boolean itemsEnabled) {
        if (isItemsEnabled(direction) == itemsEnabled) {
            return;
        }
        faceItemsEnabled.put(direction, itemsEnabled);
        this.itemsEnabled = allFacesEnabled(faceItemsEnabled);
        markRuntimeChanged();
    }

    public void setFluidsEnabled(boolean fluidsEnabled) {
        if (this.fluidsEnabled == fluidsEnabled && allFacesMatch(faceFluidsEnabled, fluidsEnabled)) {
            return;
        }
        this.fluidsEnabled = fluidsEnabled;
        for (Direction direction : Direction.values()) {
            faceFluidsEnabled.put(direction, fluidsEnabled);
        }
        markRuntimeChanged();
    }

    public void setFluidsEnabled(Direction direction, boolean fluidsEnabled) {
        if (isFluidsEnabled(direction) == fluidsEnabled) {
            return;
        }
        faceFluidsEnabled.put(direction, fluidsEnabled);
        this.fluidsEnabled = allFacesEnabled(faceFluidsEnabled);
        markRuntimeChanged();
    }

    public void setEnergyEnabled(boolean energyEnabled) {
        if (this.energyEnabled == energyEnabled && allFacesMatch(faceEnergyEnabled, energyEnabled)) {
            return;
        }
        this.energyEnabled = energyEnabled;
        for (Direction direction : Direction.values()) {
            faceEnergyEnabled.put(direction, energyEnabled);
        }
        markRuntimeChanged();
    }

    public void setEnergyEnabled(Direction direction, boolean energyEnabled) {
        if (isEnergyEnabled(direction) == energyEnabled) {
            return;
        }
        faceEnergyEnabled.put(direction, energyEnabled);
        this.energyEnabled = allFacesEnabled(faceEnergyEnabled);
        markRuntimeChanged();
    }

    public void setOperationRate(int operationRate) {
        markRuntimeChanged();
    }

    public void setFilterList(ItemStack stack) {
        setFaceFilter(getTargetDirection(), 0, stack);
    }

    public void setFaceMode(Direction direction, NodeFaceMode faceMode) {
        if (getFaceMode(direction) == faceMode) {
            return;
        }
        faceModes.put(direction, faceMode);
        if (faceMode == NodeFaceMode.INPUT) {
            mode = NodeMode.INPUT;
        } else if (faceMode == NodeFaceMode.OUTPUT) {
            mode = NodeMode.OUTPUT;
        }
        markTopologyChanged();
    }

    public void cycleRedstoneControl(Direction direction) {
        redstoneControls.put(direction, getRedstoneControl(direction).next());
        markRuntimeChanged();
    }

    public void adjustPriority(Direction direction, int delta) {
        int priority = Math.max(-99, Math.min(99, getPriority(direction) + delta));
        if (priority == getPriority(direction)) {
            return;
        }
        priorities.put(direction, priority);
        markPriorityChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putUUID("LineId", lineId);
        tag.putString("Mode", mode.name());
        tag.putBoolean("ItemsEnabled", itemsEnabled);
        tag.putBoolean("FluidsEnabled", fluidsEnabled);
        tag.putBoolean("EnergyEnabled", energyEnabled);
        ListTag upgradeTags = new ListTag();
        for (int slot = 0; slot < upgrades.size(); slot++) {
            ItemStack upgrade = upgrades.get(slot);
            if (upgrade.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            entry.put("Stack", upgrade.save(new CompoundTag()));
            upgradeTags.add(entry);
        }
        if (!upgradeTags.isEmpty()) {
            tag.put("Upgrades", upgradeTags);
        }
        CompoundTag faces = new CompoundTag();
        CompoundTag faceSettings = new CompoundTag();
        for (Direction direction : Direction.values()) {
            faces.putString(direction.getSerializedName(), getFaceMode(direction).name());
            CompoundTag settings = new CompoundTag();
            settings.putString("Redstone", getRedstoneControl(direction).name());
            settings.putInt("Priority", getPriority(direction));
            settings.putBoolean("ItemsEnabled", isItemsEnabled(direction));
            settings.putBoolean("FluidsEnabled", isFluidsEnabled(direction));
            settings.putBoolean("EnergyEnabled", isEnergyEnabled(direction));
            ListTag filterTags = new ListTag();
            NonNullList<ItemStack> filters = faceFilters.get(direction);
            if (filters != null) {
                for (int slot = 0; slot < filters.size(); slot++) {
                    ItemStack filter = filters.get(slot);
                    if (filter.isEmpty()) {
                        continue;
                    }
                    CompoundTag entry = new CompoundTag();
                    entry.putInt("Slot", slot);
                    entry.put("Stack", filter.save(new CompoundTag()));
                    filterTags.add(entry);
                }
            }
            if (!filterTags.isEmpty()) {
                settings.put("Filters", filterTags);
            }
            faceSettings.put(direction.getSerializedName(), settings);
        }
        tag.put("Faces", faces);
        tag.put("FaceSettings", faceSettings);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("LineId")) {
            lineId = tag.getUUID("LineId");
        }
        mode = NodeMode.byName(tag.getString("Mode"));
        itemsEnabled = !tag.contains("ItemsEnabled") || tag.getBoolean("ItemsEnabled");
        fluidsEnabled = !tag.contains("FluidsEnabled") || tag.getBoolean("FluidsEnabled");
        energyEnabled = !tag.contains("EnergyEnabled") || tag.getBoolean("EnergyEnabled");
        for (int i = 0; i < upgrades.size(); i++) {
            upgrades.set(i, ItemStack.EMPTY);
        }
        resetFaceRuntimeDefaults();
        if (tag.contains("Upgrades", Tag.TAG_LIST)) {
            ListTag upgradeTags = tag.getList("Upgrades", Tag.TAG_COMPOUND);
            for (int i = 0; i < upgradeTags.size(); i++) {
                CompoundTag entry = upgradeTags.getCompound(i);
                int slot = entry.getInt("Slot");
                ItemStack stack = ItemStack.of(entry.getCompound("Stack"));
                if (canAcceptUpgrade(slot, stack)) {
                    ItemStack copy = stack.copy();
                    copy.setCount(1);
                    upgrades.set(slot, copy);
                }
            }
        }
        for (Direction direction : Direction.values()) {
            faceModes.put(direction, NodeFaceMode.NONE);
            redstoneControls.put(direction, RedstoneControl.IGNORE);
            priorities.put(direction, 0);
            faceItemsEnabled.put(direction, itemsEnabled);
            faceFluidsEnabled.put(direction, fluidsEnabled);
            faceEnergyEnabled.put(direction, energyEnabled);
        }
        if (tag.contains("Faces", Tag.TAG_COMPOUND)) {
            CompoundTag faces = tag.getCompound("Faces");
            for (Direction direction : Direction.values()) {
                faceModes.put(direction, NodeFaceMode.byName(faces.getString(direction.getSerializedName())));
            }
        } else {
            faceModes.put(getTargetDirection(), mode == NodeMode.INPUT ? NodeFaceMode.INPUT : NodeFaceMode.OUTPUT);
        }
        if (tag.contains("FaceSettings", Tag.TAG_COMPOUND)) {
            CompoundTag faceSettings = tag.getCompound("FaceSettings");
            for (Direction direction : Direction.values()) {
                if (!faceSettings.contains(direction.getSerializedName(), Tag.TAG_COMPOUND)) {
                    continue;
                }
                CompoundTag settings = faceSettings.getCompound(direction.getSerializedName());
                redstoneControls.put(direction, RedstoneControl.byName(settings.getString("Redstone")));
                priorities.put(direction, Math.max(-99, Math.min(99, settings.getInt("Priority"))));
                if (settings.contains("ItemsEnabled")) {
                    faceItemsEnabled.put(direction, settings.getBoolean("ItemsEnabled"));
                }
                if (settings.contains("FluidsEnabled")) {
                    faceFluidsEnabled.put(direction, settings.getBoolean("FluidsEnabled"));
                }
                if (settings.contains("EnergyEnabled")) {
                    faceEnergyEnabled.put(direction, settings.getBoolean("EnergyEnabled"));
                }
                if (settings.contains("Filters", Tag.TAG_LIST)) {
                    ListTag filterTags = settings.getList("Filters", Tag.TAG_COMPOUND);
                    for (int i = 0; i < filterTags.size(); i++) {
                        CompoundTag entry = filterTags.getCompound(i);
                        int slot = entry.getInt("Slot");
                        ItemStack filter = ItemStack.of(entry.getCompound("Stack"));
                        setFaceFilterDirect(direction, slot, filter);
                    }
                }
            }
        }
        itemsEnabled = allFacesEnabled(faceItemsEnabled);
        fluidsEnabled = allFacesEnabled(faceFluidsEnabled);
        energyEnabled = allFacesEnabled(faceEnergyEnabled);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void markTopologyChanged() {
        markChanged(ChangeKind.TOPOLOGY);
    }

    private void markRuntimeChanged() {
        markChanged(ChangeKind.RUNTIME);
    }

    private void markPriorityChanged() {
        markChanged(ChangeKind.PRIORITY);
    }

    private void markCompositeChanged(boolean topologyChanged, boolean priorityChanged, boolean runtimeChanged) {
        if (topologyChanged) {
            markTopologyChanged();
        } else if (priorityChanged) {
            markPriorityChanged();
        } else if (runtimeChanged) {
            markRuntimeChanged();
        }
    }

    private void markChanged(ChangeKind changeKind) {
        NodeMode visualMode = visualMode();
        if (level != null && getBlockState().hasProperty(SkyNodeBlock.MODE)
                && getBlockState().getValue(SkyNodeBlock.MODE) != visualMode) {
            level.setBlock(worldPosition, getBlockState().setValue(SkyNodeBlock.MODE, visualMode), 3);
        }
        setChanged();
        if (level != null) {
            if (level instanceof ServerLevel serverLevel) {
                switch (changeKind) {
                    case TOPOLOGY -> SkyNetworkRegistry.markTopologyDirty(serverLevel);
                    case PRIORITY -> SkyNetworkRegistry.markPriorityDirty(serverLevel, worldPosition);
                    case RUNTIME -> SkyNetworkRegistry.markRuntimeDirty(serverLevel, worldPosition);
                }
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private enum ChangeKind {
        TOPOLOGY,
        RUNTIME,
        PRIORITY
    }

    private NodeMode visualMode() {
        NodeFaceMode targetMode = getFaceMode(getTargetDirection());
        if (targetMode == NodeFaceMode.INPUT) {
            return NodeMode.INPUT;
        }
        if (targetMode == NodeFaceMode.OUTPUT) {
            return NodeMode.OUTPUT;
        }
        for (NodeFaceMode faceMode : faceModes.values()) {
            if (faceMode == NodeFaceMode.INPUT) {
                return NodeMode.INPUT;
            }
            if (faceMode == NodeFaceMode.OUTPUT) {
                return NodeMode.OUTPUT;
            }
        }
        return mode;
    }

    private FilterListItem.CompiledFilter compiledFaceFilter(Direction direction, int slot) {
        FilterListItem.CompiledFilter[] compiled = compiledFaceFilters.get(direction);
        boolean[] dirty = compiledFaceFilterDirty.get(direction);
        if (compiled == null || dirty == null || slot < 0 || slot >= FACE_FILTER_SLOTS) {
            return FilterListItem.CompiledFilter.ALLOW_ALL;
        }
        if (dirty[slot]) {
            compiled[slot] = FilterListItem.compile(getFaceFilter(direction, slot));
            dirty[slot] = false;
        }
        return compiled[slot];
    }

    private void markFaceFilterDirty(Direction direction, int slot) {
        boolean[] dirty = compiledFaceFilterDirty.get(direction);
        if (dirty != null && slot >= 0 && slot < dirty.length) {
            dirty[slot] = true;
        }
    }

    private void setFaceFilterDirect(Direction direction, int slot, ItemStack stack) {
        NonNullList<ItemStack> filters = faceFilters.get(direction);
        if (filters == null || slot < 0 || slot >= filters.size() || !canAcceptFaceFilter(slot, stack)) {
            return;
        }
        ItemStack copy = stack.copy();
        if (!copy.isEmpty()) {
            copy.setCount(1);
        }
        filters.set(slot, copy);
        markFaceFilterDirty(direction, slot);
    }

    private void resetFaceRuntimeDefaults() {
        for (Direction direction : Direction.values()) {
            NonNullList<ItemStack> filters = faceFilters.get(direction);
            if (filters != null) {
                for (int slot = 0; slot < filters.size(); slot++) {
                    filters.set(slot, ItemStack.EMPTY);
                    markFaceFilterDirty(direction, slot);
                }
            }
        }
    }

    private static boolean allFacesEnabled(EnumMap<Direction, Boolean> enabled) {
        for (Direction direction : Direction.values()) {
            if (!enabled.getOrDefault(direction, true)) {
                return false;
            }
        }
        return true;
    }

    private void refreshGlobalToggles() {
        itemsEnabled = allFacesEnabled(faceItemsEnabled);
        fluidsEnabled = allFacesEnabled(faceFluidsEnabled);
        energyEnabled = allFacesEnabled(faceEnergyEnabled);
    }

    private static boolean allFacesMatch(EnumMap<Direction, Boolean> enabled, boolean value) {
        for (Direction direction : Direction.values()) {
            if (enabled.getOrDefault(direction, value) != value) {
                return false;
            }
        }
        return true;
    }

    private boolean isPoweredCached() {
        if (level == null) {
            return false;
        }
        long gameTime = level.getGameTime();
        if (redstoneCacheTick != gameTime) {
            redstonePoweredCache = level.hasNeighborSignal(worldPosition);
            redstoneCacheTick = gameTime;
        }
        return redstonePoweredCache;
    }
}
