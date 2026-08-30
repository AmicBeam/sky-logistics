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

In the node screen, each face can be disconnected, extract, or insert. Only connected faces participate in a line, and the block appearance changes with its connections and extract faces.

Nodes also have resource toggles, priority, and redstone controls. Higher-priority insert targets are attempted earlier. Redstone cycles between ignore signals, work with a signal, work without a signal, and pulse. Pulse wakes on a rising edge and sleeps again after one successful transfer. A blocked face is paused and is not treated as a failing endpoint.

The More page configures redstone, priority, item maintenance, and filters. Filters define the matching set first; the slot or item target counts that set together, not once per matching item type. A value of 0 means unlimited. Red text at the bottom explains invalid combinations, such as external item extraction without a concrete item whitelist.
