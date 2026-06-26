package com.skylogistics.compat;

import com.skylogistics.SkyLogistics;
import com.skylogistics.util.StackData;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class PatchouliCompat {
    private static final ResourceLocation GUIDE_BOOK = ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book");
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
        Item guideBook = BuiltInRegistries.ITEM.get(GUIDE_BOOK);
        if (guideBook == null || guideBook == net.minecraft.world.item.Items.AIR) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(guideBook);
        StackData.update(stack, tag -> tag.putString(BOOK_TAG, SKY_LOGISTICS_BOOK));
        return Optional.of(stack);
    }
}
