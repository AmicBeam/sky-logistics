package com.skylogistics.item;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.RedstoneControl;
import com.skylogistics.util.StackData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ConfiguratorItem extends Item {
    private static final String LINE_ID = "LineId";
    private static final String ITEMS = "Items";
    private static final String FLUIDS = "Fluids";
    private static final String ENERGY = "Energy";
    private static final String AUTO_RESOURCES = "AutoResources";
    private static final String PASTE_MODE = "PasteMode";
    private static final String PLACEMENT = "Placement";
    private static final String FACES = "Faces";
    private static final String MODE = "Mode";
    private static final String REDSTONE = "Redstone";
    private static final String PRIORITY = "Priority";
    private static final String FILTERS = "Filters";
    private static final String SLOT = "Slot";
    private static final String STACK = "Stack";
    private static final String SPEED_UPGRADE = "SpeedUpgrade";
    private static final String DIMENSION_UPGRADE = "DimensionUpgrade";
    private static final String LINES = "Lines";
    private static final String LINE_BINDINGS = "LineBindings";
    private static final String LINE_INDEX = "LineIndex";
    private static final String LINE_ENTRY_ID = "Id";
    private static final String LINE_NAME = "LineName";
    private static final String LINE_ENTRY_NAME = "Name";
    private static final String LINE_ENTRY_ASSIGNED_NAME = "AssignedName";
    private static final String FALLBACK_LINE_PREFIX = "Line";
    private static final int MAX_LINE_NAME_LENGTH = 48;

    public ConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        BlockEntity blockEntity = level.getBlockEntity(context.getClickedPos());
        if (!(blockEntity instanceof SkyNodeBlockEntity node)) {
            if (player != null && player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    setPasteMode(stack, false);
                    player.displayClientMessage(Component.translatable("message.skylogistics.configurator.paste_off"), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }
        return useOnNode(level, context.getClickedPos(), node, player, context.getHand(), stack);
    }

    public static InteractionResult useOnNode(Level level, net.minecraft.core.BlockPos pos, SkyNodeBlockEntity node,
            Player player, InteractionHand hand, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        node.claimDefaultLineName(player);

        if (player != null && player.isShiftKeyDown()) {
            writeConfig(stack, ToolConfig.fromNode(node), node.getAssignedLineName());
            setPasteMode(stack, true);
            player.displayClientMessage(Component.translatable("message.skylogistics.configurator.copied_paste",
                    node.getLineName()), true);
            return InteractionResult.CONSUME;
        }

        if (isPasteMode(stack)) {
            node.applyCopiedToolConfig(readOrCreate(stack, player), player);
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.skylogistics.configurator.pasted",
                        node.getLineName()), true);
            }
            return InteractionResult.CONSUME;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, ignored) -> new com.skylogistics.menu.SkyNodeMenu(id, inventory,
                            pos, false, hand), Component.translatable("menu.skylogistics.sky_node")),
                    buffer -> {
                        buffer.writeBlockPos(pos);
                        buffer.writeBoolean(false);
                        buffer.writeEnum(hand);
                    });
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            setPasteMode(stack, false);
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.translatable("message.skylogistics.configurator.paste_off"), true);
                return InteractionResultHolder.consume(stack);
            }
            readOrCreate(stack, player);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(
                        new SimpleMenuProvider((id, inventory, ignored) -> new ConfiguratorMenu(id, inventory, hand),
                                Component.translatable("menu.skylogistics.configurator")),
                        buffer -> buffer.writeEnum(hand));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player && isPasteMode(stack)
                && player.getMainHandItem() != stack && player.getOffhandItem() != stack) {
            setPasteMode(stack, false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ToolConfig config = read(stack);
        if (config == null) {
            tooltip.add(Component.translatable("tooltip.skylogistics.configurator.unbound").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.skylogistics.configurator.line", config.lineName())
                .withStyle(ChatFormatting.AQUA));
        if (config.autoDetectResources()) {
            tooltip.add(Component.translatable("tooltip.skylogistics.configurator.types_auto")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.skylogistics.configurator.types",
                    config.itemsEnabled(), config.fluidsEnabled(), config.energyEnabled()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.skylogistics.configurator.face",
                Component.translatable(config.placement().redstoneControl().translationKey()),
                config.placement().priority()).withStyle(ChatFormatting.GRAY));
        if (isPasteMode(stack)) {
            tooltip.add(Component.translatable("tooltip.skylogistics.configurator.paste_mode").withStyle(ChatFormatting.GOLD));
        }
    }

    public static ToolConfig readOrCreate(ItemStack stack) {
        ToolConfig config = read(stack);
        if (config != null) {
            rememberKnownLineBindings(stack);
            return config;
        }
        config = ToolConfig.createDefault(createLine(StackData.getOrEmpty(stack), FALLBACK_LINE_PREFIX, List.of()));
        writeConfig(stack, config);
        return config;
    }

    public static ToolConfig readOrCreate(ItemStack stack, Player player) {
        String prefix = linePrefix(player);
        ToolConfig config = read(stack);
        if (config != null) {
            if (ensureLineNames(stack, prefix)) {
                config = read(stack);
            }
            rememberKnownLineBindings(stack);
            return config;
        }
        config = ToolConfig.createDefault(createLine(StackData.getOrEmpty(stack), prefix, List.of()));
        writeConfig(stack, config);
        return config;
    }

    public static ToolConfig read(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        if (tag == null || !tag.hasUUID(LINE_ID)) {
            return null;
        }
        FaceConfig placement = tag.contains(PLACEMENT, Tag.TAG_COMPOUND)
                ? FaceConfig.load(tag.getCompound(PLACEMENT), FaceConfig.PLACEMENT_DEFAULT)
                : FaceConfig.PLACEMENT_DEFAULT;
        boolean copiedFaces = tag.contains(FACES, Tag.TAG_COMPOUND);
        EnumMap<Direction, FaceConfig> faces = defaultFaces();
        if (tag.contains(FACES, Tag.TAG_COMPOUND)) {
            CompoundTag faceTag = tag.getCompound(FACES);
            for (Direction direction : Direction.values()) {
                String key = direction.getSerializedName();
                if (faceTag.contains(key, Tag.TAG_COMPOUND)) {
                    faces.put(direction, FaceConfig.load(faceTag.getCompound(key), FaceConfig.DEFAULT));
                }
            }
        }
        List<LineEntry> lines = readLineEntries(tag);
        UUID activeLineId = tag.getUUID(LINE_ID);
        String activeLineName = tag.contains(LINE_NAME, Tag.TAG_STRING)
                ? tag.getString(LINE_NAME)
                : lineName(lines, activeLineId);
        return new ToolConfig(activeLineId, validLineName(activeLineName, lineName(lines, activeLineId)),
                placement, faces, copiedFaces, tag.getBoolean(SPEED_UPGRADE), tag.getBoolean(DIMENSION_UPGRADE));
    }

    public static UUID readLineId(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return tag != null && tag.hasUUID(LINE_ID) ? tag.getUUID(LINE_ID) : null;
    }

    public static void writeConfig(ItemStack stack, ToolConfig config) {
        writeConfig(stack, config, null);
    }

    public static void writeConfig(ItemStack stack, ToolConfig config, String assignedLineName) {
        CompoundTag tag = StackData.getOrEmpty(stack);
        tag.putUUID(LINE_ID, config.lineId());
        tag.putString(LINE_NAME, config.lineName());
        tag.put(PLACEMENT, config.placement().save());
        if (config.hasCopiedFaces()) {
            CompoundTag faces = new CompoundTag();
            for (Direction direction : Direction.values()) {
                faces.put(direction.getSerializedName(), config.face(direction).save());
            }
            tag.put(FACES, faces);
        } else {
            tag.remove(FACES);
        }
        tag.putBoolean(SPEED_UPGRADE, config.speedUpgrade());
        tag.putBoolean(DIMENSION_UPGRADE, config.dimensionUpgrade());
        ensureLineList(tag, config.lineId(), config.lineName(), assignedLineName);
        StackData.set(stack, tag);
    }

    public static ToolConfig selectFirstLine(ItemStack stack) {
        return selectLine(stack, 0);
    }

    public static ToolConfig selectPreviousLine(ItemStack stack) {
        CompoundTag tag = StackData.getOrEmpty(stack);
        List<LineEntry> lines = readLineEntries(tag);
        int index = Math.max(0, currentLineIndex(tag, lines) - 1);
        return selectLine(stack, index);
    }

    public static ToolConfig selectNextOrCreateLine(ItemStack stack) {
        return selectNextOrCreateLine(stack, null);
    }

    public static ToolConfig selectNextOrCreateLine(ItemStack stack, Player player) {
        ToolConfig config = readOrCreate(stack, player);
        CompoundTag tag = StackData.getOrEmpty(stack);
        List<LineEntry> lines = readLineEntries(tag);
        rememberLineBindings(tag, lines);
        int index = currentLineIndex(tag, lines);
        if (index < lines.size() - 1) {
            return selectLine(stack, index + 1);
        }
        LineEntry line = createLine(tag, linePrefix(player), lines);
        int existingIndex = indexOfLine(lines, line.id());
        if (existingIndex >= 0) {
            return selectLine(stack, existingIndex);
        }
        lines.add(line);
        writeLineList(tag, lines, lines.size() - 1);
        StackData.set(stack, tag);
        config = config.withLine(line.id(), line.name());
        writeConfig(stack, config);
        return config;
    }

    public static ToolConfig selectLastLine(ItemStack stack) {
        CompoundTag tag = StackData.getOrEmpty(stack);
        List<LineEntry> lines = readLineEntries(tag);
        return selectLine(stack, Math.max(0, lines.size() - 1));
    }

    public static ToolConfig removeCurrentLine(ItemStack stack) {
        return removeCurrentLine(stack, null);
    }

    public static ToolConfig removeCurrentLine(ItemStack stack, Player player) {
        ToolConfig config = readOrCreate(stack, player);
        CompoundTag tag = StackData.getOrEmpty(stack);
        List<LineEntry> lines = readLineEntries(tag);
        rememberLineBindings(tag, lines);
        int index = currentLineIndex(tag, lines);
        if (lines.size() <= 1) {
            LineEntry line = createLine(tag, linePrefix(player), List.of());
            lines.clear();
            lines.add(line);
            writeLineList(tag, lines, 0);
            StackData.set(stack, tag);
            config = config.withLine(line.id(), line.name());
            writeConfig(stack, config);
            return config;
        }
        lines.remove(index);
        int nextIndex = Math.min(index, lines.size() - 1);
        writeLineList(tag, lines, nextIndex);
        StackData.set(stack, tag);
        LineEntry nextLine = lines.get(nextIndex);
        config = config.withLine(nextLine.id(), nextLine.name());
        writeConfig(stack, config);
        return config;
    }

    public static String assignedLineName(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        if (tag == null) {
            return fallbackLineName(0);
        }
        List<LineEntry> lines = readLineEntries(tag);
        if (lines.isEmpty()) {
            return fallbackLineName(0);
        }
        return lines.get(currentLineIndex(tag, lines)).assignedName();
    }

    public static int lineIndex(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        if (tag == null) {
            return 0;
        }
        return currentLineIndex(tag, readLineEntries(tag));
    }

    public static int lineCount(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return tag == null ? 0 : readLineEntries(tag).size();
    }

    public static boolean isPasteMode(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return tag != null && tag.getBoolean(PASTE_MODE);
    }

    public static void setPasteMode(ItemStack stack, boolean enabled) {
        if (enabled) {
            StackData.update(stack, tag -> tag.putBoolean(PASTE_MODE, true));
        } else if (StackData.has(stack)) {
            StackData.remove(stack, PASTE_MODE);
        }
    }

    public static String linePrefix(Player player) {
        if (player == null || player.getGameProfile() == null || player.getGameProfile().getName() == null
                || player.getGameProfile().getName().isBlank()) {
            return FALLBACK_LINE_PREFIX;
        }
        return cleanLinePrefix(player.getGameProfile().getName());
    }

    public static String lineName(String prefix, int index) {
        return cleanLinePrefix(prefix) + "-" + Math.max(0, index);
    }

    public static UUID lineIdForName(String lineName) {
        String normalized = validLineName(lineName == null ? "" : lineName.trim(), fallbackLineName(0));
        return UUID.nameUUIDFromBytes(("skylogistics:line:" + normalized).getBytes(StandardCharsets.UTF_8));
    }

    private static EnumMap<Direction, FaceConfig> defaultFaces() {
        EnumMap<Direction, FaceConfig> faces = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            faces.put(direction, FaceConfig.DEFAULT);
        }
        return faces;
    }

    private static List<ItemStack> emptyFaceFilters() {
        ArrayList<ItemStack> filters = new ArrayList<>(SkyNodeBlockEntity.FACE_FILTER_SLOTS);
        for (int slot = 0; slot < SkyNodeBlockEntity.FACE_FILTER_SLOTS; slot++) {
            filters.add(ItemStack.EMPTY);
        }
        return filters;
    }

    private static List<ItemStack> copyFaceFilters(List<ItemStack> filters) {
        ArrayList<ItemStack> copies = new ArrayList<>(SkyNodeBlockEntity.FACE_FILTER_SLOTS);
        for (int slot = 0; slot < SkyNodeBlockEntity.FACE_FILTER_SLOTS; slot++) {
            ItemStack stack = filters != null && slot < filters.size() ? filters.get(slot) : ItemStack.EMPTY;
            ItemStack copy = stack.copy();
            if (!copy.isEmpty()) {
                copy.setCount(1);
            }
            copies.add(copy);
        }
        return copies;
    }

    private static ToolConfig selectLine(ItemStack stack, int index) {
        ToolConfig config = readOrCreate(stack);
        CompoundTag tag = StackData.getOrEmpty(stack);
        List<LineEntry> lines = readLineEntries(tag);
        int clamped = Math.max(0, Math.min(index, lines.size() - 1));
        writeLineList(tag, lines, clamped);
        StackData.set(stack, tag);
        LineEntry line = lines.get(clamped);
        config = config.withLine(line.id(), line.name());
        writeConfig(stack, config);
        return config;
    }

    private static void ensureLineList(CompoundTag tag, UUID lineId, String lineName, String assignedLineName) {
        List<LineEntry> lines = readLineEntries(tag);
        int index = indexOfLine(lines, lineId);
        String existingAssignedName = index >= 0 ? lines.get(index).assignedName() : "";
        String assignedName = validLineName(assignedLineName, existingAssignedName);
        assignedName = validLineName(assignedName, validLineName(lineName, fallbackLineName(lines.size())));
        String displayName = validLineName(lineName, assignedName);
        rememberLineBindings(tag, lines);
        writeLineList(tag, List.of(new LineEntry(lineId, displayName, assignedName)), 0);
    }

    private static List<LineEntry> readLineEntries(CompoundTag tag) {
        ArrayList<LineEntry> lines = new ArrayList<>();
        if (tag.contains(LINES, Tag.TAG_LIST)) {
            ListTag list = tag.getList(LINES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (entry.hasUUID(LINE_ENTRY_ID)) {
                    UUID lineId = entry.getUUID(LINE_ENTRY_ID);
                    if (indexOfLine(lines, lineId) < 0) {
                        String assignedName = entry.contains(LINE_ENTRY_ASSIGNED_NAME, Tag.TAG_STRING)
                                ? entry.getString(LINE_ENTRY_ASSIGNED_NAME)
                                : (entry.contains(LINE_ENTRY_NAME, Tag.TAG_STRING)
                                        ? entry.getString(LINE_ENTRY_NAME)
                                        : fallbackLineName(lines.size()));
                        assignedName = validLineName(assignedName, fallbackLineName(lines.size()));
                        String name = entry.contains(LINE_ENTRY_NAME, Tag.TAG_STRING)
                                ? entry.getString(LINE_ENTRY_NAME)
                                : assignedName;
                        lines.add(new LineEntry(lineId, validLineName(name, assignedName), assignedName));
                    }
                }
            }
        }
        if (lines.isEmpty() && tag.hasUUID(LINE_ID)) {
            String name = tag.contains(LINE_NAME, Tag.TAG_STRING)
                    ? tag.getString(LINE_NAME)
                    : fallbackLineName(0);
            String assignedName = validLineName(name, fallbackLineName(0));
            lines.add(new LineEntry(tag.getUUID(LINE_ID), validLineName(name, assignedName), assignedName));
        }
        if (lines.isEmpty()) {
            lines.add(createLine(FALLBACK_LINE_PREFIX, List.of()));
        }
        return lines;
    }

    private static int currentLineIndex(CompoundTag tag, List<LineEntry> lines) {
        int index = tag.contains(LINE_INDEX) ? tag.getInt(LINE_INDEX) : -1;
        if (tag.hasUUID(LINE_ID)) {
            int active = indexOfLine(lines, tag.getUUID(LINE_ID));
            if (active >= 0) {
                index = active;
            }
        }
        return Math.max(0, Math.min(index, Math.max(0, lines.size() - 1)));
    }

    private static void writeLineList(CompoundTag tag, List<LineEntry> lines, int index) {
        if (lines.isEmpty()) {
            lines = List.of(createLine(tag, FALLBACK_LINE_PREFIX, List.of()));
            index = 0;
        }
        int clamped = Math.max(0, Math.min(index, lines.size() - 1));
        ListTag list = new ListTag();
        for (int i = 0; i < lines.size(); i++) {
            LineEntry line = lines.get(i);
            CompoundTag entry = new CompoundTag();
            entry.putUUID(LINE_ENTRY_ID, line.id());
            String assignedName = validLineName(line.assignedName(), fallbackLineName(i));
            entry.putString(LINE_ENTRY_NAME, validLineName(line.name(), assignedName));
            entry.putString(LINE_ENTRY_ASSIGNED_NAME, assignedName);
            list.add(entry);
        }
        tag.put(LINES, list);
        tag.putInt(LINE_INDEX, clamped);
        tag.putUUID(LINE_ID, lines.get(clamped).id());
        tag.putString(LINE_NAME, validLineName(lines.get(clamped).name(), lines.get(clamped).assignedName()));
        rememberLineBindings(tag, lines);
    }

    private static boolean ensureLineNames(ItemStack stack, String prefix) {
        CompoundTag tag = StackData.getOrEmpty(stack);
        List<LineEntry> lines = readLineEntries(tag);
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            LineEntry line = lines.get(i);
            if (line.assignedName().startsWith(FALLBACK_LINE_PREFIX + "-")) {
                String name = nextLineName(prefix, lines, i);
                lines.set(i, new LineEntry(lineIdForName(name), name, name));
                changed = true;
            }
        }
        if (changed) {
            writeLineList(tag, lines, currentLineIndex(tag, lines));
            StackData.set(stack, tag);
        }
        return changed;
    }

    private static LineEntry createLine(String prefix, List<LineEntry> existing) {
        String assignedName = nextLineName(prefix, existing, -1);
        return new LineEntry(lineIdForName(assignedName), assignedName, assignedName);
    }

    private static void rememberKnownLineBindings(ItemStack stack) {
        CompoundTag tag = StackData.getOrEmpty(stack);
        rememberLineBindings(tag, readLineEntries(tag));
        StackData.set(stack, tag);
    }

    private static LineEntry createLine(CompoundTag tag, String prefix, List<LineEntry> existing) {
        String assignedName = nextLineName(prefix, existing, -1);
        LineEntry bound = lineBinding(tag, assignedName);
        LineEntry line = bound == null
                ? new LineEntry(lineIdForName(assignedName), assignedName, assignedName)
                : bound;
        rememberLineBinding(tag, line);
        return line;
    }

    private static LineEntry lineBinding(CompoundTag tag, String assignedName) {
        List<LineEntry> bindings = readLineBindings(tag);
        int index = indexOfLineAssignedName(bindings, assignedName);
        return index < 0 ? null : bindings.get(index);
    }

    private static List<LineEntry> readLineBindings(CompoundTag tag) {
        ArrayList<LineEntry> bindings = new ArrayList<>();
        if (!tag.contains(LINE_BINDINGS, Tag.TAG_LIST)) {
            return bindings;
        }
        ListTag list = tag.getList(LINE_BINDINGS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID(LINE_ENTRY_ID) || !entry.contains(LINE_ENTRY_NAME, Tag.TAG_STRING)) {
                continue;
            }
            String assignedName = entry.contains(LINE_ENTRY_ASSIGNED_NAME, Tag.TAG_STRING)
                    ? entry.getString(LINE_ENTRY_ASSIGNED_NAME)
                    : entry.getString(LINE_ENTRY_NAME);
            assignedName = validLineName(assignedName, "");
            String name = validLineName(entry.getString(LINE_ENTRY_NAME), assignedName);
            if (!assignedName.isBlank() && indexOfLineAssignedName(bindings, assignedName) < 0) {
                bindings.add(new LineEntry(entry.getUUID(LINE_ENTRY_ID), name, assignedName));
            }
        }
        return bindings;
    }

    private static void rememberLineBinding(CompoundTag tag, LineEntry line) {
        rememberLineBindings(tag, List.of(line));
    }

    private static void rememberLineBindings(CompoundTag tag, List<LineEntry> lines) {
        List<LineEntry> bindings = readLineBindings(tag);
        boolean changed = false;
        for (LineEntry line : lines) {
            String name = validLineName(line.name(), "");
            String assignedName = validLineName(line.assignedName(), "");
            if (assignedName.isBlank()) {
                continue;
            }
            int index = indexOfLineAssignedName(bindings, assignedName);
            LineEntry remembered = new LineEntry(line.id(), name, assignedName);
            if (index < 0) {
                bindings.add(remembered);
                changed = true;
            } else if (!bindings.get(index).equals(remembered)) {
                bindings.set(index, remembered);
                changed = true;
            }
        }
        if (changed) {
            writeLineBindings(tag, bindings);
        }
    }

    private static void writeLineBindings(CompoundTag tag, List<LineEntry> bindings) {
        ListTag list = new ListTag();
        for (LineEntry binding : bindings) {
            String assignedName = validLineName(binding.assignedName(), "");
            if (assignedName.isBlank()) {
                continue;
            }
            String name = validLineName(binding.name(), assignedName);
            CompoundTag entry = new CompoundTag();
            entry.putUUID(LINE_ENTRY_ID, binding.id());
            entry.putString(LINE_ENTRY_NAME, name);
            entry.putString(LINE_ENTRY_ASSIGNED_NAME, assignedName);
            list.add(entry);
        }
        tag.put(LINE_BINDINGS, list);
    }

    private static String nextLineName(String prefix, List<LineEntry> existing, int replacingIndex) {
        String cleanPrefix = cleanLinePrefix(prefix);
        String marker = cleanPrefix + "-";
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < existing.size(); i++) {
            if (i == replacingIndex) {
                continue;
            }
            String name = existing.get(i).assignedName();
            if (name.startsWith(marker)) {
                try {
                    int suffix = Integer.parseInt(name.substring(marker.length()));
                    if (suffix >= 0) {
                        used.add(suffix);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        int next = 0;
        while (used.contains(next)) {
            next++;
        }
        return lineName(cleanPrefix, next);
    }

    private static String cleanLinePrefix(String prefix) {
        String clean = prefix == null ? "" : prefix.trim();
        if (clean.isEmpty()) {
            clean = FALLBACK_LINE_PREFIX;
        }
        clean = clean.replaceAll("\\s+", "_");
        return clean.length() > 24 ? clean.substring(0, 24) : clean;
    }

    private static String lineName(List<LineEntry> lines, UUID lineId) {
        int index = indexOfLine(lines, lineId);
        return index < 0 ? fallbackLineName(0) : validLineName(lines.get(index).name(), lines.get(index).assignedName());
    }

    private static String validLineName(String name, String fallback) {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (clean.isBlank()) {
            clean = fallback == null ? "" : fallback.trim().replaceAll("\\s+", " ");
        }
        return clean.length() > MAX_LINE_NAME_LENGTH ? clean.substring(0, MAX_LINE_NAME_LENGTH) : clean;
    }

    private static String fallbackLineName(int index) {
        return lineName(FALLBACK_LINE_PREFIX, index);
    }

    private static int indexOfLine(List<LineEntry> lines, UUID lineId) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).id().equals(lineId)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfLineName(List<LineEntry> lines, String lineName) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).name().equals(lineName)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfLineAssignedName(List<LineEntry> lines, String assignedName) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).assignedName().equals(assignedName)) {
                return i;
            }
        }
        return -1;
    }

    private record LineEntry(UUID id, String name, String assignedName) {
    }

    public record FaceConfig(NodeFaceMode mode, boolean itemsEnabled, boolean fluidsEnabled, boolean energyEnabled,
                             boolean autoDetectResources, RedstoneControl redstoneControl, int priority,
                             List<ItemStack> filters) {
        private static final FaceConfig DEFAULT = new FaceConfig(NodeFaceMode.OUTPUT, true, true, true,
                false, RedstoneControl.IGNORE, 0, emptyFaceFilters());
        private static final FaceConfig PLACEMENT_DEFAULT = new FaceConfig(NodeFaceMode.OUTPUT, true, true, true,
                true, RedstoneControl.IGNORE, 0, emptyFaceFilters());

        public FaceConfig {
            priority = Math.max(-99, Math.min(99, priority));
            filters = List.copyOf(copyFaceFilters(filters));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(MODE, mode.name());
            tag.putBoolean(ITEMS, itemsEnabled);
            tag.putBoolean(FLUIDS, fluidsEnabled);
            tag.putBoolean(ENERGY, energyEnabled);
            tag.putBoolean(AUTO_RESOURCES, autoDetectResources);
            tag.putString(REDSTONE, redstoneControl.name());
            tag.putInt(PRIORITY, priority);
            ListTag filterTags = new ListTag();
            for (int slot = 0; slot < filters.size(); slot++) {
                ItemStack filter = filters.get(slot);
                if (filter.isEmpty()) {
                    continue;
                }
                CompoundTag entry = new CompoundTag();
                entry.putInt(SLOT, slot);
                entry.put(STACK, StackData.saveItem(filter));
                filterTags.add(entry);
            }
            if (!filterTags.isEmpty()) {
                tag.put(FILTERS, filterTags);
            }
            return tag;
        }

        private static FaceConfig load(CompoundTag tag, FaceConfig fallback) {
            List<ItemStack> filters = fallback.filters();
            if (tag.contains(FILTERS, Tag.TAG_LIST)) {
                filters = emptyFaceFilters();
                ListTag filterTags = tag.getList(FILTERS, Tag.TAG_COMPOUND);
                for (int i = 0; i < filterTags.size(); i++) {
                    CompoundTag entry = filterTags.getCompound(i);
                    int slot = entry.getInt(SLOT);
                    if (slot >= 0 && slot < filters.size()) {
                        filters.set(slot, StackData.loadItem(entry.getCompound(STACK)));
                    }
                }
            }
            return new FaceConfig(
                    tag.contains(MODE) ? NodeFaceMode.byName(tag.getString(MODE)) : fallback.mode(),
                    tag.contains(ITEMS) ? tag.getBoolean(ITEMS) : fallback.itemsEnabled(),
                    tag.contains(FLUIDS) ? tag.getBoolean(FLUIDS) : fallback.fluidsEnabled(),
                    tag.contains(ENERGY) ? tag.getBoolean(ENERGY) : fallback.energyEnabled(),
                    tag.contains(AUTO_RESOURCES) ? tag.getBoolean(AUTO_RESOURCES) : false,
                    tag.contains(REDSTONE) ? RedstoneControl.byName(tag.getString(REDSTONE)) : fallback.redstoneControl(),
                    tag.contains(PRIORITY) ? tag.getInt(PRIORITY) : fallback.priority(),
                    filters);
        }

        public ItemStack filter(int slot) {
            return slot < 0 || slot >= filters.size() ? ItemStack.EMPTY : filters.get(slot);
        }

        public FaceConfig withItemsEnabled(boolean enabled) {
            if (autoDetectResources) {
                return new FaceConfig(mode, enabled, false, false, false, redstoneControl, priority, filters);
            }
            return new FaceConfig(mode, enabled, fluidsEnabled, energyEnabled, false, redstoneControl, priority, filters);
        }

        public FaceConfig withFluidsEnabled(boolean enabled) {
            if (autoDetectResources) {
                return new FaceConfig(mode, false, enabled, false, false, redstoneControl, priority, filters);
            }
            return new FaceConfig(mode, itemsEnabled, enabled, energyEnabled, false, redstoneControl, priority, filters);
        }

        public FaceConfig withEnergyEnabled(boolean enabled) {
            if (autoDetectResources) {
                return new FaceConfig(mode, false, false, enabled, false, redstoneControl, priority, filters);
            }
            return new FaceConfig(mode, itemsEnabled, fluidsEnabled, enabled, false, redstoneControl, priority, filters);
        }

        public FaceConfig withAutoDetectResources() {
            return new FaceConfig(mode, itemsEnabled, fluidsEnabled, energyEnabled, true, redstoneControl, priority,
                    filters);
        }

        public FaceConfig withRedstoneControl(RedstoneControl control) {
            return new FaceConfig(mode, itemsEnabled, fluidsEnabled, energyEnabled, autoDetectResources, control,
                    priority, filters);
        }

        public FaceConfig cycleRedstoneControl() {
            return withRedstoneControl(redstoneControl.next());
        }

        public FaceConfig adjustPriority(int delta) {
            return new FaceConfig(mode, itemsEnabled, fluidsEnabled, energyEnabled, autoDetectResources, redstoneControl,
                    priority + delta, filters);
        }
    }

    public record ToolConfig(UUID lineId, String lineName, FaceConfig placement, Map<Direction, FaceConfig> faces,
                             boolean hasCopiedFaces, boolean speedUpgrade, boolean dimensionUpgrade) {
        private static ToolConfig createDefault(LineEntry line) {
            return new ToolConfig(line.id(), line.name(), FaceConfig.PLACEMENT_DEFAULT, defaultFaces(), false,
                    false, false);
        }

        public static ToolConfig fromNode(SkyNodeBlockEntity node) {
            EnumMap<Direction, FaceConfig> faces = new EnumMap<>(Direction.class);
            for (Direction direction : Direction.values()) {
                ArrayList<ItemStack> filters = new ArrayList<>(SkyNodeBlockEntity.FACE_FILTER_SLOTS);
                for (int slot = 0; slot < SkyNodeBlockEntity.FACE_FILTER_SLOTS; slot++) {
                    filters.add(node.getFaceFilter(direction, slot));
                }
                faces.put(direction, new FaceConfig(node.getFaceMode(direction), node.isItemsEnabled(direction),
                        node.isFluidsEnabled(direction), node.isEnergyEnabled(direction),
                        false, node.getRedstoneControl(direction), node.getPriority(direction), filters));
            }
            return new ToolConfig(node.getLineId(), node.getLineName(),
                    faces.getOrDefault(node.getTargetDirection(), FaceConfig.DEFAULT), faces, true,
                    node.hasSpeedUpgrade(), node.hasDimensionUpgrade());
        }

        public ToolConfig {
            faces = new EnumMap<>(faces);
        }

        public boolean itemsEnabled() {
            return !placement.autoDetectResources() && placement.itemsEnabled();
        }

        public boolean fluidsEnabled() {
            return !placement.autoDetectResources() && placement.fluidsEnabled();
        }

        public boolean energyEnabled() {
            return !placement.autoDetectResources() && placement.energyEnabled();
        }

        public boolean autoDetectResources() {
            return placement.autoDetectResources();
        }

        public FaceConfig face(Direction direction) {
            return faces.getOrDefault(direction, FaceConfig.DEFAULT);
        }

        public ToolConfig withLine(UUID newLineId) {
            return withLine(newLineId, fallbackLineName(0));
        }

        public ToolConfig withLine(UUID newLineId, String newLineName) {
            return new ToolConfig(newLineId, validLineName(newLineName, fallbackLineName(0)),
                    placement, faces, hasCopiedFaces, speedUpgrade, dimensionUpgrade);
        }

        public ToolConfig withPlacement(FaceConfig placement) {
            return new ToolConfig(lineId, lineName, placement, faces, hasCopiedFaces, speedUpgrade, dimensionUpgrade);
        }

        public ToolConfig withItemsEnabled(boolean enabled) {
            return withPlacement(placement.withItemsEnabled(enabled));
        }

        public ToolConfig withFluidsEnabled(boolean enabled) {
            return withPlacement(placement.withFluidsEnabled(enabled));
        }

        public ToolConfig withEnergyEnabled(boolean enabled) {
            return withPlacement(placement.withEnergyEnabled(enabled));
        }

        public ToolConfig withAutoDetectResources() {
            return withPlacement(placement.withAutoDetectResources());
        }

        public ToolConfig cycleRedstoneControl() {
            return withPlacement(placement.cycleRedstoneControl());
        }

        public ToolConfig adjustPriority(int delta) {
            return withPlacement(placement.adjustPriority(delta));
        }
    }
}
