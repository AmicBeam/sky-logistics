package com.skylogistics.item;

import com.skylogistics.menu.TagFilterListMenu;
import com.skylogistics.util.StackData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

public class TagFilterListItem extends Item {
    public static final int TAG_SLOTS = 6;
    public static final int MAX_TAG_LENGTH = 96;
    public static final int MAX_MOD_ID_LENGTH = 64;
    public static final String FORGE_ENERGY_MOD_ID = "forge";
    public static final String BOTANIA_MANA_MOD_ID = "botania";
    public static final String ARS_NOUVEAU_SOURCE_MOD_ID = "ars_nouveau";
    private static final String SAMPLE = "Sample";
    private static final String TAGS = "ItemTags";
    private static final String FLUID_TAGS = "FluidTags";
    private static final String MODS = "Mods";
    private static final String SLOT = "Slot";
    private static final String TAG_ID = "Tag";
    private static final String MOD_ID = "ModId";
    private static final String WHITELIST = "Whitelist";

    public TagFilterListItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            clearLegacySample(stack);
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, ignored) -> new TagFilterListMenu(id, inventory, hand),
                            Component.translatable("menu.skylogistics.tag_filter_list")),
                    buffer -> buffer.writeEnum(hand));
        }
        return com.skylogistics.util.InteractionResults.sidedSuccess(level.isClientSide());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.skylogistics.filter_list.list_mode",
                Component.translatable(isWhitelist(stack) ? "screen.skylogistics.filter_whitelist"
                        : "screen.skylogistics.filter_blacklist")).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.skylogistics.tag_filter_list.entries", countTags(stack), TAG_SLOTS)
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.skylogistics.tag_filter_list.fluid_entries",
                countFluidTags(stack), TAG_SLOTS).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.skylogistics.tag_filter_list.mod_entries",
                countMods(stack), TAG_SLOTS).withStyle(ChatFormatting.GRAY));
        if (FilterListItem.showFilterContents()) {
            appendFilterContents(stack, tooltip, false);
        } else {
            tooltip.accept(Component.translatable("tooltip.skylogistics.filter_list.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static boolean isTagFilterList(ItemStack stack) {
        return stack.getItem() instanceof TagFilterListItem;
    }

    public static boolean isWhitelist(ItemStack stack) {
        CompoundTag tag = StackData.getOrEmpty(stack);
        return !tag.contains(WHITELIST) || tag.getBooleanOr(WHITELIST, false);
    }

    public static void setWhitelist(ItemStack stack, boolean whitelist) {
        StackData.update(stack, tag -> tag.putBoolean(WHITELIST, whitelist));
    }

    public static void clearLegacySample(ItemStack stack) {
        StackData.update(stack, tag -> tag.remove(SAMPLE));
    }

    public static String getTag(ItemStack stack, int slot) {
        if (slot < 0 || slot >= TAG_SLOTS) {
            return "";
        }
        CompoundTag tag = StackData.get(stack);
        if (tag == null || !tag.contains(TAGS)) {
            return "";
        }
        ListTag tags = tag.getListOrEmpty(TAGS);
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag entry = tags.getCompoundOrEmpty(i);
            if (entry.getIntOr(SLOT, 0) == slot) {
                return normalizeTag(entry.getStringOr(TAG_ID, ""));
            }
        }
        return "";
    }

    public static void setTag(ItemStack stack, int slot, String tag) {
        if (slot < 0 || slot >= TAG_SLOTS) {
            return;
        }
        List<String> tags = getTags(stack);
        tags.set(slot, normalizeTag(tag));
        saveTags(stack, tags);
    }

    public static String getFluidTag(ItemStack stack, int slot) { return getTag(stack, slot, FLUID_TAGS); }
    public static void setFluidTag(ItemStack stack, int slot, String tag) {
        if (slot < 0 || slot >= TAG_SLOTS) return;
        List<String> tags = getFluidTags(stack);
        tags.set(slot, normalizeTag(tag));
        saveTags(stack, tags, FLUID_TAGS);
    }
    public static List<String> getFluidTags(ItemStack stack) { return getTags(stack, FLUID_TAGS); }

    public static List<String> getTags(ItemStack stack) {
        ArrayList<String> tags = new ArrayList<>(TAG_SLOTS);
        for (int i = 0; i < TAG_SLOTS; i++) {
            tags.add("");
        }
        CompoundTag tag = StackData.get(stack);
        if (tag == null || !tag.contains(TAGS)) {
            return tags;
        }
        ListTag entries = tag.getListOrEmpty(TAGS);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompoundOrEmpty(i);
            int slot = entry.getIntOr(SLOT, 0);
            if (slot >= 0 && slot < TAG_SLOTS) {
                tags.set(slot, normalizeTag(entry.getStringOr(TAG_ID, "")));
            }
        }
        return tags;
    }

    public static int countTags(ItemStack stack) {
        int count = 0;
        for (String tag : getTags(stack)) {
            if (!tag.isBlank()) {
                count++;
            }
        }
        return count;
    }

    public static int countFluidTags(ItemStack stack) {
        int count = 0;
        for (String tag : getFluidTags(stack)) if (!tag.isBlank()) count++;
        return count;
    }

    public static String getMod(ItemStack stack, int slot) {
        if (slot < 0 || slot >= TAG_SLOTS) return "";
        return getMods(stack).get(slot);
    }

    public static void setMod(ItemStack stack, int slot, String modId) {
        if (slot < 0 || slot >= TAG_SLOTS) return;
        List<String> mods = getMods(stack);
        mods.set(slot, normalizeModId(modId));
        saveMods(stack, mods);
    }

    public static List<String> getMods(ItemStack stack) {
        ArrayList<String> mods = new ArrayList<>(TAG_SLOTS);
        for (int i = 0; i < TAG_SLOTS; i++) mods.add("");
        CompoundTag data = StackData.get(stack);
        if (data == null || !data.contains(MODS)) return mods;
        ListTag entries = data.getListOrEmpty(MODS);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompoundOrEmpty(i);
            int slot = entry.getIntOr(SLOT, 0);
            if (slot >= 0 && slot < TAG_SLOTS) mods.set(slot, normalizeModId(entry.getStringOr(MOD_ID, "")));
        }
        return mods;
    }

    public static int countMods(ItemStack stack) {
        int count = 0;
        for (String mod : getMods(stack)) if (!mod.isBlank()) count++;
        return count;
    }

    public static List<String> availableMods() {
        ArrayList<String> mods = new ArrayList<>();
        mods.add(FORGE_ENERGY_MOD_ID);
        net.neoforged.fml.ModList.get().getMods().stream().map(info -> info.getModId())
                .filter(id -> !FORGE_ENERGY_MOD_ID.equals(id)).sorted().forEach(mods::add);
        return mods;
    }

    public static String normalizeModId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith("@")) value = value.substring(1).trim();
        if (value.length() > MAX_MOD_ID_LENGTH) value = value.substring(0, MAX_MOD_ID_LENGTH);
        return value.matches("[a-z][a-z0-9_]*") ? value : "";
    }

    public static void clearTags(ItemStack stack) {
        StackData.update(stack, tag -> {
            tag.remove(TAGS);
            tag.remove(FLUID_TAGS);
            tag.remove(MODS);
        });
    }

    public static void appendFilterContents(ItemStack stack, Consumer<Component> tooltip, boolean indented) {
        String suffix = indented ? "_indented" : "";
        tooltip.accept(Component.translatable("tooltip.skylogistics.filter_list.contents" + suffix)
                .withStyle(ChatFormatting.GOLD));
        boolean added = false;
        List<String> tags = getTags(stack);
        for (int slot = 0; slot < TAG_SLOTS; slot++) {
            String tag = tags.get(slot);
            if (!tag.isBlank()) {
                tooltip.accept(Component.translatable("tooltip.skylogistics.tag_filter_list.entry" + suffix,
                        slot + 1, "#" + tag).withStyle(ChatFormatting.GRAY));
                added = true;
            }
        }
        List<String> fluidTags = getFluidTags(stack);
        for (int slot = 0; slot < TAG_SLOTS; slot++) {
            String tag = fluidTags.get(slot);
            if (!tag.isBlank()) {
                tooltip.accept(Component.translatable("tooltip.skylogistics.tag_filter_list.fluid_entry" + suffix,
                        slot + 1, "#" + tag).withStyle(ChatFormatting.AQUA));
                added = true;
            }
        }
        List<String> mods = getMods(stack);
        for (int slot = 0; slot < TAG_SLOTS; slot++) {
            String mod = mods.get(slot);
            if (!mod.isBlank()) {
                tooltip.accept(Component.translatable("tooltip.skylogistics.tag_filter_list.mod_entry" + suffix,
                        slot + 1, "@" + mod).withStyle(ChatFormatting.LIGHT_PURPLE));
                added = true;
            }
        }
        if (!added) {
            tooltip.accept(Component.translatable("tooltip.skylogistics.filter_list.empty" + suffix)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static List<TagKey<Item>> getTagKeys(ItemStack stack) {
        ArrayList<TagKey<Item>> keys = new ArrayList<>();
        for (String tag : getTags(stack)) {
            Identifier id = Identifier.tryParse(tag);
            if (id != null) {
                keys.add(TagKey.create(Registries.ITEM, id));
            }
        }
        return keys;
    }

    public static List<TagKey<Fluid>> getFluidTagKeys(ItemStack stack) {
        ArrayList<TagKey<Fluid>> keys = new ArrayList<>();
        for (String tag : getFluidTags(stack)) {
            Identifier id = Identifier.tryParse(tag);
            if (id != null) keys.add(TagKey.create(Registries.FLUID, id));
        }
        return keys;
    }

    public static List<String> sampleTags(ItemStack sample) {
        if (sample.isEmpty()) {
            return List.of();
        }
        return sample.typeHolder().tags()
                .map(TagKey::location)
                .map(Identifier::toString)
                .sorted()
                .toList();
    }

    public static List<String> availableTags(boolean fluid) {
        return (fluid ? net.minecraft.core.registries.BuiltInRegistries.FLUID
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM)
                .getTags().map(named -> named.key().location()).map(Identifier::toString).sorted().toList();
    }

    public static String sampleModId(ItemStack sample) {
        return sample.isEmpty() ? ""
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(sample.getItem()).getNamespace();
    }

    public static String normalizeTag(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1).trim();
        }
        if (value.isBlank()) {
            return "";
        }
        if (value.length() > MAX_TAG_LENGTH) {
            value = value.substring(0, MAX_TAG_LENGTH);
        }
        int separator = value.indexOf(':');
        if (separator < 0) {
            value = "minecraft:" + value;
        } else if (separator == 0 || separator == value.length() - 1) {
            return "";
        }
        Identifier id = Identifier.tryParse(value);
        return id == null || id.getNamespace().isBlank() || id.getPath().isBlank() ? "" : id.toString();
    }

    private static void saveTags(ItemStack stack, List<String> tags) {
        saveTags(stack, tags, TAGS);
    }

    private static String getTag(ItemStack stack, int slot, String key) {
        if (slot < 0 || slot >= TAG_SLOTS) return "";
        return getTags(stack, key).get(slot);
    }

    private static List<String> getTags(ItemStack stack, String key) {
        ArrayList<String> tags = new ArrayList<>(TAG_SLOTS);
        for (int i = 0; i < TAG_SLOTS; i++) tags.add("");
        CompoundTag data = StackData.get(stack);
        if (data == null || !data.contains(key)) return tags;
        ListTag entries = data.getListOrEmpty(key);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompoundOrEmpty(i);
            int slot = entry.getIntOr(SLOT, 0);
            if (slot >= 0 && slot < TAG_SLOTS) tags.set(slot, normalizeTag(entry.getStringOr(TAG_ID, "")));
        }
        return tags;
    }

    private static void saveTags(ItemStack stack, List<String> tags, String key) {
        ListTag entries = new ListTag();
        for (int slot = 0; slot < Math.min(TAG_SLOTS, tags.size()); slot++) {
            String tag = normalizeTag(tags.get(slot));
            if (tag.isBlank()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(SLOT, slot);
            entry.putString(TAG_ID, tag);
            entries.add(entry);
        }
        StackData.update(stack, data -> data.put(key, entries));
    }

    private static void saveMods(ItemStack stack, List<String> mods) {
        ListTag entries = new ListTag();
        for (int slot = 0; slot < Math.min(TAG_SLOTS, mods.size()); slot++) {
            String mod = normalizeModId(mods.get(slot));
            if (mod.isBlank()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt(SLOT, slot);
            entry.putString(MOD_ID, mod);
            entries.add(entry);
        }
        StackData.update(stack, data -> data.put(MODS, entries));
    }
}
