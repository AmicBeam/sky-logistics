package com.skylogistics.menu;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.PipeTemplate;
import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.util.SimplePipeType;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

final class ConfiguratorTemplateSlots {
    static final int UPGRADES = SkyNodeBlockEntity.UPGRADE_SLOTS;
    static final int FILTER = UPGRADES;
    static final int FIRST_PLAYER_SLOT = FILTER + 1;
    private final SimpleContainer samples = new SimpleContainer(FIRST_PLAYER_SLOT);

    void add(Inventory inventory, Consumer<Slot> addSlot, BooleanSupplier visible,
            BooleanSupplier editable, BooleanSupplier pipe, IntPredicate boundSlot) {
        for (int index = 0; index < UPGRADES; index++) {
            addSlot.accept(sample(index, 52 + index * 20, 112,
                    () -> editable.getAsBoolean() && !pipe.getAsBoolean()));
        }
        addSlot.accept(sample(FILTER, 163, 112, editable));
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 9; column++) {
                int index = row == 3 ? column : 9 + row * 9 + column;
                int y = row == 3 ? 224 : 166 + row * 18;
                addSlot.accept(new Slot(inventory, index, 50 + column * 18, y) {
                    @Override public boolean isActive() { return visible.getAsBoolean(); }
                    @Override public boolean mayPickup(Player player) { return isActive() && !boundSlot.test(index); }
                    @Override public boolean mayPlace(ItemStack stack) { return isActive() && !boundSlot.test(index); }
                });
            }
        }
    }

    private Slot sample(int index, int x, int y, BooleanSupplier visible) {
        return new Slot(samples, index, x, y) {
            @Override public boolean isActive() { return visible.getAsBoolean(); }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        };
    }

    void refresh(ItemStack tool, Direction face, boolean allowed) {
        var config = allowed ? ConfiguratorItem.read(tool) : null;
        var pipe = allowed ? ConfiguratorItem.readPipeTemplate(tool) : null;
        for (int index = 0; index < UPGRADES; index++) {
            samples.setItem(index, config != null && pipe == null && index < config.upgrades().size()
                    ? config.upgrades().get(index) : ItemStack.EMPTY);
        }
        samples.setItem(FILTER, pipe != null ? pipe.face(face).filter() : config == null ? ItemStack.EMPTY
                : (config.hasCopiedFaces() ? config.face(face) : config.placement()).filter(0));
    }

    static boolean edit(ItemStack tool, Direction face, int index, ItemStack sample, boolean single) {
        var config = ConfiguratorItem.read(tool);
        if (config == null) return false;
        var pipe = ConfiguratorItem.readPipeTemplate(tool);
        if (index == FILTER) {
            if (!sample.isEmpty() && !FilterListItem.isFilterItem(sample)) return false;
            if (pipe != null) {
                var entry = pipe.face(face);
                if (!sample.isEmpty() && (!entry.connection().isContainer()
                        || (pipe.type() == SimplePipeType.ENERGY && !TagFilterListItem.isTagFilterList(sample)))) {
                    return false;
                }
                ConfiguratorItem.writePipeTemplate(tool, pipe.withFace(face,
                        new PipeTemplate.Face(entry.connection(), entry.disconnected(), sample)));
            } else {
                ConfiguratorItem.writeConfig(tool, config.withTemplateFilter(face, sample),
                        ConfiguratorItem.assignedLineName(tool));
            }
            return true;
        }
        if (pipe != null || index < 0 || index >= UPGRADES) return false;
        var cards = new ArrayList<>(config.upgrades());
        while (cards.size() < UPGRADES) cards.add(ItemStack.EMPTY);
        if (sample.isEmpty()) {
            if (cards.get(index).isEmpty()) return false;
            cards.set(index, ItemStack.EMPTY);
        } else {
            if (!SkyNodeBlockEntity.isUpgradeItem(sample)) return false;
            int count = Math.min(single ? 1 : sample.getCount(), SkyNodeBlockEntity.maxUpgradeStackSize(sample));
            if (count <= 0) return false;
            for (int other = 0; other < cards.size(); other++) {
                if (other != index && cards.get(other).is(sample.getItem())) return false;
            }
            cards.set(index, sample.copyWithCount(count));
        }
        ConfiguratorItem.writeConfig(tool, config.withUpgrades(cards), ConfiguratorItem.assignedLineName(tool));
        return true;
    }
}
