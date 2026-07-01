package com.skylogistics.compat;

import com.skylogistics.SkyLogistics;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class PatchouliCompat {
    private static final boolean SUPPORTED_IN_26_1_2 = false;
    private static final Identifier GUIDE_BOOK = Identifier.fromNamespaceAndPath("patchouli", "guide_book");
    private static final Identifier BOOK_COMPONENT = Identifier.fromNamespaceAndPath("patchouli", "book");
    private static final Identifier SKY_LOGISTICS_BOOK =
            Identifier.fromNamespaceAndPath(SkyLogistics.MOD_ID, "sky_logistics");

    private PatchouliCompat() {
    }

    public static boolean isLoaded() {
        return SUPPORTED_IN_26_1_2 && ModList.get().isLoaded("patchouli");
    }

    public static Optional<ItemStack> createManualStack() {
        if (!isLoaded()) {
            return Optional.empty();
        }
        Item guideBook = BuiltInRegistries.ITEM.getValue(GUIDE_BOOK);
        if (guideBook == null || guideBook == net.minecraft.world.item.Items.AIR) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(guideBook);
        Optional<DataComponentType<Identifier>> bookComponent = bookComponent();
        if (bookComponent.isEmpty()) {
            return Optional.empty();
        }
        stack.set(bookComponent.get(), SKY_LOGISTICS_BOOK);
        return Optional.of(stack);
    }

    @SuppressWarnings("unchecked")
    private static Optional<DataComponentType<Identifier>> bookComponent() {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(BOOK_COMPONENT)
                .map(component -> (DataComponentType<Identifier>) component);
    }
}
