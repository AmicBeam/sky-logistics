package com.skylogistics.menu;

import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModMenus;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class TagFilterListMenu extends AbstractContainerMenu {
    private static final int SAMPLE_SLOT = 0;
    public static final int SAMPLE_SLOT_X = 32;
    public static final int SAMPLE_SLOT_Y = 25;
    private static final int PLAYER_INVENTORY_X = 32;
    private static final int PLAYER_INVENTORY_Y = 158;

    private final InteractionHand hand;
    private final Player player;
    private final SimpleContainer sampleContainer = new SimpleContainer(1);

    public TagFilterListMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(ModMenus.TAG_FILTER_LIST.get(), containerId);
        this.hand = hand;
        this.player = inventory.player;
        addSlot(new Slot(sampleContainer, 0, SAMPLE_SLOT_X, SAMPLE_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addPlayerInventory(inventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
    }

    public InteractionHand getHand() {
        return hand;
    }

    public ItemStack getSample() {
        return sampleContainer.getItem(0);
    }

    public List<String> sampleTags() {
        return TagFilterListItem.sampleTags(getSample());
    }

    public String sampleModId() {
        return TagFilterListItem.sampleModId(getSample());
    }

    public List<String> availableTags(boolean fluid) {
        return TagFilterListItem.availableTags(fluid);
    }

    public String getTag(int slot) {
        return TagFilterListItem.getTag(filterStack(), slot);
    }

    public String getTag(int slot, int resource) {
        if (resource == 2) {
            String mod = TagFilterListItem.getMod(filterStack(), slot);
            return mod.isBlank() ? "" : "@" + mod;
        }
        return resource == 1 ? TagFilterListItem.getFluidTag(filterStack(), slot) : getTag(slot);
    }

    public boolean isWhitelist() {
        return TagFilterListItem.isWhitelist(filterStack());
    }

    public List<String> availableMods() { return TagFilterListItem.availableMods(); }

    public void setTag(int slot, String tag) {
        setTag(slot, tag, 0);
    }

    public void setTag(int slot, String tag, int resource) {
        ItemStack stack = filterStack();
        if (!stack.is(ModItems.TAG_FILTER_LIST.get())) {
            return;
        }
        if (resource == 2) TagFilterListItem.setMod(stack, slot, tag);
        else if (resource == 1) TagFilterListItem.setFluidTag(stack, slot, tag);
        else TagFilterListItem.setTag(stack, slot, tag);
        syncHeldStack(stack);
        broadcastChanges();
    }

    public void setGhostSample(ItemStack stack) {
        ItemStack sample = stack.copy();
        if (!sample.isEmpty()) sample.setCount(1);
        sampleContainer.setItem(0, sample);
        broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = player.getItemInHand(hand);
        return stack.is(ModItems.TAG_FILTER_LIST.get());
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == SAMPLE_SLOT) {
            setGhostSample(getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index <= SAMPLE_SLOT || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slots.get(index).getItem();
        if (!stack.isEmpty()) {
            setGhostSample(stack);
        }
        return ItemStack.EMPTY;
    }

    public void applyAction(Player player, int action) {
        ItemStack stack = filterStack();
        if (!stack.is(ModItems.TAG_FILTER_LIST.get())) {
            return;
        }
        switch (action) {
            case MenuAction.FILTER_SET_WHITELIST -> TagFilterListItem.setWhitelist(stack, true);
            case MenuAction.FILTER_SET_BLACKLIST -> TagFilterListItem.setWhitelist(stack, false);
            case MenuAction.FILTER_CLEAR -> TagFilterListItem.clearTags(stack);
            default -> {
                return;
            }
        }
        syncHeldStack(stack);
        broadcastChanges();
    }

    private ItemStack filterStack() {
        return player.getItemInHand(hand);
    }

    private void syncHeldStack(ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        int slot = hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().selected;
        player.getInventory().setChanged();
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, stack.copy()));
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, x + column * 18, y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, x + column * 18, y + 58));
        }
    }
}
