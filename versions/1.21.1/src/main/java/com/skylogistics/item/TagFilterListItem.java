package com.skylogistics.item;

import com.skylogistics.menu.TagFilterListMenu;
import com.skylogistics.util.StackData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class TagFilterListItem extends Item {
    public static final int TAG_SLOTS = 6;
    public static final int MAX_TAG_LENGTH = 96;
    private static final String SAMPLE = "Sample";
    private static final String TAGS = "ItemTags";
    private static final String SLOT = "Slot";
    private static final String TAG_ID = "Tag";
    private static final String WHITELIST = "Whitelist";

    public TagFilterListItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, ignored) -> new TagFilterListMenu(id, inventory, hand),
                            Component.translatable("menu.skylogistics.tag_filter_list")),
                    buffer -> buffer.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.list_mode",
                Component.translatable(isWhitelist(stack) ? "screen.skylogistics.filter_whitelist"
                        : "screen.skylogistics.filter_blacklist")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skylogistics.tag_filter_list.entries", countTags(stack), TAG_SLOTS)
                .withStyle(ChatFormatting.GRAY));
        ItemStack sample = getSample(stack);
        if (!sample.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.skylogistics.tag_filter_list.sample", sample.getHoverName())
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        if (Screen.hasShiftDown()) {
            appendFilterContents(stack, tooltip, false);
        } else {
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static boolean isTagFilterList(ItemStack stack) {
        return stack.getItem() instanceof TagFilterListItem;
    }

    public static boolean isWhitelist(ItemStack stack) {
        CompoundTag tag = StackData.getOrEmpty(stack);
        return !tag.contains(WHITELIST) || tag.getBoolean(WHITELIST);
    }

    public static void setWhitelist(ItemStack stack, boolean whitelist) {
        StackData.update(stack, tag -> tag.putBoolean(WHITELIST, whitelist));
    }

    public static ItemStack getSample(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return tag != null && tag.contains(SAMPLE, Tag.TAG_COMPOUND)
                ? StackData.loadItem(tag.getCompound(SAMPLE))
                : ItemStack.EMPTY;
    }

    public static void setSample(ItemStack stack, ItemStack sample) {
        StackData.update(stack, tag -> {
            ItemStack copy = sample.copy();
            if (copy.isEmpty()) {
                tag.remove(SAMPLE);
                return;
            }
            copy.setCount(1);
            tag.put(SAMPLE, StackData.saveItem(copy));
        });
    }

    public static String getTag(ItemStack stack, int slot) {
        if (slot < 0 || slot >= TAG_SLOTS) {
            return "";
        }
        CompoundTag tag = StackData.get(stack);
        if (tag == null || !tag.contains(TAGS, Tag.TAG_LIST)) {
            return "";
        }
        ListTag tags = tag.getList(TAGS, Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag entry = tags.getCompound(i);
            if (entry.getInt(SLOT) == slot) {
                return normalizeTag(entry.getString(TAG_ID));
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

    public static List<String> getTags(ItemStack stack) {
        ArrayList<String> tags = new ArrayList<>(TAG_SLOTS);
        for (int i = 0; i < TAG_SLOTS; i++) {
            tags.add("");
        }
        CompoundTag tag = StackData.get(stack);
        if (tag == null || !tag.contains(TAGS, Tag.TAG_LIST)) {
            return tags;
        }
        ListTag entries = tag.getList(TAGS, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int slot = entry.getInt(SLOT);
            if (slot >= 0 && slot < TAG_SLOTS) {
                tags.set(slot, normalizeTag(entry.getString(TAG_ID)));
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

    public static void clearTags(ItemStack stack) {
        StackData.update(stack, tag -> tag.remove(TAGS));
    }

    public static void appendFilterContents(ItemStack stack, List<Component> tooltip, boolean indented) {
        String suffix = indented ? "_indented" : "";
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.contents" + suffix)
                .withStyle(ChatFormatting.GOLD));
        boolean added = false;
        List<String> tags = getTags(stack);
        for (int slot = 0; slot < TAG_SLOTS; slot++) {
            String tag = tags.get(slot);
            if (!tag.isBlank()) {
                tooltip.add(Component.translatable("tooltip.skylogistics.tag_filter_list.entry" + suffix,
                        slot + 1, "#" + tag).withStyle(ChatFormatting.GRAY));
                added = true;
            }
        }
        if (!added) {
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.empty" + suffix)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static List<TagKey<Item>> getTagKeys(ItemStack stack) {
        ArrayList<TagKey<Item>> keys = new ArrayList<>();
        for (String tag : getTags(stack)) {
            ResourceLocation id = ResourceLocation.tryParse(tag);
            if (id != null) {
                keys.add(TagKey.create(Registries.ITEM, id));
            }
        }
        return keys;
    }

    public static List<String> sampleTags(ItemStack sample) {
        if (sample.isEmpty()) {
            return List.of();
        }
        return sample.getTags()
                .map(TagKey::location)
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
    }

    public static String normalizeTag(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1).trim();
        }
        if (value.length() > MAX_TAG_LENGTH) {
            value = value.substring(0, MAX_TAG_LENGTH);
        }
        if (!value.contains(":") && !value.isBlank()) {
            value = "minecraft:" + value;
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? "" : id.toString();
    }

    private static void saveTags(ItemStack stack, List<String> tags) {
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
        StackData.update(stack, data -> data.put(TAGS, entries));
    }
}
