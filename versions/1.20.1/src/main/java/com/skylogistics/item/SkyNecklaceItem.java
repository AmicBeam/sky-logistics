package com.skylogistics.item;

import com.skylogistics.menu.SkyNecklaceMenu;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public class SkyNecklaceItem extends Item {
    public static final int MIN_INSERT_SLOTS = 1;
    public static final int MAX_INSERT_SLOTS = 36;
    public static final int MIN_PRIORITY = -99;
    public static final int MAX_PRIORITY = 99;
    private static final String MODE = "SkyNecklaceMode";
    private static final String FILTER = "SkyNecklaceFilter";
    private static final String INSERT_SLOTS = "SkyNecklaceInsertSlots";
    private static final String PRIORITY = "SkyNecklacePriority";

    public SkyNecklaceItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ConfiguratorItem.readOrCreate(stack, player);
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer,
                        new SimpleMenuProvider((id, inventory, ignored) -> new SkyNecklaceMenu(id, inventory, hand),
                                Component.translatable("menu.skylogistics.sky_necklace")),
                        buffer -> buffer.writeEnum(hand));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.read(stack);
        tooltip.add(Component.translatable("tooltip.skylogistics.sky_necklace.mode",
                Component.translatable(mode(stack).translationKey())).withStyle(ChatFormatting.AQUA));
        if (config == null) {
            tooltip.add(Component.translatable("tooltip.skylogistics.configurator.unbound").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.skylogistics.configurator.line", config.lineName())
                    .withStyle(ChatFormatting.GRAY));
        }
        ItemStack filter = filterList(stack);
        if (filter.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.skylogistics.sky_necklace.no_filter")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (!FilterListItem.isWhitelist(filter)) {
            tooltip.add(Component.translatable("tooltip.skylogistics.sky_necklace.invalid_blacklist")
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable("tooltip.skylogistics.sky_necklace.filter",
                    FilterListItem.countItemRules(filter)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.skylogistics.sky_necklace.insert_slots",
                insertSlots(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skylogistics.sky_necklace.priority",
                priority(stack)).withStyle(ChatFormatting.GRAY));
        if (!filter.isEmpty()) {
            FilterListItem.appendFilterContentsOrHint(filter, tooltip, flag);
        }
    }

    public static NecklaceMode mode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(MODE, Tag.TAG_STRING)) {
            return NecklaceMode.EXTRACT;
        }
        return NecklaceMode.byName(tag.getString(MODE));
    }

    public static void setMode(ItemStack stack, NecklaceMode mode) {
        stack.getOrCreateTag().putString(MODE, mode.name());
    }

    public static int insertSlots(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(INSERT_SLOTS, Tag.TAG_INT)) {
            return MIN_INSERT_SLOTS;
        }
        return clampInsertSlots(tag.getInt(INSERT_SLOTS));
    }

    public static void adjustInsertSlots(ItemStack stack, int delta) {
        setInsertSlots(stack, insertSlots(stack) + delta);
    }

    public static void setInsertSlots(ItemStack stack, int slots) {
        stack.getOrCreateTag().putInt(INSERT_SLOTS, clampInsertSlots(slots));
    }

    public static int priority(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(PRIORITY, Tag.TAG_INT)) {
            return 0;
        }
        return clampPriority(tag.getInt(PRIORITY));
    }

    public static void adjustPriority(ItemStack stack, int delta) {
        setPriority(stack, priority(stack) + delta);
    }

    public static void setPriority(ItemStack stack, int priority) {
        stack.getOrCreateTag().putInt(PRIORITY, clampPriority(priority));
    }

    public static ItemStack filterList(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(FILTER, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        ItemStack filter = ItemStack.of(tag.getCompound(FILTER));
        return FilterListItem.isFilterItem(filter) ? filter : ItemStack.EMPTY;
    }

    public static boolean setFilterList(ItemStack necklace, ItemStack filter) {
        if (!canAcceptFilter(filter)) {
            return false;
        }
        CompoundTag tag = necklace.getOrCreateTag();
        if (filter.isEmpty()) {
            tag.remove(FILTER);
        } else {
            ItemStack copy = filter.copy();
            copy.setCount(1);
            tag.put(FILTER, copy.save(new CompoundTag()));
        }
        return true;
    }

    public static boolean canAcceptFilter(ItemStack stack) {
        return stack.isEmpty() || (FilterListItem.isFilterItem(stack) && FilterListItem.isWhitelist(stack));
    }

    public static boolean hasValidItemWhitelist(ItemStack necklace) {
        ItemStack filter = filterList(necklace);
        if (filter.isEmpty() || !FilterListItem.isWhitelist(filter)) {
            return false;
        }
        return FilterListItem.compile(filter).hasItemRules();
    }

    public static boolean matchesWhitelist(ItemStack necklace, ItemStack candidate) {
        ItemStack filter = filterList(necklace);
        return !filter.isEmpty() && FilterListItem.isWhitelist(filter)
                && FilterListItem.compile(filter).hasItemRules()
                && FilterListItem.matches(filter, candidate);
    }

    public static UUID lineId(ItemStack necklace) {
        return ConfiguratorItem.readLineId(necklace);
    }

    private static int clampInsertSlots(int slots) {
        return Math.max(MIN_INSERT_SLOTS, Math.min(MAX_INSERT_SLOTS, slots));
    }

    private static int clampPriority(int priority) {
        return Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, priority));
    }

    public enum NecklaceMode {
        EXTRACT("screen.skylogistics.sky_necklace.mode.extract"),
        INSERT("screen.skylogistics.sky_necklace.mode.insert");

        private final String translationKey;

        NecklaceMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }

        private static NecklaceMode byName(String name) {
            try {
                return NecklaceMode.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return EXTRACT;
            }
        }
    }
}
