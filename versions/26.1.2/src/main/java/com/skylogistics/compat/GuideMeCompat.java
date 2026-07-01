package com.skylogistics.compat;

import com.skylogistics.SkyLogistics;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

public final class GuideMeCompat {
    private static final String GUIDEME = "guideme";
    private static final Identifier GUIDE_ITEM = Identifier.fromNamespaceAndPath(GUIDEME, "guide");
    private static final Identifier GUIDE_ID_COMPONENT = Identifier.fromNamespaceAndPath(GUIDEME, "guide_id");
    private static final Identifier SKY_LOGISTICS_GUIDE =
            Identifier.fromNamespaceAndPath(SkyLogistics.MOD_ID, "sky_logistics");

    private GuideMeCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(GUIDEME);
    }

    public static Optional<ItemStack> createManualStack() {
        if (!isLoaded()) {
            return Optional.empty();
        }
        Item guide = BuiltInRegistries.ITEM.getValue(GUIDE_ITEM);
        if (guide == null || guide == Items.AIR) {
            return Optional.empty();
        }
        Optional<DataComponentType<Identifier>> guideIdComponent = guideIdComponent();
        if (guideIdComponent.isEmpty()) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(guide);
        stack.set(guideIdComponent.get(), SKY_LOGISTICS_GUIDE);
        return Optional.of(stack);
    }

    @SuppressWarnings("unchecked")
    private static Optional<DataComponentType<Identifier>> guideIdComponent() {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(GUIDE_ID_COMPONENT)
                .map(component -> (DataComponentType<Identifier>) component);
    }
}
