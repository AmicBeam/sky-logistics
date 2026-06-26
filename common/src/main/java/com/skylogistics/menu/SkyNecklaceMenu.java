package com.skylogistics.menu;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.SkyNecklaceItem;
import com.skylogistics.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SkyNecklaceMenu extends AbstractContainerMenu {
    private static final int FILTER_SLOT = 0;
    public static final int FILTER_LABEL_X = 166;
    public static final int FILTER_SLOT_X = 200;
    public static final int FILTER_SLOT_Y = 55;
    private static final int PLAYER_INVENTORY_Y = 158;

    private final InteractionHand hand;
    private final Player player;
    private final Container filterContainer = new SimpleContainer(1) {
        @Override
        public ItemStack getItem(int slot) {
            return slot == 0 ? SkyNecklaceItem.filterList(necklace()) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            setFilter(stack);
        }

        @Override
        public void setChanged() {
            player.getInventory().setChanged();
        }
    };

    public SkyNecklaceMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(com.skylogistics.registry.ModMenus.SKY_NECKLACE.get(), containerId);
        this.hand = hand;
        this.player = inventory.player;
        addSlot(new Slot(filterContainer, 0, FILTER_SLOT_X, FILTER_SLOT_Y) {
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
        addPlayerInventory(inventory, 44, PLAYER_INVENTORY_Y);
    }

    public InteractionHand getHand() {
        return hand;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).is(ModItems.SKY_NECKLACE.get());
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == FILTER_SLOT) {
            setFilter(getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        if (index == FILTER_SLOT) {
            setFilter(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        if (original.is(ModItems.FILTER_LIST.get())) {
            setFilter(original);
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    public void applyAction(Player player, int action) {
        ItemStack stack = necklace();
        if (!stack.is(ModItems.SKY_NECKLACE.get())) {
            return;
        }
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.readOrCreate(stack, player);
        switch (action) {
            case MenuAction.LINE_FIRST -> config = ConfiguratorItem.selectFirstLine(stack);
            case MenuAction.LINE_PREVIOUS -> config = ConfiguratorItem.selectPreviousLine(stack);
            case MenuAction.LINE_NEXT_OR_CREATE -> config = ConfiguratorItem.selectNextOrCreateLine(stack, player);
            case MenuAction.LINE_LAST -> config = ConfiguratorItem.selectLastLine(stack);
            case MenuAction.LINE_REMOVE_CURRENT -> config = ConfiguratorItem.removeCurrentLine(stack, player);
            case MenuAction.MODE_EXTRACT -> SkyNecklaceItem.setMode(stack, SkyNecklaceItem.NecklaceMode.EXTRACT);
            case MenuAction.MODE_INSERT -> SkyNecklaceItem.setMode(stack, SkyNecklaceItem.NecklaceMode.INSERT);
            case MenuAction.NECKLACE_INSERT_SLOTS_DOWN -> {
                if (SkyNecklaceItem.mode(stack) == SkyNecklaceItem.NecklaceMode.INSERT) {
                    SkyNecklaceItem.adjustInsertSlots(stack, -1);
                }
            }
            case MenuAction.NECKLACE_INSERT_SLOTS_UP -> {
                if (SkyNecklaceItem.mode(stack) == SkyNecklaceItem.NecklaceMode.INSERT) {
                    SkyNecklaceItem.adjustInsertSlots(stack, 1);
                }
            }
            case MenuAction.NECKLACE_INSERT_SLOTS_DOWN_FAST -> {
                if (SkyNecklaceItem.mode(stack) == SkyNecklaceItem.NecklaceMode.INSERT) {
                    SkyNecklaceItem.adjustInsertSlots(stack, -10);
                }
            }
            case MenuAction.NECKLACE_INSERT_SLOTS_UP_FAST -> {
                if (SkyNecklaceItem.mode(stack) == SkyNecklaceItem.NecklaceMode.INSERT) {
                    SkyNecklaceItem.adjustInsertSlots(stack, 10);
                }
            }
            case MenuAction.NECKLACE_PRIORITY_DOWN -> SkyNecklaceItem.adjustPriority(stack, -1);
            case MenuAction.NECKLACE_PRIORITY_UP -> SkyNecklaceItem.adjustPriority(stack, 1);
            case MenuAction.NECKLACE_PRIORITY_DOWN_FAST -> SkyNecklaceItem.adjustPriority(stack, -10);
            case MenuAction.NECKLACE_PRIORITY_UP_FAST -> SkyNecklaceItem.adjustPriority(stack, 10);
            default -> {
                return;
            }
        }
        ConfiguratorItem.writeConfig(stack, config);
        syncHeldStack(stack);
        broadcastChanges();
    }

    private void setFilter(ItemStack filter) {
        ItemStack stack = necklace();
        if (!stack.is(ModItems.SKY_NECKLACE.get())) {
            return;
        }
        if (!filter.isEmpty() && filter.is(ModItems.FILTER_LIST.get()) && !FilterListItem.isWhitelist(filter)) {
            player.displayClientMessage(Component.translatable("message.skylogistics.sky_necklace.blacklist_filter"), true);
            return;
        }
        if (!SkyNecklaceItem.setFilterList(stack, filter)) {
            return;
        }
        syncHeldStack(stack);
        broadcastChanges();
    }

    private ItemStack necklace() {
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
