package com.skylogistics.item;

import com.skylogistics.menu.FilterListMenu;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.registry.ModItems;
import com.skylogistics.storage.FluidStackKey;
import com.skylogistics.util.StackData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public class FilterListItem extends Item {
    public static final int FILTER_SLOTS = 18;
    private static final String FILTERS = "Filters";
    private static final String FLUID_FILTERS = "FluidFilters";
    private static final String CHEMICAL_FILTERS = "ChemicalFilters";
    private static final String CHEMICAL = "Chemical";
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
        HolderLookup.Provider registries = context.registries() == null
                ? StackData.builtinRegistries() : context.registries();
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.list_mode",
                Component.translatable(isWhitelist(stack) ? "screen.skylogistics.filter_whitelist"
                        : "screen.skylogistics.filter_blacklist")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.nbt", matchNbt(stack))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.durability", matchDurability(stack))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.entries",
                countFilters(stack, registries), FILTER_SLOTS)
                .withStyle(ChatFormatting.GRAY));
        int fluids = countFluidFilters(stack, registries);
        if (fluids > 0) {
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.fluids", fluids, FILTER_SLOTS)
                    .withStyle(ChatFormatting.AQUA));
        }
        int chemicals = countChemicalFilters(stack);
        if (chemicals > 0) tooltip.add(Component.translatable(
                "tooltip.skylogistics.filter_list.chemicals", chemicals, FILTER_SLOTS)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (showFilterContents()) {
            appendFilterContents(stack, registries, tooltip, false);
        } else {
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
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
        return getFilter(stack, slot, StackData.builtinRegistries());
    }

    public static ItemStack getFilter(ItemStack stack, int slot, HolderLookup.Provider registries) {
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
                return StackData.loadItem(entry.getCompound(STACK), registries);
            }
        }
        return ItemStack.EMPTY;
    }

    public static void setFilter(ItemStack stack, int slot, ItemStack filter) {
        setFilter(stack, slot, filter, StackData.builtinRegistries());
    }

    public static void setFilter(ItemStack stack, int slot, ItemStack filter, HolderLookup.Provider registries) {
        if (slot < 0 || slot >= FILTER_SLOTS) {
            return;
        }
        List<ItemStack> filters = getFilters(stack, registries);
        ItemStack copy = filter.copy();
        if (!copy.isEmpty()) {
            copy.setCount(1);
        }
        filters.set(slot, copy);
        saveFilters(stack, filters, registries);
        if (!copy.isEmpty()) {
            setFluidFilter(stack, slot, FluidStack.EMPTY, registries);
        }
    }

    public static FluidStack getFluidFilter(ItemStack stack, int slot) {
        return getFluidFilter(stack, slot, StackData.builtinRegistries());
    }

    public static FluidStack getFluidFilter(ItemStack stack, int slot, HolderLookup.Provider registries) {
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
                return StackData.loadFluid(entry.getCompound(FLUID), registries);
            }
        }
        return FluidStack.EMPTY;
    }

    public static void setFluidFilter(ItemStack stack, int slot, FluidStack filter) {
        setFluidFilter(stack, slot, filter, StackData.builtinRegistries());
    }

    public static void setFluidFilter(ItemStack stack, int slot, FluidStack filter, HolderLookup.Provider registries) {
        if (slot < 0 || slot >= FILTER_SLOTS) {
            return;
        }
        List<FluidStack> filters = getFluidFilters(stack, registries);
        FluidStack copy = filter.copy();
        if (!copy.isEmpty()) {
            copy.setAmount(1);
        }
        filters.set(slot, copy);
        saveFluidFilters(stack, filters, registries);
        if (!copy.isEmpty()) {
            setFilter(stack, slot, ItemStack.EMPTY, registries);
        }
    }

    public static String getChemicalFilter(ItemStack stack, int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return "";
        CompoundTag tag = StackData.get(stack);
        if (tag == null || !tag.contains(CHEMICAL_FILTERS, Tag.TAG_LIST)) return "";
        ListTag filters = tag.getList(CHEMICAL_FILTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < filters.size(); i++) {
            CompoundTag entry = filters.getCompound(i);
            if (entry.getInt(SLOT) == slot) return entry.getString(CHEMICAL);
        }
        return "";
    }

    public static void setChemicalFilter(ItemStack stack, int slot, String chemical) {
        setChemicalFilter(stack, slot, chemical, StackData.builtinRegistries());
    }

    public static void setChemicalFilter(ItemStack stack, int slot, String chemical,
            HolderLookup.Provider registries) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;
        List<String> values = getChemicalFilters(stack);
        values.set(slot, chemical == null ? "" : chemical);
        ListTag entries = new ListTag();
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt(SLOT, i);
            entry.putString(CHEMICAL, values.get(i));
            entries.add(entry);
        }
        StackData.update(stack, tag -> tag.put(CHEMICAL_FILTERS, entries));
        if (!values.get(slot).isEmpty()) {
            setFilter(stack, slot, ItemStack.EMPTY, registries);
            setFluidFilter(stack, slot, FluidStack.EMPTY, registries);
        }
    }

    public static List<String> getChemicalFilters(ItemStack stack) {
        List<String> result = new ArrayList<>(java.util.Collections.nCopies(FILTER_SLOTS, ""));
        for (int slot = 0; slot < FILTER_SLOTS; slot++) result.set(slot, getChemicalFilter(stack, slot));
        return result;
    }

    public static ItemStack getDisplayFilter(ItemStack stack, int slot) {
        return getDisplayFilter(stack, slot, StackData.builtinRegistries());
    }

    public static ItemStack getDisplayFilter(ItemStack stack, int slot, HolderLookup.Provider registries) {
        ItemStack item = getFilter(stack, slot, registries);
        if (!item.isEmpty()) {
            return item;
        }
        ItemStack fluid = fluidDisplayStack(getFluidFilter(stack, slot, registries));
        if (!fluid.isEmpty()) return fluid;
        return getChemicalFilter(stack, slot).isEmpty() ? ItemStack.EMPTY : Items.GLASS_BOTTLE.getDefaultInstance();
    }

    public static void clearFilters(ItemStack stack) {
        StackData.update(stack, tag -> {
            tag.remove(FILTERS);
            tag.remove(FLUID_FILTERS);
            tag.remove(CHEMICAL_FILTERS);
            tag.remove("FilterMode");
            tag.remove("Attributes");
            tag.remove("MatchAllAttributes");
        });
    }

    public static int countFilters(ItemStack stack) {
        return countFilters(stack, StackData.builtinRegistries());
    }

    public static int countFilters(ItemStack stack, HolderLookup.Provider registries) {
        return countStoredFilters(stack, FILTERS, STACK);
    }

    public static int countFluidFilters(ItemStack stack) {
        return countFluidFilters(stack, StackData.builtinRegistries());
    }

    public static int countFluidFilters(ItemStack stack, HolderLookup.Provider registries) {
        return countStoredFilters(stack, FLUID_FILTERS, FLUID);
    }

    private static int countStoredFilters(ItemStack stack, String listKey, String valueKey) {
        CompoundTag tag = StackData.get(stack);
        if (tag == null || !tag.contains(listKey, Tag.TAG_LIST)) {
            return 0;
        }
        int count = 0;
        ListTag entries = tag.getList(listKey, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int slot = entry.getInt(SLOT);
            if (slot >= 0 && slot < FILTER_SLOTS && !entry.getCompound(valueKey).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static int countChemicalFilters(ItemStack stack) {
        int count = 0;
        for (String key : getChemicalFilters(stack)) if (!key.isEmpty()) count++;
        return count;
    }

    public static boolean hasAnyFilters(ItemStack stack) {
        return countFilters(stack) + countFluidFilters(stack) + countChemicalFilters(stack)
                + TagFilterListItem.countTags(stack) + TagFilterListItem.countFluidTags(stack)
                + TagFilterListItem.countMods(stack) > 0;
    }

    public static boolean isFilterItem(ItemStack stack) {
        return stack.is(ModItems.FILTER_LIST.get()) || stack.is(ModItems.TAG_FILTER_LIST.get());
    }

    public static int countItemRules(ItemStack stack) {
        return TagFilterListItem.isTagFilterList(stack)
                ? TagFilterListItem.countTags(stack) + TagFilterListItem.countMods(stack) : countFilters(stack);
    }

    public static void appendFilterContentsOrHint(ItemStack stack, List<Component> tooltip, TooltipFlag flag) {
        appendFilterContentsOrHint(stack, StackData.builtinRegistries(), tooltip, flag);
    }

    public static void appendFilterContentsOrHint(ItemStack stack, HolderLookup.Provider registries,
            List<Component> tooltip, TooltipFlag flag) {
        if (TagFilterListItem.isTagFilterList(stack)) {
            if (showFilterContents()) {
                TagFilterListItem.appendFilterContents(stack, tooltip, true);
            } else {
                tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.hold_shift")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }
        if (showFilterContents()) {
            appendFilterContents(stack, registries, tooltip, true);
        } else {
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static void appendFilterContents(ItemStack stack, List<Component> tooltip, boolean indented) {
        appendFilterContents(stack, StackData.builtinRegistries(), tooltip, indented);
    }

    public static void appendFilterContents(ItemStack stack, HolderLookup.Provider registries,
            List<Component> tooltip, boolean indented) {
        String suffix = indented ? "_indented" : "";
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.contents" + suffix)
                .withStyle(ChatFormatting.GOLD));
        boolean added = false;
        List<ItemStack> filters = getFilters(stack, registries);
        List<FluidStack> fluidFilters = getFluidFilters(stack, registries);
        List<String> chemicalFilters = getChemicalFilters(stack);
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            ItemStack filter = filters.get(slot);
            if (!filter.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.entry.item" + suffix,
                        slot + 1, filter.getHoverName()).withStyle(ChatFormatting.GRAY));
                added = true;
            }
            FluidStack fluid = fluidFilters.get(slot);
            if (!fluid.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.entry.fluid" + suffix,
                        slot + 1, fluid.getHoverName()).withStyle(ChatFormatting.AQUA));
                added = true;
            }
            String chemical = chemicalFilters.get(slot);
            if (!chemical.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.entry.chemical" + suffix,
                        slot + 1, chemical).withStyle(ChatFormatting.LIGHT_PURPLE));
                added = true;
            }
        }
        if (!added) {
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.empty" + suffix)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static boolean showFilterContents() {
        return Screen.hasShiftDown();
    }

    public static List<ItemStack> getFilters(ItemStack stack) {
        return getFilters(stack, StackData.builtinRegistries());
    }

    public static List<ItemStack> getFilters(ItemStack stack, HolderLookup.Provider registries) {
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
                filters.set(slot, StackData.loadItem(entry.getCompound(STACK), registries));
            }
        }
        return filters;
    }

    public static List<FluidStack> getFluidFilters(ItemStack stack) {
        return getFluidFilters(stack, StackData.builtinRegistries());
    }

    public static List<FluidStack> getFluidFilters(ItemStack stack, HolderLookup.Provider registries) {
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
                filters.set(slot, StackData.loadFluid(entry.getCompound(FLUID), registries));
            }
        }
        return filters;
    }

    public static boolean matches(ItemStack filterList, ItemStack candidate) {
        return matches(filterList, candidate, StackData.builtinRegistries());
    }

    public static boolean matches(ItemStack filterList, ItemStack candidate, HolderLookup.Provider registries) {
        return compile(filterList, registries).matches(candidate);
    }

    public static boolean matchesFluid(ItemStack filterList, FluidStack candidate) {
        return matchesFluid(filterList, candidate, StackData.builtinRegistries());
    }

    public static boolean matchesFluid(ItemStack filterList, FluidStack candidate, HolderLookup.Provider registries) {
        return compile(filterList, registries).matchesFluid(candidate);
    }

    public static CompiledFilter compile(ItemStack filterList) {
        return compile(filterList, StackData.builtinRegistries());
    }

    public static CompiledFilter compile(ItemStack filterList, HolderLookup.Provider registries) {
        if (filterList.isEmpty()) {
            return CompiledFilter.ALLOW_ALL;
        }
        if (TagFilterListItem.isTagFilterList(filterList)) {
            List<TagKey<Item>> tagKeys = TagFilterListItem.getTagKeys(filterList);
            List<TagKey<Fluid>> fluidTagKeys = TagFilterListItem.getFluidTagKeys(filterList);
            Set<String> mods = new HashSet<>(TagFilterListItem.getMods(filterList));
            mods.remove("");
            if (tagKeys.isEmpty() && fluidTagKeys.isEmpty() && mods.isEmpty()) {
                return CompiledFilter.ALLOW_ALL;
            }
            return CompiledFilter.tagList(TagFilterListItem.isWhitelist(filterList), tagKeys, fluidTagKeys, mods);
        }
        boolean whitelist = isWhitelist(filterList);
        List<ItemStack> filters = getFilters(filterList, registries);
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
        for (FluidStack fluid : getFluidFilters(filterList, registries)) {
            if (fluid.isEmpty()) {
                continue;
            }
            FluidStack copy = fluid.copy();
            copy.setAmount(1);
            fluidEntries.add(new CompiledFilter.FluidEntry(copy));
        }
        Set<String> chemicalKeys = new HashSet<>();
        for (String key : getChemicalFilters(filterList)) if (!key.isEmpty()) chemicalKeys.add(key);
        if (entries.isEmpty() && fluidEntries.isEmpty() && chemicalKeys.isEmpty()) {
            return CompiledFilter.ALLOW_ALL;
        }
        return CompiledFilter.list(whitelist, nbt, durability, entries.toArray(CompiledFilter.Entry[]::new),
                fluidEntries.toArray(CompiledFilter.FluidEntry[]::new), chemicalKeys);
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
        return comparableComponents(sample, matchDurability).equals(comparableComponents(candidate, matchDurability));
    }

    private static DataComponentPatch comparableComponents(ItemStack stack, boolean includeDurability) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        if (!includeDurability && copy.isDamageableItem()) {
            copy.setDamageValue(0);
        }
        return copy.getComponentsPatch();
    }

    private static void saveFilters(ItemStack stack, List<ItemStack> filters, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (int slot = 0; slot < Math.min(FILTER_SLOTS, filters.size()); slot++) {
            ItemStack filter = filters.get(slot);
            if (filter.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(SLOT, slot);
            entry.put(STACK, StackData.saveItem(filter, registries));
            entries.add(entry);
        }
        StackData.update(stack, tag -> tag.put(FILTERS, entries));
    }

    private static void saveFluidFilters(ItemStack stack, List<FluidStack> filters,
            HolderLookup.Provider registries) {
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
            entry.put(FLUID, StackData.saveFluid(copy, registries));
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
        // Small filters are faster as allocation-free linear scans. Hash probes allocate stack keys,
        // and component/NBT-aware probes may also copy their data.
        private static final int HASH_LOOKUP_THRESHOLD = 16;
        private static final Entry[] NO_ENTRIES = new Entry[0];
        private static final FluidEntry[] NO_FLUID_ENTRIES = new FluidEntry[0];
        private static final List<TagKey<Item>> NO_TAG_ENTRIES = List.of();
        private static final List<TagKey<Fluid>> NO_FLUID_TAG_ENTRIES = List.of();
        public static final CompiledFilter ALLOW_ALL = new CompiledFilter(Mode.ALLOW_ALL, true, false, false,
                NO_ENTRIES, NO_FLUID_ENTRIES, NO_TAG_ENTRIES, NO_FLUID_TAG_ENTRIES, null, null, Set.of(), Set.of());

        private final Mode mode;
        private final boolean whitelist;
        private final boolean matchNbt;
        private final boolean matchDurability;
        private final Entry[] entries;
        private final FluidEntry[] fluidEntries;
        private final List<TagKey<Item>> tagEntries;
        private final List<TagKey<Fluid>> fluidTagEntries;
        private final Set<ItemFilterKey> itemKeys;
        private final Set<FluidStackKey> fluidKeys;
        private final Set<String> chemicalKeys;
        private final Set<String> modIds;

        private CompiledFilter(Mode mode, boolean whitelist, boolean matchNbt, boolean matchDurability,
                Entry[] entries, FluidEntry[] fluidEntries, List<TagKey<Item>> tagEntries,
                List<TagKey<Fluid>> fluidTagEntries, Set<ItemFilterKey> itemKeys,
                Set<FluidStackKey> fluidKeys, Set<String> chemicalKeys, Set<String> modIds) {
            this.mode = mode;
            this.whitelist = whitelist;
            this.matchNbt = matchNbt;
            this.matchDurability = matchDurability;
            this.entries = entries;
            this.fluidEntries = fluidEntries;
            this.tagEntries = tagEntries;
            this.fluidTagEntries = fluidTagEntries;
            this.itemKeys = itemKeys;
            this.fluidKeys = fluidKeys;
            this.chemicalKeys = chemicalKeys;
            this.modIds = modIds;
        }

        private static CompiledFilter list(boolean whitelist, boolean matchNbt, boolean matchDurability, Entry[] entries,
                FluidEntry[] fluidEntries, Set<String> chemicalKeys) {
            return new CompiledFilter(Mode.LIST, whitelist, matchNbt, matchDurability, entries, fluidEntries,
                    NO_TAG_ENTRIES, NO_FLUID_TAG_ENTRIES, compileItemKeys(entries, matchNbt, matchDurability),
                    compileFluidKeys(fluidEntries), Set.copyOf(chemicalKeys), Set.of());
        }

        private static CompiledFilter tagList(boolean whitelist, List<TagKey<Item>> tagEntries,
                List<TagKey<Fluid>> fluidTagEntries, Set<String> modIds) {
            return new CompiledFilter(Mode.LIST, whitelist, false, false, NO_ENTRIES, NO_FLUID_ENTRIES,
                    List.copyOf(tagEntries), List.copyOf(fluidTagEntries), null, null, Set.of(), Set.copyOf(modIds));
        }

        private static CompiledFilter modList(boolean whitelist, Set<String> modIds) {
            return new CompiledFilter(Mode.MOD, whitelist, false, false, NO_ENTRIES, NO_FLUID_ENTRIES,
                    NO_TAG_ENTRIES, NO_FLUID_TAG_ENTRIES, null, null, Set.of(), Set.copyOf(modIds));
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
                case MOD -> matchesMod(candidate.getItem().builtInRegistryHolder().key().location().getNamespace());
                case ALLOW_ALL -> true;
            };
        }

        private boolean matchesList(ItemStack candidate) {
            if (entries.length == 0 && tagEntries.isEmpty() && modIds.isEmpty()) {
                return true;
            }
            boolean matched = itemKeys == null
                    ? matchesItemEntries(candidate)
                    : itemKeys.contains(ItemFilterKey.of(candidate, matchNbt, matchDurability));
            if (!matched) {
                matched = matchesTagEntries(candidate);
            }
            if (!matched) matched = modIds.contains(candidate.getItem().builtInRegistryHolder().key()
                    .location().getNamespace());
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

        private boolean matchesTagEntries(ItemStack candidate) {
            for (TagKey<Item> tag : tagEntries) {
                if (candidate.is(tag)) {
                    return true;
                }
            }
            return false;
        }

        public boolean matchesFluid(FluidStack candidate) {
            if (mode == Mode.MOD) return !candidate.isEmpty() && matchesMod(candidate.getFluid()
                    .builtInRegistryHolder().key().location().getNamespace());
            if (mode != Mode.LIST || fluidEntries.length == 0 && fluidTagEntries.isEmpty() && modIds.isEmpty()) {
                return true;
            }
            if (candidate.isEmpty()) {
                return false;
            }
            boolean matched = fluidKeys == null ? matchesFluidEntries(candidate)
                    : fluidKeys.contains(FluidStackKey.of(candidate));
            if (!matched) {
                for (TagKey<Fluid> tag : fluidTagEntries) {
                    if (candidate.getFluid().builtInRegistryHolder().is(tag)) {
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) matched = modIds.contains(candidate.getFluid().builtInRegistryHolder().key()
                    .location().getNamespace());
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
            return mode == Mode.MOD || entries.length > 0 || !tagEntries.isEmpty() || !modIds.isEmpty();
        }

        public List<ItemStack> itemSamples() {
            if (entries.length == 0) {
                return List.of();
            }
            List<ItemStack> samples = new ArrayList<>(entries.length);
            for (Entry entry : entries) {
                samples.add(entry.stack().copy());
            }
            return samples;
        }

        public List<TagKey<Item>> itemTags() {
            return tagEntries;
        }

        public boolean hasFluidRules() {
            return mode == Mode.MOD || mode == Mode.LIST
                    && (fluidEntries.length > 0 || !fluidTagEntries.isEmpty() || !modIds.isEmpty());
        }

        public List<TagKey<Fluid>> fluidTags() {
            return fluidTagEntries;
        }

        public boolean hasChemicalRules() { return mode == Mode.MOD || !chemicalKeys.isEmpty() || !modIds.isEmpty(); }

        public boolean matchesChemical(ChemicalStackView candidate) {
            if (mode == Mode.MOD) return candidate != null && !candidate.isEmpty()
                    && matchesMod(namespace(candidate.chemicalKey()));
            if (mode != Mode.LIST || chemicalKeys.isEmpty() && modIds.isEmpty()) return true;
            if (candidate == null || candidate.isEmpty()) return false;
            return whitelist == (chemicalKeys.contains(candidate.chemicalKey())
                    || modIds.contains(namespace(candidate.chemicalKey())));
        }

        public boolean hasSoulRules() {
            if (mode == Mode.MOD || modIds.contains("industrialforegoingsouls")) return true;
            for (Entry entry : entries) {
                if ("industrialforegoingsouls".equals(entry.stack().getItem().builtInRegistryHolder().key()
                        .location().getNamespace())) return true;
            }
            return false;
        }

        public boolean matchesSoul() {
            if (mode == Mode.ALLOW_ALL) return true;
            boolean matched = modIds.contains("industrialforegoingsouls");
            if (!matched) {
                for (Entry entry : entries) {
                    if ("industrialforegoingsouls".equals(entry.stack().getItem().builtInRegistryHolder().key()
                            .location().getNamespace())) {
                        matched = true;
                        break;
                    }
                }
            }
            return whitelist == matched;
        }

        public boolean hasEnergyRules() { return !modIds.isEmpty(); }
        public boolean matchesEnergy(String modId) { return modIds.isEmpty() || matchesMod(modId); }
        private boolean matchesMod(String modId) { return whitelist == modIds.contains(modId); }
        private static String namespace(String id) {
            int separator = id == null ? -1 : id.indexOf(':');
            return separator > 0 ? id.substring(0, separator) : id;
        }

        public List<FluidStack> fluidSamples() {
            if (fluidEntries.length == 0) {
                return List.of();
            }
            List<FluidStack> samples = new ArrayList<>(fluidEntries.length);
            for (FluidEntry entry : fluidEntries) {
                samples.add(entry.stack().copy());
            }
            return samples;
        }

        private enum Mode {
            ALLOW_ALL,
            LIST,
            MOD
        }

        private record Entry(ItemStack stack) {
        }

        private record FluidEntry(FluidStack stack) {
        }

        private record ItemFilterKey(Item item, int damage, DataComponentPatch components) {
            private static ItemFilterKey of(ItemStack stack, boolean matchNbt, boolean matchDurability) {
                int damage = matchDurability && stack.isDamageableItem() ? stack.getDamageValue() : 0;
                DataComponentPatch components = matchNbt ? comparableComponents(stack, matchDurability) : null;
                return new ItemFilterKey(stack.getItem(), damage, components);
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
