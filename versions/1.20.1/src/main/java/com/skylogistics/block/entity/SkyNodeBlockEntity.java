package com.skylogistics.block.entity;

import com.skylogistics.block.SkyNodeBlock;
import com.skylogistics.client.ClientLineNames;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.network.SkyLineNames;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.NodeMode;
import com.skylogistics.util.RedstoneControl;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

public class SkyNodeBlockEntity extends BlockEntity {
    public static final int UPGRADE_SLOTS = 2;
    public static final int FACE_FILTER_SLOTS = 1;
    private static final String LINE_ID_TAG = "LineId";
    private static final String LINES_TAG = "Lines";
    private static final String LINE_INDEX_TAG = "LineIndex";
    private static final String LINE_ENTRY_ID_TAG = "Id";
    private static final String LINE_NAME_TAG = "LineName";
    private static final String LINE_ENTRY_NAME_TAG = "Name";
    private static final String LINE_ENTRY_ASSIGNED_NAME_TAG = "AssignedName";
    private static final int MAX_LINE_NAME_LENGTH = 48;

    private String lineName = ConfiguratorItem.lineName("Line", 0);
    private UUID lineId = ConfiguratorItem.lineIdForName(lineName);
    private final List<UUID> lines = new ArrayList<>();
    private final List<String> lineNames = new ArrayList<>();
    private final List<String> lineAssignedNames = new ArrayList<>();
    private int lineIndex;
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
        lines.add(lineId);
        lineNames.add(lineName);
        lineAssignedNames.add(lineName);
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
        initializeModeFromState(state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateVisualState();
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

    public String getLineName() {
        ensureLineList();
        String fallback = localAssignedLineName();
        if (level != null && level.isClientSide) {
            return ClientLineNames.displayName(lineId, lineName);
        }
        if (level != null && level.getServer() != null) {
            return SkyLineNames.displayName(level.getServer(), lineId, fallback, lineName);
        }
        return lineName;
    }

    public String getAssignedLineName() {
        ensureLineList();
        String fallback = localAssignedLineName();
        if (level != null && level.isClientSide) {
            return ClientLineNames.assignedName(lineId, fallback);
        }
        if (level != null && level.getServer() != null) {
            return SkyLineNames.assignedName(level.getServer(), lineId, fallback);
        }
        return fallback;
    }

    public int getLineIndex() {
        ensureLineList();
        return lineIndex;
    }

    public int getLineCount() {
        ensureLineList();
        return lines.size();
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

    private void initializeModeFromState(BlockState state) {
        if (state.hasProperty(SkyNodeBlock.MODE)) {
            mode = state.getValue(SkyNodeBlock.MODE);
        }
        for (Direction direction : Direction.values()) {
            if (state.hasProperty(SkyNodeBlock.faceModeProperty(direction))) {
                faceModes.put(direction, state.getValue(SkyNodeBlock.faceModeProperty(direction)));
            }
        }
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
        boolean topologyChanged = assignLine(config.lineId(), config.lineName());
        boolean runtimeChanged = false;
        boolean priorityChanged = false;
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
            topologyChanged = true;
        }
        if (isFluidsEnabled(direction) != face.fluidsEnabled()) {
            faceFluidsEnabled.put(direction, face.fluidsEnabled());
            topologyChanged = true;
        }
        if (isEnergyEnabled(direction) != face.energyEnabled()) {
            faceEnergyEnabled.put(direction, face.energyEnabled());
            topologyChanged = true;
        }
        if (getRedstoneControl(direction) != face.redstoneControl()) {
            redstoneControls.put(direction, face.redstoneControl());
            runtimeChanged = true;
        }
        if (getPriority(direction) != face.priority()) {
            priorities.put(direction, face.priority());
            priorityChanged = true;
        }
        if (config.hasCopiedFaces() && applyFaceFilters(direction, face)) {
            runtimeChanged = true;
        }
        refreshGlobalToggles();
        markCompositeChanged(topologyChanged, priorityChanged, runtimeChanged);
    }

    public void applyCopiedToolConfig(ConfiguratorItem.ToolConfig config) {
        applyCopiedToolConfig(config, null);
    }

    public void applyCopiedToolConfig(ConfiguratorItem.ToolConfig config, Player player) {
        if (!config.hasCopiedFaces()) {
            applyPlacementToolConfig(config, true);
            installCopiedUpgrades(config, player);
            return;
        }
        boolean topologyChanged = assignLine(config.lineId(), config.lineName());
        boolean runtimeChanged = false;
        boolean priorityChanged = false;
        for (Direction direction : Direction.values()) {
            ConfiguratorItem.FaceConfig face = config.face(direction);
            if (getFaceMode(direction) != face.mode()) {
                faceModes.put(direction, face.mode());
                topologyChanged = true;
            }
            if (isItemsEnabled(direction) != face.itemsEnabled()) {
                faceItemsEnabled.put(direction, face.itemsEnabled());
                topologyChanged = true;
            }
            if (isFluidsEnabled(direction) != face.fluidsEnabled()) {
                faceFluidsEnabled.put(direction, face.fluidsEnabled());
                topologyChanged = true;
            }
            if (isEnergyEnabled(direction) != face.energyEnabled()) {
                faceEnergyEnabled.put(direction, face.energyEnabled());
                topologyChanged = true;
            }
            if (getRedstoneControl(direction) != face.redstoneControl()) {
                redstoneControls.put(direction, face.redstoneControl());
                runtimeChanged = true;
            }
            if (getPriority(direction) != face.priority()) {
                priorities.put(direction, face.priority());
                priorityChanged = true;
            }
            if (applyFaceFilters(direction, face)) {
                runtimeChanged = true;
            }
        }
        mode = visualMode();
        refreshGlobalToggles();
        markCompositeChanged(topologyChanged, priorityChanged, runtimeChanged);
        installCopiedUpgrades(config, player);
    }

    private void installCopiedUpgrades(ConfiguratorItem.ToolConfig config, Player player) {
        installCopiedUpgrade(config.speedUpgrade(), ModItems.SPEED_UPGRADE.get(), player);
        installCopiedUpgrade(config.dimensionUpgrade(), ModItems.DIMENSION_UPGRADE.get(), player);
    }

    private void installCopiedUpgrade(boolean shouldInstall, Item item, Player player) {
        if (!shouldInstall || hasUpgrade(item)) {
            return;
        }
        int slot = firstUpgradeSlotFor(item);
        if (slot < 0 || !consumeUpgradeFromPlayer(player, item)) {
            return;
        }
        setUpgrade(slot, new ItemStack(item));
    }

    private int firstUpgradeSlotFor(Item item) {
        ItemStack stack = new ItemStack(item);
        for (int slot = 0; slot < upgrades.size(); slot++) {
            if (canAcceptUpgrade(slot, stack)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean consumeUpgradeFromPlayer(Player player, Item item) {
        if (player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack container = inventory.getItem(slot);
            if (!container.isEmpty() && !container.is(item) && consumeUpgradeFromItemHandler(container, item)) {
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    private static boolean consumeUpgradeFromItemHandler(ItemStack container, Item item) {
        return container.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                .map(handler -> consumeUpgradeFromItemHandler(handler, item))
                .orElse(false);
    }

    private static boolean consumeUpgradeFromItemHandler(IItemHandler handler, Item item) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack simulated = handler.extractItem(slot, 1, true);
            if (simulated.is(item)) {
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (extracted.is(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean applyFaceFilters(Direction direction, ConfiguratorItem.FaceConfig face) {
        NonNullList<ItemStack> filters = faceFilters.get(direction);
        if (filters == null) {
            return false;
        }
        boolean changed = false;
        for (int slot = 0; slot < FACE_FILTER_SLOTS; slot++) {
            ItemStack stack = face.filter(slot);
            if (!canAcceptFaceFilter(slot, stack)) {
                stack = ItemStack.EMPTY;
            }
            ItemStack copy = stack.copy();
            if (!copy.isEmpty()) {
                copy.setCount(1);
            }
            ItemStack previous = filters.get(slot);
            if (ItemStack.isSameItemSameTags(previous, copy)) {
                continue;
            }
            filters.set(slot, copy);
            markFaceFilterDirty(direction, slot);
            changed = true;
        }
        return changed;
    }

    public ConfiguratorItem.ToolConfig toToolConfig() {
        return ConfiguratorItem.ToolConfig.fromNode(this);
    }

    public void cycleMode() {
        setMode(mode.next());
    }

    public void setLineId(UUID lineId) {
        if (!assignLineId(lineId)) {
            return;
        }
        markTopologyChanged();
    }

    private void setLine(UUID lineId, String lineName) {
        if (!assignLine(lineId, lineName)) {
            return;
        }
        markTopologyChanged();
    }

    public void claimDefaultLineName(Player player) {
        ensureLineList();
        if (!lineName.startsWith("Line-")) {
            return;
        }
        String newLineName = nextLineName(ConfiguratorItem.linePrefix(player), lineIndex);
        UUID newLineId = ConfiguratorItem.lineIdForName(newLineName);
        int currentIndex = Math.max(0, Math.min(lineIndex, lines.size() - 1));
        boolean changed = !lineId.equals(newLineId)
                || !lineName.equals(newLineName)
                || !lines.get(currentIndex).equals(newLineId)
                || !lineNames.get(currentIndex).equals(newLineName)
                || !lineAssignedNames.get(currentIndex).equals(newLineName);
        lines.set(currentIndex, newLineId);
        lineNames.set(currentIndex, newLineName);
        lineAssignedNames.set(currentIndex, newLineName);
        lineId = newLineId;
        lineName = newLineName;
        lineIndex = currentIndex;
        if (changed) {
            markTopologyChanged();
        }
    }

    public void selectFirstLine() {
        selectLine(0);
    }

    public void selectPreviousLine() {
        ensureLineList();
        selectLine(Math.max(0, lineIndex - 1));
    }

    public void selectNextOrCreateLine(Player player) {
        ensureLineList();
        if (lineIndex < lines.size() - 1) {
            selectLine(lineIndex + 1);
            return;
        }
        String newLineName = nextLineName(ConfiguratorItem.linePrefix(player), -1);
        UUID newLineId = ConfiguratorItem.lineIdForName(newLineName);
        lines.add(newLineId);
        lineNames.add(newLineName);
        lineAssignedNames.add(newLineName);
        selectLine(lines.size() - 1);
    }

    public void selectLastLine() {
        ensureLineList();
        selectLine(lines.size() - 1);
    }

    public void removeCurrentLine(Player player) {
        ensureLineList();
        if (lines.size() <= 1) {
            lines.clear();
            lineNames.clear();
            lineAssignedNames.clear();
            String newLineName = nextLineName(ConfiguratorItem.linePrefix(player), -1);
            lines.add(ConfiguratorItem.lineIdForName(newLineName));
            lineNames.add(newLineName);
            lineAssignedNames.add(newLineName);
            selectLine(0);
            return;
        }
        lines.remove(lineIndex);
        lineNames.remove(lineIndex);
        lineAssignedNames.remove(lineIndex);
        selectLine(Math.min(lineIndex, lines.size() - 1));
    }

    public void lineNameChanged(UUID targetLineId) {
        ensureLineList();
        if (lines.contains(targetLineId)) {
            markLineNameChanged();
        }
    }

    private void selectLine(int index) {
        if (lines.isEmpty()) {
            lines.add(lineId);
            lineNames.add(lineName);
            lineAssignedNames.add(lineName);
            lineIndex = 0;
        }
        normalizeLineNames();
        int clamped = Math.max(0, Math.min(index, lines.size() - 1));
        setLine(lines.get(clamped), lineNames.get(clamped));
    }

    private boolean assignLineId(UUID newLineId) {
        return assignLine(newLineId, null);
    }

    private boolean assignLine(UUID newLineId, String newLineName) {
        if (lines.isEmpty()) {
            lines.add(lineId);
            lineNames.add(lineName);
            lineAssignedNames.add(lineName);
            lineIndex = 0;
        }
        normalizeLineNames();
        int index = lines.indexOf(newLineId);
        if (index < 0) {
            lines.add(newLineId);
            String assignedName = validLineName(newLineName, fallbackLineName(lines.size() - 1));
            lineNames.add(assignedName);
            lineAssignedNames.add(assignedName);
            index = lines.size() - 1;
        } else if (newLineName != null && !newLineName.isBlank() && !lineNames.get(index).equals(newLineName)) {
            lineNames.set(index, validLineName(newLineName, lineAssignedNames.get(index)));
        }
        String selectedName = lineNames.get(index);
        boolean changed = !lineId.equals(newLineId) || lineIndex != index || !lineName.equals(selectedName);
        lineId = newLineId;
        lineName = selectedName;
        lineIndex = index;
        return changed;
    }

    private void ensureLineList() {
        normalizeLineNames();
        if (lines.isEmpty()) {
            lines.add(lineId);
            lineNames.add(lineName);
            lineAssignedNames.add(lineName);
            lineIndex = 0;
            return;
        }
        int active = lines.indexOf(lineId);
        if (active >= 0) {
            lineIndex = active;
            lineName = lineNames.get(active);
            return;
        }
        lineIndex = Math.max(0, Math.min(lineIndex, lines.size() - 1));
        lineId = lines.get(lineIndex);
        lineName = lineNames.get(lineIndex);
    }

    private String localAssignedLineName() {
        ensureLineList();
        return lineAssignedNames.get(Math.max(0, Math.min(lineIndex, lineAssignedNames.size() - 1)));
    }

    private void normalizeLineNames() {
        while (lineNames.size() < lines.size()) {
            lineNames.add(fallbackLineName(lineNames.size()));
        }
        while (lineAssignedNames.size() < lines.size()) {
            lineAssignedNames.add(lineNames.get(lineAssignedNames.size()));
        }
        while (lineNames.size() > lines.size()) {
            lineNames.remove(lineNames.size() - 1);
        }
        while (lineAssignedNames.size() > lines.size()) {
            lineAssignedNames.remove(lineAssignedNames.size() - 1);
        }
        for (int i = 0; i < lineNames.size(); i++) {
            lineAssignedNames.set(i, validLineName(lineAssignedNames.get(i), fallbackLineName(i)));
            lineNames.set(i, validLineName(lineNames.get(i), lineAssignedNames.get(i)));
        }
    }

    private String nextLineName(String prefix, int replacingIndex) {
        String marker = ConfiguratorItem.lineName(prefix, 0);
        marker = marker.substring(0, marker.lastIndexOf('-') + 1);
        int next = 0;
        for (int i = 0; i < lineNames.size(); i++) {
            if (i == replacingIndex) {
                continue;
            }
            String name = lineAssignedNames.get(i);
            if (!name.startsWith(marker)) {
                continue;
            }
            try {
                next = Math.max(next, Integer.parseInt(name.substring(marker.length())) + 1);
            } catch (NumberFormatException ignored) {
                next++;
            }
        }
        return ConfiguratorItem.lineName(prefix, next);
    }

    private static String validLineName(String name, String fallback) {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (clean.isBlank()) {
            clean = fallback == null ? "" : fallback.trim().replaceAll("\\s+", " ");
        }
        return clean.length() > MAX_LINE_NAME_LENGTH ? clean.substring(0, MAX_LINE_NAME_LENGTH) : clean;
    }

    private static String fallbackLineName(int index) {
        return ConfiguratorItem.lineName("Line", index);
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
        markTopologyChanged();
    }

    public void setItemsEnabled(Direction direction, boolean itemsEnabled) {
        if (isItemsEnabled(direction) == itemsEnabled) {
            return;
        }
        faceItemsEnabled.put(direction, itemsEnabled);
        this.itemsEnabled = allFacesEnabled(faceItemsEnabled);
        markTopologyChanged();
    }

    public void setFluidsEnabled(boolean fluidsEnabled) {
        if (this.fluidsEnabled == fluidsEnabled && allFacesMatch(faceFluidsEnabled, fluidsEnabled)) {
            return;
        }
        this.fluidsEnabled = fluidsEnabled;
        for (Direction direction : Direction.values()) {
            faceFluidsEnabled.put(direction, fluidsEnabled);
        }
        markTopologyChanged();
    }

    public void setFluidsEnabled(Direction direction, boolean fluidsEnabled) {
        if (isFluidsEnabled(direction) == fluidsEnabled) {
            return;
        }
        faceFluidsEnabled.put(direction, fluidsEnabled);
        this.fluidsEnabled = allFacesEnabled(faceFluidsEnabled);
        markTopologyChanged();
    }

    public void setEnergyEnabled(boolean energyEnabled) {
        if (this.energyEnabled == energyEnabled && allFacesMatch(faceEnergyEnabled, energyEnabled)) {
            return;
        }
        this.energyEnabled = energyEnabled;
        for (Direction direction : Direction.values()) {
            faceEnergyEnabled.put(direction, energyEnabled);
        }
        markTopologyChanged();
    }

    public void setEnergyEnabled(Direction direction, boolean energyEnabled) {
        if (isEnergyEnabled(direction) == energyEnabled) {
            return;
        }
        faceEnergyEnabled.put(direction, energyEnabled);
        this.energyEnabled = allFacesEnabled(faceEnergyEnabled);
        markTopologyChanged();
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
        ensureLineList();
        tag.putUUID(LINE_ID_TAG, lineId);
        tag.putString(LINE_NAME_TAG, lineName);
        tag.putInt(LINE_INDEX_TAG, lineIndex);
        ListTag lineTags = new ListTag();
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(LINE_ENTRY_ID_TAG, lines.get(i));
            String assignedName = validLineName(lineAssignedNames.get(i), fallbackLineName(i));
            entry.putString(LINE_ENTRY_NAME_TAG, validLineName(lineNames.get(i), assignedName));
            entry.putString(LINE_ENTRY_ASSIGNED_NAME_TAG, assignedName);
            lineTags.add(entry);
        }
        tag.put(LINES_TAG, lineTags);
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
        if (tag.hasUUID(LINE_ID_TAG)) {
            lineId = tag.getUUID(LINE_ID_TAG);
        }
        if (tag.contains(LINE_NAME_TAG, Tag.TAG_STRING)) {
            lineName = validLineName(tag.getString(LINE_NAME_TAG), fallbackLineName(0));
        }
        lines.clear();
        lineNames.clear();
        lineAssignedNames.clear();
        if (tag.contains(LINES_TAG, Tag.TAG_LIST)) {
            ListTag lineTags = tag.getList(LINES_TAG, Tag.TAG_COMPOUND);
            for (int i = 0; i < lineTags.size(); i++) {
                CompoundTag entry = lineTags.getCompound(i);
                if (entry.hasUUID(LINE_ENTRY_ID_TAG)) {
                    UUID savedLineId = entry.getUUID(LINE_ENTRY_ID_TAG);
                    if (!lines.contains(savedLineId)) {
                        lines.add(savedLineId);
                        String savedAssignedName = entry.contains(LINE_ENTRY_ASSIGNED_NAME_TAG, Tag.TAG_STRING)
                                ? entry.getString(LINE_ENTRY_ASSIGNED_NAME_TAG)
                                : (entry.contains(LINE_ENTRY_NAME_TAG, Tag.TAG_STRING)
                                        ? entry.getString(LINE_ENTRY_NAME_TAG)
                                        : fallbackLineName(lineAssignedNames.size()));
                        savedAssignedName = validLineName(savedAssignedName, fallbackLineName(lineAssignedNames.size()));
                        String savedLineName = entry.contains(LINE_ENTRY_NAME_TAG, Tag.TAG_STRING)
                                ? entry.getString(LINE_ENTRY_NAME_TAG)
                                : savedAssignedName;
                        lineNames.add(validLineName(savedLineName, savedAssignedName));
                        lineAssignedNames.add(savedAssignedName);
                    }
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add(lineId);
            lineNames.add(lineName);
            lineAssignedNames.add(lineName);
        }
        normalizeLineNames();
        lineIndex = tag.contains(LINE_INDEX_TAG) ? tag.getInt(LINE_INDEX_TAG) : 0;
        if (!tag.hasUUID(LINE_ID_TAG)) {
            lineIndex = Math.max(0, Math.min(lineIndex, lines.size() - 1));
            lineId = lines.get(lineIndex);
            lineName = lineNames.get(lineIndex);
        }
        int activeLine = lines.indexOf(lineId);
        if (activeLine < 0) {
            lines.add(lineId);
            lineNames.add(lineName);
            lineAssignedNames.add(lineName);
            activeLine = lines.size() - 1;
        }
        lineIndex = Math.max(0, Math.min(activeLine, lines.size() - 1));
        lineId = lines.get(lineIndex);
        lineName = lineNames.get(lineIndex);
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
                NodeFaceMode faceMode = NodeFaceMode.byName(faces.getString(direction.getSerializedName()));
                faceModes.put(direction, faceMode);
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

    private void markLineNameChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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
        updateVisualState();
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

    private void updateVisualState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState current = getBlockState();
        BlockState updated = current;
        if (updated.hasProperty(SkyNodeBlock.MODE)) {
            updated = updated.setValue(SkyNodeBlock.MODE, visualMode());
        }
        for (Direction direction : Direction.values()) {
            NodeFaceMode faceMode = getFaceMode(direction);
            if (updated.hasProperty(SkyNodeBlock.connectedProperty(direction))) {
                updated = updated.setValue(SkyNodeBlock.connectedProperty(direction),
                        faceMode != NodeFaceMode.NONE);
            }
            if (updated.hasProperty(SkyNodeBlock.faceModeProperty(direction))) {
                updated = updated.setValue(SkyNodeBlock.faceModeProperty(direction), faceMode);
            }
        }
        if (!updated.equals(current)) {
            level.setBlock(worldPosition, updated, 3);
        }
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
