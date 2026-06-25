package com.skylogistics.item;

import com.skylogistics.menu.FilterListMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

public class FilterListItem extends Item {
    public static final int FILTER_SLOTS = 9;
    private static final String FILTERS = "Filters";
    private static final String FLUID_FILTERS = "FluidFilters";
    private static final String SLOT = "Slot";
    private static final String STACK = "Stack";
    private static final String FLUID = "Fluid";
    private static final String WHITELIST = "Whitelist";
    private static final String MATCH_TAGS = "MatchTags";
    private static final String MATCH_MODS = "MatchMods";
    private static final String FILTER_MODE = "FilterMode";
    private static final String ATTRIBUTES = "Attributes";
    private static final String MATCH_ALL_ATTRIBUTES = "MatchAllAttributes";

    public FilterListItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, ignored) -> new FilterListMenu(id, inventory, hand),
                            Component.translatable("menu.skylogistics.filter_list")),
                    buffer -> buffer.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.mode",
                Component.translatable(getMode(stack).translationKey())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.list_mode",
                Component.translatable(isWhitelist(stack) ? "screen.skylogistics.filter_whitelist"
                        : "screen.skylogistics.filter_blacklist")).withStyle(ChatFormatting.GRAY));
        if (getMode(stack) == FilterMode.ATTRIBUTE) {
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.attribute_match",
                    Component.translatable(matchAllAttributes(stack) ? "screen.skylogistics.filter_match_all"
                            : "screen.skylogistics.filter_match_any")).withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.attributes",
                    countAttributes(stack), Attribute.values().length).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.tags", matchTags(stack))
                    .withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.mods", matchMods(stack))
                    .withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.entries", countFilters(stack), FILTER_SLOTS)
                    .withStyle(ChatFormatting.GRAY));
            int fluids = countFluidFilters(stack);
            if (fluids > 0) {
                tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.fluids", fluids, FILTER_SLOTS)
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    public static FilterMode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? FilterMode.LIST : FilterMode.byName(tag.getString(FILTER_MODE));
    }

    public static void setMode(ItemStack stack, FilterMode mode) {
        stack.getOrCreateTag().putString(FILTER_MODE, mode.name());
    }

    public static void cycleMode(ItemStack stack) {
        setMode(stack, getMode(stack).next());
    }

    public static boolean isWhitelist(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return !tag.contains(WHITELIST) || tag.getBoolean(WHITELIST);
    }

    public static void setWhitelist(ItemStack stack, boolean whitelist) {
        stack.getOrCreateTag().putBoolean(WHITELIST, whitelist);
    }

    public static boolean matchTags(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(MATCH_TAGS);
    }

    public static void setMatchTags(ItemStack stack, boolean matchTags) {
        stack.getOrCreateTag().putBoolean(MATCH_TAGS, matchTags);
    }

    public static boolean matchMods(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(MATCH_MODS);
    }

    public static void setMatchMods(ItemStack stack, boolean matchMods) {
        stack.getOrCreateTag().putBoolean(MATCH_MODS, matchMods);
    }

    public static boolean matchAllAttributes(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(MATCH_ALL_ATTRIBUTES);
    }

    public static void setMatchAllAttributes(ItemStack stack, boolean matchAllAttributes) {
        stack.getOrCreateTag().putBoolean(MATCH_ALL_ATTRIBUTES, matchAllAttributes);
    }

    public static boolean hasAttribute(ItemStack stack, Attribute attribute) {
        return (attributeMask(stack) & attribute.mask()) != 0;
    }

    public static void toggleAttribute(ItemStack stack, Attribute attribute) {
        int mask = attributeMask(stack) ^ attribute.mask();
        stack.getOrCreateTag().putInt(ATTRIBUTES, mask);
    }

    public static void clearAttributes(ItemStack stack) {
        if (stack.hasTag()) {
            stack.getTag().remove(ATTRIBUTES);
        }
    }

    public static ItemStack getFilter(ItemStack stack, int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(FILTERS, Tag.TAG_LIST)) {
            return ItemStack.EMPTY;
        }
        ListTag filters = tag.getList(FILTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < filters.size(); i++) {
            CompoundTag entry = filters.getCompound(i);
            if (entry.getInt(SLOT) == slot) {
                return ItemStack.of(entry.getCompound(STACK));
            }
        }
        return ItemStack.EMPTY;
    }

    public static void setFilter(ItemStack stack, int slot, ItemStack filter) {
        if (slot < 0 || slot >= FILTER_SLOTS) {
            return;
        }
        List<ItemStack> filters = getFilters(stack);
        ItemStack copy = filter.copy();
        if (!copy.isEmpty()) {
            copy.setCount(1);
        }
        filters.set(slot, copy);
        saveFilters(stack, filters);
        if (!copy.isEmpty()) {
            setFluidFilter(stack, slot, FluidStack.EMPTY);
        }
    }

    public static FluidStack getFluidFilter(ItemStack stack, int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) {
            return FluidStack.EMPTY;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(FLUID_FILTERS, Tag.TAG_LIST)) {
            return FluidStack.EMPTY;
        }
        ListTag filters = tag.getList(FLUID_FILTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < filters.size(); i++) {
            CompoundTag entry = filters.getCompound(i);
            if (entry.getInt(SLOT) == slot) {
                return FluidStack.loadFluidStackFromNBT(entry.getCompound(FLUID));
            }
        }
        return FluidStack.EMPTY;
    }

    public static void setFluidFilter(ItemStack stack, int slot, FluidStack filter) {
        if (slot < 0 || slot >= FILTER_SLOTS) {
            return;
        }
        List<FluidStack> filters = getFluidFilters(stack);
        FluidStack copy = filter.copy();
        if (!copy.isEmpty()) {
            copy.setAmount(1);
        }
        filters.set(slot, copy);
        saveFluidFilters(stack, filters);
        if (!copy.isEmpty()) {
            setFilter(stack, slot, ItemStack.EMPTY);
        }
    }

    public static ItemStack getDisplayFilter(ItemStack stack, int slot) {
        ItemStack item = getFilter(stack, slot);
        if (!item.isEmpty()) {
            return item;
        }
        return fluidDisplayStack(getFluidFilter(stack, slot));
    }

    public static void clearFilters(ItemStack stack) {
        if (stack.hasTag()) {
            stack.getTag().remove(FILTERS);
            stack.getTag().remove(FLUID_FILTERS);
        }
    }

    public static int countFilters(ItemStack stack) {
        int count = 0;
        for (ItemStack filter : getFilters(stack)) {
            if (!filter.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static int countFluidFilters(ItemStack stack) {
        int count = 0;
        for (FluidStack filter : getFluidFilters(stack)) {
            if (!filter.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static int countAttributes(ItemStack stack) {
        return Integer.bitCount(attributeMask(stack));
    }

    public static List<ItemStack> getFilters(ItemStack stack) {
        List<ItemStack> filters = new ArrayList<>(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++) {
            filters.add(ItemStack.EMPTY);
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(FILTERS, Tag.TAG_LIST)) {
            return filters;
        }
        ListTag entries = tag.getList(FILTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int slot = entry.getInt(SLOT);
            if (slot >= 0 && slot < FILTER_SLOTS) {
                filters.set(slot, ItemStack.of(entry.getCompound(STACK)));
            }
        }
        return filters;
    }

    public static List<FluidStack> getFluidFilters(ItemStack stack) {
        List<FluidStack> filters = new ArrayList<>(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++) {
            filters.add(FluidStack.EMPTY);
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(FLUID_FILTERS, Tag.TAG_LIST)) {
            return filters;
        }
        ListTag entries = tag.getList(FLUID_FILTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int slot = entry.getInt(SLOT);
            if (slot >= 0 && slot < FILTER_SLOTS) {
                filters.set(slot, FluidStack.loadFluidStackFromNBT(entry.getCompound(FLUID)));
            }
        }
        return filters;
    }

    public static boolean matches(ItemStack filterList, ItemStack candidate) {
        return compile(filterList).matches(candidate);
    }

    public static boolean matchesFluid(ItemStack filterList, FluidStack candidate) {
        return compile(filterList).matchesFluid(candidate);
    }

    public static CompiledFilter compile(ItemStack filterList) {
        if (filterList.isEmpty()) {
            return CompiledFilter.ALLOW_ALL;
        }
        boolean whitelist = isWhitelist(filterList);
        if (getMode(filterList) == FilterMode.ATTRIBUTE) {
            int mask = attributeMask(filterList);
            if (mask == 0) {
                return CompiledFilter.ALLOW_ALL;
            }
            return CompiledFilter.attributes(whitelist, mask, matchAllAttributes(filterList));
        }
        List<ItemStack> filters = getFilters(filterList);
        List<CompiledFilter.Entry> entries = new ArrayList<>(FILTER_SLOTS);
        boolean tags = matchTags(filterList);
        boolean mods = matchMods(filterList);
        for (ItemStack filter : filters) {
            if (filter.isEmpty()) {
                continue;
            }
            ItemStack copy = filter.copy();
            copy.setCount(1);
            entries.add(new CompiledFilter.Entry(copy, tags ? tagArray(filter.getTags()) : CompiledFilter.NO_TAGS,
                    mods ? itemNamespace(filter) : ""));
        }
        List<CompiledFilter.FluidEntry> fluidEntries = new ArrayList<>(FILTER_SLOTS);
        for (FluidStack fluid : getFluidFilters(filterList)) {
            if (fluid.isEmpty()) {
                continue;
            }
            FluidStack copy = fluid.copy();
            copy.setAmount(1);
            fluidEntries.add(new CompiledFilter.FluidEntry(copy, mods ? fluidNamespace(fluid) : ""));
        }
        if (entries.isEmpty() && fluidEntries.isEmpty()) {
            return CompiledFilter.ALLOW_ALL;
        }
        return CompiledFilter.list(whitelist, entries.toArray(CompiledFilter.Entry[]::new),
                fluidEntries.toArray(CompiledFilter.FluidEntry[]::new));
    }

    private static boolean matchesAttributes(ItemStack filterList, ItemStack candidate) {
        int mask = attributeMask(filterList);
        if (mask == 0) {
            return true;
        }
        boolean all = matchAllAttributes(filterList);
        boolean matched = all;
        for (Attribute attribute : Attribute.values()) {
            if ((mask & attribute.mask()) == 0) {
                continue;
            }
            boolean attributeMatched = attribute.matches(candidate);
            if (all && !attributeMatched) {
                matched = false;
                break;
            }
            if (!all && attributeMatched) {
                matched = true;
                break;
            }
        }
        return isWhitelist(filterList) == matched;
    }

    private static boolean matchesFilter(ItemStack filter, ItemStack candidate, boolean tags) {
        if (ItemStack.isSameItemSameTags(filter, candidate)) {
            return true;
        }
        if (!tags) {
            return false;
        }
        return filter.getTags().anyMatch((TagKey<net.minecraft.world.item.Item> tag) -> candidate.is(tag));
    }

    private static String itemNamespace(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.getNamespace();
    }

    private static String fluidNamespace(FluidStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        var id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
        return id == null ? "" : id.getNamespace();
    }

    private static void saveFilters(ItemStack stack, List<ItemStack> filters) {
        ListTag entries = new ListTag();
        for (int slot = 0; slot < Math.min(FILTER_SLOTS, filters.size()); slot++) {
            ItemStack filter = filters.get(slot);
            if (filter.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(SLOT, slot);
            entry.put(STACK, filter.save(new CompoundTag()));
            entries.add(entry);
        }
        stack.getOrCreateTag().put(FILTERS, entries);
    }

    private static void saveFluidFilters(ItemStack stack, List<FluidStack> filters) {
        ListTag entries = new ListTag();
        for (int slot = 0; slot < Math.min(FILTER_SLOTS, filters.size()); slot++) {
            FluidStack filter = filters.get(slot);
            if (filter.isEmpty()) {
                continue;
            }
            FluidStack copy = filter.copy();
            copy.setAmount(1);
            CompoundTag entry = new CompoundTag();
            entry.putInt(SLOT, slot);
            entry.put(FLUID, copy.writeToNBT(new CompoundTag()));
            entries.add(entry);
        }
        stack.getOrCreateTag().put(FLUID_FILTERS, entries);
    }

    private static ItemStack fluidDisplayStack(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack bucket = fluid.getFluid().getBucket().getDefaultInstance();
        if (bucket.isEmpty()) {
            bucket = Items.BUCKET.getDefaultInstance();
        }
        bucket.setCount(1);
        return bucket;
    }

    private static int attributeMask(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(ATTRIBUTES);
    }

    @SuppressWarnings("unchecked")
    private static TagKey<Item>[] tagArray(Stream<TagKey<Item>> tags) {
        return tags.toArray(TagKey[]::new);
    }

    public static final class CompiledFilter {
        private static final TagKey<Item>[] NO_TAGS = tagArray(Stream.empty());
        private static final Entry[] NO_ENTRIES = new Entry[0];
        private static final FluidEntry[] NO_FLUID_ENTRIES = new FluidEntry[0];
        public static final CompiledFilter ALLOW_ALL = new CompiledFilter(Mode.ALLOW_ALL, true, 0, false, NO_ENTRIES,
                NO_FLUID_ENTRIES);

        private final Mode mode;
        private final boolean whitelist;
        private final int attributeMask;
        private final boolean matchAllAttributes;
        private final Entry[] entries;
        private final FluidEntry[] fluidEntries;

        private CompiledFilter(Mode mode, boolean whitelist, int attributeMask, boolean matchAllAttributes,
                Entry[] entries, FluidEntry[] fluidEntries) {
            this.mode = mode;
            this.whitelist = whitelist;
            this.attributeMask = attributeMask;
            this.matchAllAttributes = matchAllAttributes;
            this.entries = entries;
            this.fluidEntries = fluidEntries;
        }

        private static CompiledFilter attributes(boolean whitelist, int attributeMask, boolean matchAllAttributes) {
            return new CompiledFilter(Mode.ATTRIBUTES, whitelist, attributeMask, matchAllAttributes, NO_ENTRIES,
                    NO_FLUID_ENTRIES);
        }

        private static CompiledFilter list(boolean whitelist, Entry[] entries, FluidEntry[] fluidEntries) {
            return new CompiledFilter(Mode.LIST, whitelist, 0, false, entries, fluidEntries);
        }

        public boolean matches(ItemStack candidate) {
            if (mode == Mode.ALLOW_ALL) {
                return true;
            }
            if (candidate.isEmpty()) {
                return false;
            }
            return switch (mode) {
                case ATTRIBUTES -> matchesAttributes(candidate);
                case LIST -> matchesList(candidate);
                case ALLOW_ALL -> true;
            };
        }

        private boolean matchesAttributes(ItemStack candidate) {
            boolean all = matchAllAttributes;
            boolean matched = all;
            for (Attribute attribute : Attribute.values()) {
                if ((attributeMask & attribute.mask()) == 0) {
                    continue;
                }
                boolean attributeMatched = attribute.matches(candidate);
                if (all && !attributeMatched) {
                    matched = false;
                    break;
                }
                if (!all && attributeMatched) {
                    matched = true;
                    break;
                }
            }
            return whitelist == matched;
        }

        private boolean matchesList(ItemStack candidate) {
            if (entries.length == 0) {
                return true;
            }
            boolean matched = false;
            String candidateNamespace = null;
            for (Entry entry : entries) {
                if (ItemStack.isSameItemSameTags(entry.stack(), candidate)) {
                    matched = true;
                    break;
                }
                for (TagKey<Item> tag : entry.tags()) {
                    if (candidate.is(tag)) {
                        matched = true;
                        break;
                    }
                }
                if (matched) {
                    break;
                }
                if (!entry.namespace().isEmpty()) {
                    if (candidateNamespace == null) {
                        candidateNamespace = itemNamespace(candidate);
                    }
                    if (!candidateNamespace.isEmpty() && entry.namespace().equals(candidateNamespace)) {
                        matched = true;
                        break;
                    }
                }
            }
            return whitelist == matched;
        }

        public boolean matchesFluid(FluidStack candidate) {
            if (mode != Mode.LIST || fluidEntries.length == 0) {
                return true;
            }
            if (candidate.isEmpty()) {
                return false;
            }
            boolean matched = false;
            String candidateNamespace = null;
            for (FluidEntry entry : fluidEntries) {
                if (entry.stack().isFluidEqual(candidate)) {
                    matched = true;
                    break;
                }
                if (!entry.namespace().isEmpty()) {
                    if (candidateNamespace == null) {
                        candidateNamespace = fluidNamespace(candidate);
                    }
                    if (!candidateNamespace.isEmpty() && entry.namespace().equals(candidateNamespace)) {
                        matched = true;
                        break;
                    }
                }
            }
            return whitelist == matched;
        }

        public boolean whitelist() {
            return whitelist;
        }

        public boolean hasItemRules() {
            return mode == Mode.ATTRIBUTES || entries.length > 0;
        }

        public boolean hasFluidRules() {
            return mode == Mode.LIST && fluidEntries.length > 0;
        }

        private enum Mode {
            ALLOW_ALL,
            ATTRIBUTES,
            LIST
        }

        private record Entry(ItemStack stack, TagKey<Item>[] tags, String namespace) {
        }

        private record FluidEntry(FluidStack stack, String namespace) {
        }
    }

    public enum FilterMode {
        LIST("screen.skylogistics.filter_mode_list"),
        ATTRIBUTE("screen.skylogistics.filter_mode_attribute");

        private final String translationKey;

        FilterMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }

        public FilterMode next() {
            return this == LIST ? ATTRIBUTE : LIST;
        }

        private static FilterMode byName(String name) {
            for (FilterMode mode : values()) {
                if (mode.name().equals(name)) {
                    return mode;
                }
            }
            return LIST;
        }
    }

    public enum Attribute {
        STACKABLE("screen.skylogistics.filter_attr_stackable") {
            @Override
            boolean matches(ItemStack stack) {
                return stack.isStackable();
            }
        },
        UNSTACKABLE("screen.skylogistics.filter_attr_unstackable") {
            @Override
            boolean matches(ItemStack stack) {
                return !stack.isStackable();
            }
        },
        HAS_TAG("screen.skylogistics.filter_attr_has_tag") {
            @Override
            boolean matches(ItemStack stack) {
                return stack.hasTag();
            }
        },
        DAMAGEABLE("screen.skylogistics.filter_attr_damageable") {
            @Override
            boolean matches(ItemStack stack) {
                return stack.isDamageableItem();
            }
        },
        DAMAGED("screen.skylogistics.filter_attr_damaged") {
            @Override
            boolean matches(ItemStack stack) {
                return stack.isDamaged();
            }
        },
        ENCHANTED("screen.skylogistics.filter_attr_enchanted") {
            @Override
            boolean matches(ItemStack stack) {
                return stack.isEnchanted();
            }
        },
        FOOD("screen.skylogistics.filter_attr_food") {
            @Override
            boolean matches(ItemStack stack) {
                return stack.getItem().isEdible();
            }
        },
        BLOCK_ITEM("screen.skylogistics.filter_attr_block_item") {
            @Override
            boolean matches(ItemStack stack) {
                return stack.getItem() instanceof BlockItem;
            }
        };

        private final String translationKey;

        Attribute(String translationKey) {
            this.translationKey = translationKey;
        }

        public int mask() {
            return 1 << ordinal();
        }

        public String translationKey() {
            return translationKey;
        }

        abstract boolean matches(ItemStack stack);
    }
}
