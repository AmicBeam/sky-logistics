---
navigation:
  title: Nodes and Pipe Endpoints
  icon: sky_node
  parent: logistics.md
  position: 1
item_ids:
  - skylogistics:sky_node
---

# Nodes and Pipe Endpoints

A <ItemLink id="sky_node" /> is the wireless equivalent of a pipe endpoint. Normal placement defaults to insert mode; sneak placement defaults to extract mode. The node attaches to the clicked block side and accesses the adjacent block from that direction.

<RecipeFor id="sky_node" fallbackText="The Sky Logistics Node recipe is unavailable." />

One craft gives 2 nodes. Nodes have no internal buffer; they only pair same-line endpoints for transfer.

In the node screen, each face can be disconnected, extract, or insert. Only connected faces participate in a line. The block model shows the main visual mode, but the actual behavior comes from the per-face settings.

Nodes also have resource toggles, priority, and redstone controls. Higher-priority insert targets are attempted earlier. A face blocked by redstone is paused and is not treated as a failing endpoint.
