package sh.aki.servertransfer.item;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import sh.aki.servertransfer.ServerTransferPlugin;
import sh.aki.servertransfer.config.PluginConfig;
import sh.aki.servertransfer.text.Text;

import java.util.Objects;

/** The hotbar item that opens the menu. It is recognised by a tag, not by its material. */
public final class JoinItems implements Listener {

    private final ServerTransferPlugin plugin;

    public JoinItems(ServerTransferPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isJoinItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(plugin.joinItemKey(), PersistentDataType.BYTE);
    }

    private ItemStack create() {
        PluginConfig.JoinItem c = plugin.settings().joinItem();
        ItemStack item = new ItemStack(Objects.requireNonNull(Material.matchMaterial(c.material())));
        item.editMeta(meta -> {
            meta.displayName(Text.item(Text.parse(c.name())));
            meta.lore(c.lore().stream().map(l -> Text.item(Text.parse(l))).toList());
            meta.getPersistentDataContainer().set(plugin.joinItemKey(), PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    /** Replaces old copies with the current item, or removes them when the item is disabled. */
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isJoinItem(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        PluginConfig.JoinItem c = plugin.settings().joinItem();
        if (!c.enabled() || !player.hasPermission("servertransfer.use")) {
            return;
        }
        int slot = c.slot();
        if (inv.getItem(slot) != null && inv.getItem(slot).getType() != Material.AIR) {
            slot = inv.firstEmpty();
            if (slot < 0) {
                return;
            }
        }
        inv.setItem(slot, create());
    }

    private boolean locked() {
        return plugin.settings().joinItem().locked();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                apply(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isJoinItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().hasPermission("servertransfer.use")) {
            plugin.menus().open(event.getPlayer());
        } else {
            plugin.messages().send(event.getPlayer(), "error.no-permission");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (locked() && isJoinItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (locked() && (isJoinItem(event.getMainHandItem()) || isJoinItem(event.getOffHandItem()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!locked()) {
            return;
        }
        boolean hotbarSwap = event.getHotbarButton() >= 0
                && isJoinItem(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()));
        if (isJoinItem(event.getCurrentItem()) || isJoinItem(event.getCursor()) || hotbarSwap) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (locked() && isJoinItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (locked()) {
            event.getDrops().removeIf(this::isJoinItem);
        }
    }
}
