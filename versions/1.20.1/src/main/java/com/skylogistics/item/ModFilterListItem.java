package com.skylogistics.item;

import com.skylogistics.menu.TagFilterListMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkHooks;

public class ModFilterListItem extends Item {
    public static final String FORGE_ENERGY_MOD_ID = "forge";
    public static final int MOD_SLOTS = 6;
    public static final int MAX_MOD_ID_LENGTH = 64;
    private static final String MODS = "Mods";
    private static final String SLOT = "Slot";
    private static final String MOD_ID = "ModId";
    private static final String WHITELIST = "Whitelist";

    public ModFilterListItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, ignored) -> new TagFilterListMenu(id, inventory, hand),
                            Component.translatable("menu.skylogistics.mod_filter_list")),
                    buffer -> buffer.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.list_mode",
                Component.translatable(isWhitelist(stack) ? "screen.skylogistics.filter_whitelist"
                        : "screen.skylogistics.filter_blacklist")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skylogistics.mod_filter_list.entries", countMods(stack), MOD_SLOTS)
                .withStyle(ChatFormatting.GRAY));
        if (Screen.hasShiftDown()) appendFilterContents(stack, tooltip, false);
        else tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.hold_shift")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static boolean isModFilterList(ItemStack stack) { return stack.getItem() instanceof ModFilterListItem; }
    public static boolean isWhitelist(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return !tag.contains(WHITELIST) || tag.getBoolean(WHITELIST);
    }
    public static void setWhitelist(ItemStack stack, boolean whitelist) {
        stack.getOrCreateTag().putBoolean(WHITELIST, whitelist);
    }
    public static String getMod(ItemStack stack, int slot) {
        if (slot < 0 || slot >= MOD_SLOTS) return "";
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(MODS, Tag.TAG_LIST)) return "";
        ListTag entries = tag.getList(MODS, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.getInt(SLOT) == slot) return normalizeModId(entry.getString(MOD_ID));
        }
        return "";
    }
    public static void setMod(ItemStack stack, int slot, String modId) {
        if (slot < 0 || slot >= MOD_SLOTS) return;
        List<String> mods = getMods(stack);
        mods.set(slot, normalizeModId(modId));
        saveMods(stack, mods);
    }
    public static List<String> getMods(ItemStack stack) {
        ArrayList<String> result = new ArrayList<>(MOD_SLOTS);
        for (int i = 0; i < MOD_SLOTS; i++) result.add(getMod(stack, i));
        return result;
    }
    public static int countMods(ItemStack stack) {
        int count = 0;
        for (String mod : getMods(stack)) if (!mod.isBlank()) count++;
        return count;
    }
    public static void clearMods(ItemStack stack) {
        if (stack.hasTag()) stack.getTag().remove(MODS);
    }
    public static List<String> availableMods() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(FORGE_ENERGY_MOD_ID),
                ModList.get().getMods().stream().map(info -> info.getModId())).distinct().sorted().toList();
    }
    public static String normalizeModId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.length() > MAX_MOD_ID_LENGTH) value = value.substring(0, MAX_MOD_ID_LENGTH);
        return value.matches("[a-z][a-z0-9_.-]*") ? value : "";
    }
    public static void appendFilterContents(ItemStack stack, List<Component> tooltip, boolean indented) {
        String suffix = indented ? "_indented" : "";
        tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.contents" + suffix)
                .withStyle(ChatFormatting.GOLD));
        boolean added = false;
        for (int slot = 0; slot < MOD_SLOTS; slot++) {
            String mod = getMod(stack, slot);
            if (!mod.isBlank()) {
                tooltip.add(Component.translatable("tooltip.skylogistics.mod_filter_list.entry" + suffix,
                        slot + 1, mod).withStyle(ChatFormatting.AQUA));
                added = true;
            }
        }
        if (!added) tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.empty" + suffix)
                .withStyle(ChatFormatting.DARK_GRAY));
    }
    private static void saveMods(ItemStack stack, List<String> mods) {
        ListTag entries = new ListTag();
        for (int slot = 0; slot < Math.min(MOD_SLOTS, mods.size()); slot++) {
            String mod = normalizeModId(mods.get(slot));
            if (mod.isBlank()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt(SLOT, slot);
            entry.putString(MOD_ID, mod);
            entries.add(entry);
        }
        stack.getOrCreateTag().put(MODS, entries);
    }
}
