package sh.aki.servertransfer.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import sh.aki.servertransfer.config.ServerEntry;

import java.util.Map;
import java.util.UUID;

/** Marks an inventory as the server menu and remembers which server sits in which slot. */
final class SelectorHolder implements InventoryHolder {

    final UUID viewer;
    final Map<Integer, ServerEntry> slots;
    Inventory inventory;

    SelectorHolder(UUID viewer, Map<Integer, ServerEntry> slots) {
        this.viewer = viewer;
        this.slots = slots;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
