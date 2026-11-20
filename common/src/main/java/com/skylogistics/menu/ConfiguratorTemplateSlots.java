package com.skylogistics.menu;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.PipeTemplate;
import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.network.SkyLineAccess;
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
            BooleanSupplier editable, IntPredicate boundSlot) {
        for (int index = 0; index < UPGRADES; index++) {
            addSlot.accept(sample(index, 47 + index * 20, 125, editable));
        }
        addSlot.accept(sample(FILTER, 181, 125, editable));
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

    static Direction firstCopiedFace(ItemStack tool) {
        var pipe = ConfiguratorItem.readPipeTemplate(tool);
        if (pipe != null) {
            Direction firstEndpoint = null;
            for (Direction direction : Direction.values()) {
                var face = pipe.face(direction);
                if (!face.filter().isEmpty()) return direction;
                if (firstEndpoint == null && face.connection().isContainer()) firstEndpoint = direction;
            }
            return firstEndpoint;
        }
        var config = ConfiguratorItem.read(tool);
        if (config == null || !config.hasCopiedFaces()) return null;
        for (Direction direction : Direction.values()) {
            if (!config.face(direction).filter(0).isEmpty()) return direction;
        }
        return null;
    }

    static boolean canEdit(Player player, ItemStack tool, boolean copied) {
        var config = ConfiguratorItem.read(tool);
        var pipe = copied ? ConfiguratorItem.readPipeTemplate(tool) : null;
        return config != null && SkyLineAccess.canUse(player, config.lineId())
                && (pipe == null || pipe.owner() == null || SkyLineAccess.canUseOwner(player, pipe.owner()));
    }

    void refresh(ItemStack tool, Direction face, boolean copied, boolean allowed) {
        var config = allowed ? ConfiguratorItem.read(tool) : null;
        var pipe = allowed && copied ? ConfiguratorItem.readPipeTemplate(tool) : null;
        for (int index = 0; index < UPGRADES; index++) {
            samples.setItem(index, config != null && index < config.upgrades().size()
                    ? config.upgrades().get(index) : ItemStack.EMPTY);
        }
        ItemStack filter = ItemStack.EMPTY;
        if (pipe != null && face != null) {
            filter = pipe.face(face).filter();
        } else if (pipe == null && config != null) {
            filter = (face == null ? config.placement() : config.face(face)).filter(0);
        }
        samples.setItem(FILTER, filter);
    }

    static boolean edit(ItemStack tool, Direction face, boolean copied, int index, ItemStack sample, boolean single) {
        var config = ConfiguratorItem.read(tool);
        if (config == null) return false;
        var pipe = copied ? ConfiguratorItem.readPipeTemplate(tool) : null;
        if (index == FILTER) {
            if (!sample.isEmpty() && !FilterListItem.isFilterItem(sample)) return false;
            if (pipe != null) {
                if (face == null) return false;
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
        if (index < 0 || index >= UPGRADES) return false;
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
