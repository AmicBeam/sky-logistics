package com.skylogistics.network;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.NodeMode;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Configuration and menu surface implemented by placed nodes and Kleis virtual endpoints. */
public interface ConfigurableLogisticsEndpoint extends LogisticsEndpoint {
    String getLineName();
    String getAssignedLineName();
    NodeMode getMode();
    default boolean usesSingleEndpoint() { return false; }
    default Direction getSingleEndpointDirection() { return getTargetDirection(); }
    Direction getTargetDirection();
    boolean canConfigureFace(Direction direction);
    boolean hasConfigurableTarget(Direction direction);
    ItemStack getTargetIcon(Direction direction);
    Component getTargetName(Direction direction);
    ItemStack getUpgrade(int slot);
    boolean canAcceptUpgrade(int slot, ItemStack stack);
    void setUpgrade(int slot, ItemStack stack);
    boolean rejectsTagFaceFilter(Direction direction, ItemStack stack);
    boolean hasTagFaceFilterRestriction(Direction direction);
    boolean hasValidItemWhitelistFaceFilter(Direction direction);
    void setFaceFilter(Direction direction, int slot, ItemStack stack);
    void setFaceMode(Direction direction, NodeFaceMode mode);
    void setItemsEnabled(Direction direction, boolean enabled);
    void setFluidsEnabled(Direction direction, boolean enabled);
    void setEnergyEnabled(Direction direction, boolean enabled);
    void adjustPriority(Direction direction, int delta);
    void adjustItemSlotLimit(Direction direction, int delta);
    void setItemSlotLimit(Direction direction, int limit);
    boolean isItemLimitByItems(Direction direction);
    void toggleItemLimitUnit(Direction direction);
    void cycleRedstoneControl(Direction direction);
    void setMode(NodeMode mode);
    void selectPlayerLine(UUID lineId, String assignedName, String displayName);
    void applyPlacementToolConfig(ConfiguratorItem.ToolConfig config, boolean includeMode);
    void applySingleEndpointToolConfig(ConfiguratorItem.ToolConfig config, Player player);
    void setChanged();
    default boolean supportsRedstoneControl() { return true; }
}
