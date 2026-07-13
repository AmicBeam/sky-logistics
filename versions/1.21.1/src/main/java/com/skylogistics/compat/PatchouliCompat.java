package com.skylogistics.compat;

import com.skylogistics.SkyLogistics;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class PatchouliCompat {
    private static final String PATCHOULI_API = "vazkii.patchouli.api.PatchouliAPI";
    private static final String PATCHOULI_API_INTERFACE = "vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI";
    private static final ResourceLocation GUIDE_BOOK = ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book");
    private static final ResourceLocation BOOK_COMPONENT = ResourceLocation.fromNamespaceAndPath("patchouli", "book");
    private static final ResourceLocation SKY_LOGISTICS_BOOK =
            ResourceLocation.fromNamespaceAndPath(SkyLogistics.MOD_ID, "sky_logistics");

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
        Optional<DataComponentType<ResourceLocation>> bookComponent = bookComponent();
        if (bookComponent.isEmpty()) {
            return Optional.empty();
        }
        stack.set(bookComponent.get(), SKY_LOGISTICS_BOOK);
        return Optional.of(stack);
    }

    @SuppressWarnings("unchecked")
    private static Optional<DataComponentType<ResourceLocation>> bookComponent() {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(BOOK_COMPONENT)
                .map(component -> (DataComponentType<ResourceLocation>) component);
    }

    public static boolean openManual(Player player) {
        if (!isLoaded() || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName(PATCHOULI_API);
            Object api = apiClass.getMethod("get").invoke(null);
            Class<?> apiInterface = Class.forName(PATCHOULI_API_INTERFACE);
            apiInterface.getMethod("openBookGUI", ServerPlayer.class, ResourceLocation.class)
                    .invoke(api, serverPlayer, SKY_LOGISTICS_BOOK);
            return true;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return false;
        }
    }
}
