package com.skylogistics.item;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.RedstoneControl;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
import net.minecraftforge.network.NetworkHooks;

public class ConfiguratorItem extends Item {
    private static final String LINE_ID = "LineId";
    private static final String ITEMS = "Items";
    private static final String FLUIDS = "Fluids";
    private static final String ENERGY = "Energy";
    private static final String PASTE_MODE = "PasteMode";
    private static final String PLACEMENT = "Placement";
    private static final String FACES = "Faces";
    private static final String MODE = "Mode";
    private static final String REDSTONE = "Redstone";
    private static final String PRIORITY = "Priority";
    private static final String LINES = "Lines";
    private static final String LINE_INDEX = "LineIndex";
    private static final String LINE_ENTRY_ID = "Id";

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

        if (player != null && player.isShiftKeyDown()) {
            writeConfig(stack, ToolConfig.fromNode(node));
            setPasteMode(stack, true);
            player.displayClientMessage(Component.translatable("message.skylogistics.configurator.copied_paste",
                    shortLine(node.getLineId())), true);
            return InteractionResult.CONSUME;
        }

        if (isPasteMode(stack)) {
            node.applyCopiedToolConfig(readOrCreate(stack));
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.skylogistics.configurator.pasted",
                        shortLine(node.getLineId())), true);
            }
            return InteractionResult.CONSUME;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, ignored) -> new com.skylogistics.menu.SkyNodeMenu(id, inventory,
                            pos, true, hand), Component.translatable("menu.skylogistics.sky_node")),
                    buffer -> {
                        buffer.writeBlockPos(pos);
                        buffer.writeBoolean(true);
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
                NetworkHooks.openScreen(serverPlayer,
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
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ToolConfig config = read(stack);
        if (config == null) {
            tooltip.add(Component.translatable("tooltip.skylogistics.configurator.unbound").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.skylogistics.configurator.line", shortLine(config.lineId()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.skylogistics.configurator.types",
                config.itemsEnabled(), config.fluidsEnabled(), config.energyEnabled()).withStyle(ChatFormatting.GRAY));
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
            return config;
        }
        config = ToolConfig.createDefault(UUID.randomUUID());
        writeConfig(stack, config);
        return config;
    }

    public static ToolConfig readOrCreate(ItemStack stack, Player player) {
        ToolConfig config = read(stack);
        if (config != null) {
            return config;
        }
        config = ToolConfig.createDefault(UUID.randomUUID());
        writeConfig(stack, config);
        return config;
    }

    public static ToolConfig read(ItemStack stack) {
        CompoundTag tag = stack.getTag();
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
        return new ToolConfig(tag.getUUID(LINE_ID), placement, faces, copiedFaces);
    }

    public static void writeConfig(ItemStack stack, ToolConfig config) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(LINE_ID, config.lineId());
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
        ensureLineList(tag, config.lineId());
    }

    public static ToolConfig selectFirstLine(ItemStack stack) {
        return selectLine(stack, 0);
    }

    public static ToolConfig selectPreviousLine(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        List<UUID> lines = readLineList(tag);
        int index = Math.max(0, currentLineIndex(tag, lines) - 1);
        return selectLine(stack, index);
    }

    public static ToolConfig selectNextOrCreateLine(ItemStack stack) {
        ToolConfig config = readOrCreate(stack);
        CompoundTag tag = stack.getOrCreateTag();
        List<UUID> lines = readLineList(tag);
        int index = currentLineIndex(tag, lines);
        if (index < lines.size() - 1) {
            return selectLine(stack, index + 1);
        }
        UUID lineId = UUID.randomUUID();
        lines.add(lineId);
        writeLineList(tag, lines, lines.size() - 1);
        config = config.withLine(lineId);
        writeConfig(stack, config);
        return config;
    }

    public static ToolConfig selectLastLine(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        List<UUID> lines = readLineList(tag);
        return selectLine(stack, Math.max(0, lines.size() - 1));
    }

    public static ToolConfig removeCurrentLine(ItemStack stack) {
        ToolConfig config = readOrCreate(stack);
        CompoundTag tag = stack.getOrCreateTag();
        List<UUID> lines = readLineList(tag);
        int index = currentLineIndex(tag, lines);
        if (lines.size() <= 1) {
            UUID lineId = UUID.randomUUID();
            lines.clear();
            lines.add(lineId);
            writeLineList(tag, lines, 0);
            config = config.withLine(lineId);
            writeConfig(stack, config);
            return config;
        }
        lines.remove(index);
        int nextIndex = Math.min(index, lines.size() - 1);
        writeLineList(tag, lines, nextIndex);
        config = config.withLine(lines.get(nextIndex));
        writeConfig(stack, config);
        return config;
    }

    public static int lineIndex(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return 0;
        }
        return currentLineIndex(tag, readLineList(tag));
    }

    public static int lineCount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : readLineList(tag).size();
    }

    public static boolean isPasteMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(PASTE_MODE);
    }

    public static void setPasteMode(ItemStack stack, boolean enabled) {
        if (enabled) {
            stack.getOrCreateTag().putBoolean(PASTE_MODE, true);
        } else if (stack.hasTag()) {
            stack.getTag().remove(PASTE_MODE);
        }
    }

    public static String shortLine(UUID lineId) {
        String raw = lineId.toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        return raw.substring(0, 4) + "-" + raw.substring(4, 6);
    }

    private static EnumMap<Direction, FaceConfig> defaultFaces() {
        EnumMap<Direction, FaceConfig> faces = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            faces.put(direction, FaceConfig.DEFAULT);
        }
        return faces;
    }

    private static ToolConfig selectLine(ItemStack stack, int index) {
        ToolConfig config = readOrCreate(stack);
        CompoundTag tag = stack.getOrCreateTag();
        List<UUID> lines = readLineList(tag);
        int clamped = Math.max(0, Math.min(index, lines.size() - 1));
        writeLineList(tag, lines, clamped);
        config = config.withLine(lines.get(clamped));
        writeConfig(stack, config);
        return config;
    }

    private static void ensureLineList(CompoundTag tag, UUID lineId) {
        List<UUID> lines = readLineList(tag);
        int index = lines.indexOf(lineId);
        if (index < 0) {
            lines.add(lineId);
            index = lines.size() - 1;
        }
        writeLineList(tag, lines, index);
    }

    private static List<UUID> readLineList(CompoundTag tag) {
        java.util.ArrayList<UUID> lines = new java.util.ArrayList<>();
        if (tag.contains(LINES, Tag.TAG_LIST)) {
            ListTag list = tag.getList(LINES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (entry.hasUUID(LINE_ENTRY_ID)) {
                    UUID lineId = entry.getUUID(LINE_ENTRY_ID);
                    if (!lines.contains(lineId)) {
                        lines.add(lineId);
                    }
                }
            }
        }
        if (lines.isEmpty() && tag.hasUUID(LINE_ID)) {
            lines.add(tag.getUUID(LINE_ID));
        }
        if (lines.isEmpty()) {
            lines.add(UUID.randomUUID());
        }
        return lines;
    }

    private static int currentLineIndex(CompoundTag tag, List<UUID> lines) {
        int index = tag.contains(LINE_INDEX) ? tag.getInt(LINE_INDEX) : -1;
        if (tag.hasUUID(LINE_ID)) {
            int active = lines.indexOf(tag.getUUID(LINE_ID));
            if (active >= 0) {
                index = active;
            }
        }
        return Math.max(0, Math.min(index, Math.max(0, lines.size() - 1)));
    }

    private static void writeLineList(CompoundTag tag, List<UUID> lines, int index) {
        if (lines.isEmpty()) {
            lines = List.of(UUID.randomUUID());
            index = 0;
        }
        int clamped = Math.max(0, Math.min(index, lines.size() - 1));
        ListTag list = new ListTag();
        for (UUID lineId : lines) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(LINE_ENTRY_ID, lineId);
            list.add(entry);
        }
        tag.put(LINES, list);
        tag.putInt(LINE_INDEX, clamped);
        tag.putUUID(LINE_ID, lines.get(clamped));
    }

    public record FaceConfig(NodeFaceMode mode, boolean itemsEnabled, boolean fluidsEnabled, boolean energyEnabled,
                             RedstoneControl redstoneControl, int priority) {
        private static final FaceConfig DEFAULT = new FaceConfig(NodeFaceMode.OUTPUT, true, true, true,
                RedstoneControl.IGNORE, 0);
        private static final FaceConfig PLACEMENT_DEFAULT = new FaceConfig(NodeFaceMode.OUTPUT, true, true, true,
                RedstoneControl.IGNORE, 0);

        public FaceConfig {
            if (mode == NodeFaceMode.NONE) {
                mode = NodeFaceMode.OUTPUT;
            }
            priority = Math.max(-99, Math.min(99, priority));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(MODE, mode.name());
            tag.putBoolean(ITEMS, itemsEnabled);
            tag.putBoolean(FLUIDS, fluidsEnabled);
            tag.putBoolean(ENERGY, energyEnabled);
            tag.putString(REDSTONE, redstoneControl.name());
            tag.putInt(PRIORITY, priority);
            return tag;
        }

        private static FaceConfig load(CompoundTag tag, FaceConfig fallback) {
            return new FaceConfig(
                    tag.contains(MODE) ? NodeFaceMode.byName(tag.getString(MODE)) : fallback.mode(),
                    tag.contains(ITEMS) ? tag.getBoolean(ITEMS) : fallback.itemsEnabled(),
                    tag.contains(FLUIDS) ? tag.getBoolean(FLUIDS) : fallback.fluidsEnabled(),
                    tag.contains(ENERGY) ? tag.getBoolean(ENERGY) : fallback.energyEnabled(),
                    tag.contains(REDSTONE) ? RedstoneControl.byName(tag.getString(REDSTONE)) : fallback.redstoneControl(),
                    tag.contains(PRIORITY) ? tag.getInt(PRIORITY) : fallback.priority());
        }

        public FaceConfig withItemsEnabled(boolean enabled) {
            return new FaceConfig(mode, enabled, fluidsEnabled, energyEnabled, redstoneControl, priority);
        }

        public FaceConfig withFluidsEnabled(boolean enabled) {
            return new FaceConfig(mode, itemsEnabled, enabled, energyEnabled, redstoneControl, priority);
        }

        public FaceConfig withEnergyEnabled(boolean enabled) {
            return new FaceConfig(mode, itemsEnabled, fluidsEnabled, enabled, redstoneControl, priority);
        }

        public FaceConfig withRedstoneControl(RedstoneControl control) {
            return new FaceConfig(mode, itemsEnabled, fluidsEnabled, energyEnabled, control, priority);
        }

        public FaceConfig cycleRedstoneControl() {
            return withRedstoneControl(redstoneControl.next());
        }

        public FaceConfig adjustPriority(int delta) {
            return new FaceConfig(mode, itemsEnabled, fluidsEnabled, energyEnabled, redstoneControl, priority + delta);
        }
    }

    public record ToolConfig(UUID lineId, FaceConfig placement, Map<Direction, FaceConfig> faces,
                             boolean hasCopiedFaces) {
        public static ToolConfig createDefault(UUID lineId) {
            return new ToolConfig(lineId, FaceConfig.PLACEMENT_DEFAULT, defaultFaces(), false);
        }

        public static ToolConfig fromNode(SkyNodeBlockEntity node) {
            EnumMap<Direction, FaceConfig> faces = new EnumMap<>(Direction.class);
            for (Direction direction : Direction.values()) {
                faces.put(direction, new FaceConfig(node.getFaceMode(direction), node.isItemsEnabled(direction),
                        node.isFluidsEnabled(direction), node.isEnergyEnabled(direction),
                        node.getRedstoneControl(direction), node.getPriority(direction)));
            }
            return new ToolConfig(node.getLineId(), faces.getOrDefault(node.getTargetDirection(), FaceConfig.DEFAULT),
                    faces, true);
        }

        public ToolConfig {
            faces = new EnumMap<>(faces);
        }

        public boolean itemsEnabled() {
            return placement.itemsEnabled();
        }

        public boolean fluidsEnabled() {
            return placement.fluidsEnabled();
        }

        public boolean energyEnabled() {
            return placement.energyEnabled();
        }

        public FaceConfig face(Direction direction) {
            return faces.getOrDefault(direction, FaceConfig.DEFAULT);
        }

        public ToolConfig withLine(UUID newLineId) {
            return new ToolConfig(newLineId, placement, faces, hasCopiedFaces);
        }

        public ToolConfig withPlacement(FaceConfig placement) {
            return new ToolConfig(lineId, placement, faces, hasCopiedFaces);
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

        public ToolConfig cycleRedstoneControl() {
            return withPlacement(placement.cycleRedstoneControl());
        }

        public ToolConfig adjustPriority(int delta) {
            return withPlacement(placement.adjustPriority(delta));
        }
    }
}
