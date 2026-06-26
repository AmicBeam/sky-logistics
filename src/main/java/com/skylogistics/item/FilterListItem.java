package com.skylogistics.item;

import com.skylogistics.menu.FilterListMenu;
import com.skylogistics.storage.FluidStackKey;
import com.skylogistics.util.StackData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

public class FilterListItem extends Item {
    public static final int FILTER_SLOTS = 18;
    private static final String FILTERS = "Filters";
    private static final String FLUID_FILTERS = "FluidFilters";
    private static final String SLOT = "Slot";
    private static final String STACK = "Stack";
    private static final String FLUID = "Fluid";
    private static final String WHITELIST = "Whitelist";
    private static final String MATCH_NBT = "MatchNbt";
    private static final String MATCH_DURABILITY = "MatchDurability";

    public FilterListItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, ignored) -> new FilterListMenu(id, inventory, hand),
                            Component.translatable("menu.skylogistics.filter_list")),
                    buffer -> buffer.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.list_mode",
                Component.translatable(isWhitelist(stack) ? "screen.skylogistics.filter_whitelist"
                        : "screen.skylogistics.filter_blacklist")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.nbt", matchNbt(stack))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.durability", matchDurability(stack))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.entries", countFilters(stack), FILTER_SLOTS)
                .withStyle(ChatFormatting.GRAY));
        int fluids = countFluidFilters(stack);
        if (fluids > 0) {
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.fluids", fluids, FILTER_SLOTS)
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    public static boolean isWhitelist(ItemStack stack) {
        CompoundTag tag = StackData.getOrEmpty(stack);
        return !tag.contains(WHITELIST) || tag.getBoolean(WHITELIST);
    }

    public static void setWhitelist(ItemStack stack, boolean whitelist) {
        StackData.update(stack, tag -> tag.putBoolean(WHITELIST, whitelist));
    }

    public static boolean matchNbt(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return tag != null && tag.getBoolean(MATCH_NBT);
    }

    public static void setMatchNbt(ItemStack stack, boolean matchNbt) {
        StackData.update(stack, tag -> tag.putBoolean(MATCH_NBT, matchNbt));
    }

    public static boolean matchDurability(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return tag != null && tag.getBoolean(MATCH_DURABILITY);
    }

    public static void setMatchDurability(ItemStack stack, boolean matchDurability) {
        StackData.update(stack, tag -> tag.putBoolean(MATCH_DURABILITY, matchDurability));
    }

    public static ItemStack getFilter(ItemStack stack, int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = StackData.get(stack);
        if (tag == null || !tag.contains(FILTERS, Tag.TAG_LIST)) {
            return ItemStack.EMPTY;
        }
        ListTag filters = tag.getList(FILTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < filters.size(); i++) {
            CompoundTag entry = filters.getCompound(i);
            if (entry.getInt(SLOT) == slot) {
                return StackData.loadItem(entry.getCompound(STACK));
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
        CompoundTag tag = StackData.get(stack);
        if (tag == null || !tag.contains(FLUID_FILTERS, Tag.TAG_LIST)) {
            return FluidStack.EMPTY;
        }
        ListTag filters = tag.getList(FLUID_FILTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < filters.size(); i++) {
            CompoundTag entry = filters.getCompound(i);
            if (entry.getInt(SLOT) == slot) {
                return StackData.loadFluid(entry.getCompound(FLUID));
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
        StackData.update(stack, tag -> {
            tag.remove(FILTERS);
            tag.remove(FLUID_FILTERS);
            tag.remove("FilterMode");
            tag.remove("Attributes");
            tag.remove("MatchAllAttributes");
        });
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

    public static List<ItemStack> getFilters(ItemStack stack) {
        List<ItemStack> filters = new ArrayList<>(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++) {
            filters.add(ItemStack.EMPTY);
        }
        CompoundTag tag = StackData.get(stack);
        if (tag == null || !tag.contains(FILTERS, Tag.TAG_LIST)) {
            return filters;
        }
        ListTag entries = tag.getList(FILTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int slot = entry.getInt(SLOT);
            if (slot >= 0 && slot < FILTER_SLOTS) {
                filters.set(slot, StackData.loadItem(entry.getCompound(STACK)));
            }
        }
        return filters;
    }

    public static List<FluidStack> getFluidFilters(ItemStack stack) {
        List<FluidStack> filters = new ArrayList<>(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++) {
            filters.add(FluidStack.EMPTY);
        }
        CompoundTag tag = StackData.get(stack);
        if (tag == null || !tag.contains(FLUID_FILTERS, Tag.TAG_LIST)) {
            return filters;
        }
        ListTag entries = tag.getList(FLUID_FILTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int slot = entry.getInt(SLOT);
            if (slot >= 0 && slot < FILTER_SLOTS) {
                filters.set(slot, StackData.loadFluid(entry.getCompound(FLUID)));
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
        List<ItemStack> filters = getFilters(filterList);
        List<CompiledFilter.Entry> entries = new ArrayList<>(FILTER_SLOTS);
        boolean nbt = matchNbt(filterList);
        boolean durability = matchDurability(filterList);
        for (ItemStack filter : filters) {
            if (filter.isEmpty()) {
                continue;
            }
            ItemStack copy = filter.copy();
            copy.setCount(1);
            entries.add(new CompiledFilter.Entry(copy));
        }
        List<CompiledFilter.FluidEntry> fluidEntries = new ArrayList<>(FILTER_SLOTS);
        for (FluidStack fluid : getFluidFilters(filterList)) {
            if (fluid.isEmpty()) {
                continue;
            }
            FluidStack copy = fluid.copy();
            copy.setAmount(1);
            fluidEntries.add(new CompiledFilter.FluidEntry(copy));
        }
        if (entries.isEmpty() && fluidEntries.isEmpty()) {
            return CompiledFilter.ALLOW_ALL;
        }
        return CompiledFilter.list(whitelist, nbt, durability, entries.toArray(CompiledFilter.Entry[]::new),
                fluidEntries.toArray(CompiledFilter.FluidEntry[]::new));
    }

    private static boolean matchesItemSample(ItemStack sample, ItemStack candidate, boolean matchNbt,
            boolean matchDurability) {
        if (!ItemStack.isSameItem(sample, candidate)) {
            return false;
        }
        if (matchDurability && sample.isDamageableItem() && sample.getDamageValue() != candidate.getDamageValue()) {
            return false;
        }
        if (!matchNbt) {
            return true;
        }
        return Objects.equals(comparableTag(sample, matchDurability), comparableTag(candidate, matchDurability));
    }

    private static CompoundTag comparableTag(ItemStack stack, boolean includeDurability) {
        CompoundTag tag = StackData.get(stack);
        if (tag == null) {
            return null;
        }
        CompoundTag copy = tag.copy();
        if (!includeDurability) {
            copy.remove("Damage");
        }
        return copy.getAllKeys().isEmpty() ? null : copy;
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
            entry.put(STACK, StackData.saveItem(filter));
            entries.add(entry);
        }
        StackData.update(stack, tag -> tag.put(FILTERS, entries));
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
            entry.put(FLUID, StackData.saveFluid(copy));
            entries.add(entry);
        }
        StackData.update(stack, tag -> tag.put(FLUID_FILTERS, entries));
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

    public static final class CompiledFilter {
        private static final int HASH_LOOKUP_THRESHOLD = 5;
        private static final Entry[] NO_ENTRIES = new Entry[0];
        private static final FluidEntry[] NO_FLUID_ENTRIES = new FluidEntry[0];
        public static final CompiledFilter ALLOW_ALL = new CompiledFilter(Mode.ALLOW_ALL, true, false, false,
                NO_ENTRIES, NO_FLUID_ENTRIES, null, null);

        private final Mode mode;
        private final boolean whitelist;
        private final boolean matchNbt;
        private final boolean matchDurability;
        private final Entry[] entries;
        private final FluidEntry[] fluidEntries;
        private final Set<ItemFilterKey> itemKeys;
        private final Set<FluidStackKey> fluidKeys;

        private CompiledFilter(Mode mode, boolean whitelist, boolean matchNbt, boolean matchDurability,
                Entry[] entries, FluidEntry[] fluidEntries, Set<ItemFilterKey> itemKeys,
                Set<FluidStackKey> fluidKeys) {
            this.mode = mode;
            this.whitelist = whitelist;
            this.matchNbt = matchNbt;
            this.matchDurability = matchDurability;
            this.entries = entries;
            this.fluidEntries = fluidEntries;
            this.itemKeys = itemKeys;
            this.fluidKeys = fluidKeys;
        }

        private static CompiledFilter list(boolean whitelist, boolean matchNbt, boolean matchDurability, Entry[] entries,
                FluidEntry[] fluidEntries) {
            return new CompiledFilter(Mode.LIST, whitelist, matchNbt, matchDurability, entries, fluidEntries,
                    compileItemKeys(entries, matchNbt, matchDurability), compileFluidKeys(fluidEntries));
        }

        public boolean matches(ItemStack candidate) {
            if (mode == Mode.ALLOW_ALL) {
                return true;
            }
            if (candidate.isEmpty()) {
                return false;
            }
            return switch (mode) {
                case LIST -> matchesList(candidate);
                case ALLOW_ALL -> true;
            };
        }

        private boolean matchesList(ItemStack candidate) {
            if (entries.length == 0) {
                return true;
            }
            boolean matched = itemKeys == null
                    ? matchesItemEntries(candidate)
                    : itemKeys.contains(ItemFilterKey.of(candidate, matchNbt, matchDurability));
            return whitelist == matched;
        }

        private boolean matchesItemEntries(ItemStack candidate) {
            for (Entry entry : entries) {
                if (matchesItemSample(entry.stack(), candidate, matchNbt, matchDurability)) {
                    return true;
                }
            }
            return false;
        }

        public boolean matchesFluid(FluidStack candidate) {
            if (mode != Mode.LIST || fluidEntries.length == 0) {
                return true;
            }
            if (candidate.isEmpty()) {
                return false;
            }
            boolean matched = fluidKeys == null ? matchesFluidEntries(candidate)
                    : fluidKeys.contains(FluidStackKey.of(candidate));
            return whitelist == matched;
        }

        private boolean matchesFluidEntries(FluidStack candidate) {
            for (FluidEntry entry : fluidEntries) {
                if (FluidStack.isSameFluidSameComponents(entry.stack(), candidate)) {
                    return true;
                }
            }
            return false;
        }

        public boolean whitelist() {
            return whitelist;
        }

        public boolean hasItemRules() {
            return entries.length > 0;
        }

        public boolean hasFluidRules() {
            return mode == Mode.LIST && fluidEntries.length > 0;
        }

        private enum Mode {
            ALLOW_ALL,
            LIST
        }

        private record Entry(ItemStack stack) {
        }

        private record FluidEntry(FluidStack stack) {
        }

        private record ItemFilterKey(Item item, int damage, CompoundTag tag) {
            private static ItemFilterKey of(ItemStack stack, boolean matchNbt, boolean matchDurability) {
                int damage = matchDurability && stack.isDamageableItem() ? stack.getDamageValue() : 0;
                CompoundTag tag = matchNbt ? comparableTag(stack, matchDurability) : null;
                return new ItemFilterKey(stack.getItem(), damage, tag);
            }
        }

        private static Set<ItemFilterKey> compileItemKeys(Entry[] entries, boolean matchNbt, boolean matchDurability) {
            if (entries.length < HASH_LOOKUP_THRESHOLD) {
                return null;
            }
            Set<ItemFilterKey> keys = new HashSet<>(entries.length * 2);
            for (Entry entry : entries) {
                keys.add(ItemFilterKey.of(entry.stack(), matchNbt, matchDurability));
            }
            return keys;
        }

        private static Set<FluidStackKey> compileFluidKeys(FluidEntry[] fluidEntries) {
            if (fluidEntries.length < HASH_LOOKUP_THRESHOLD) {
                return null;
            }
            Set<FluidStackKey> keys = new HashSet<>(fluidEntries.length * 2);
            for (FluidEntry entry : fluidEntries) {
                keys.add(FluidStackKey.of(entry.stack()));
            }
            return keys;
        }
    }
}
