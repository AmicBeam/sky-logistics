package com.skylogistics.compat;

import com.skylogistics.SkyLogistics;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public final class PatchouliCompat {
    private static final ResourceLocation GUIDE_BOOK = new ResourceLocation("patchouli", "guide_book");
    private static final String BOOK_TAG = "patchouli:book";
    private static final String SKY_LOGISTICS_BOOK = SkyLogistics.MOD_ID + ":sky_logistics";

    private PatchouliCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("patchouli");
    }

    public static Optional<ItemStack> createManualStack() {
        if (!isLoaded()) {
            return Optional.empty();
        }
        Item guideBook = ForgeRegistries.ITEMS.getValue(GUIDE_BOOK);
        if (guideBook == null) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(guideBook);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(BOOK_TAG, SKY_LOGISTICS_BOOK);
        return Optional.of(stack);
    }
}
