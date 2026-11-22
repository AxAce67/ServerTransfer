package sh.aki.servertransfer.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Marks an inventory as the layout editor. */
final class EditorHolder implements InventoryHolder {

    final UUID viewer;
    final int rows;
    Inventory inventory;

    EditorHolder(UUID viewer, int rows) {
        this.viewer = viewer;
        this.rows = rows;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
