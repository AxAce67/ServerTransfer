package sh.aki.servertransfer.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import sh.aki.servertransfer.ServerTransferPlugin;
import sh.aki.servertransfer.config.PluginConfig;
import sh.aki.servertransfer.config.ServerEntry;
import sh.aki.servertransfer.net.ServerStatus;
import sh.aki.servertransfer.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The server menu and the layout editor. */
public final class Menus implements Listener {

    private static final Set<InventoryAction> EDITOR_ACTIONS = Set.of(InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_HALF, InventoryAction.PICKUP_ONE, InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_ONE, InventoryAction.SWAP_WITH_CURSOR, InventoryAction.NOTHING);

    private final ServerTransferPlugin plugin;
    private final List<SelectorHolder> openMenus = new ArrayList<>();
    private final List<EditorHolder> openEditors = new ArrayList<>();

    public Menus(ServerTransferPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean canSee(Player p, ServerEntry s) {
        return !s.needsPermission() || p.hasPermission(s.permission());
    }

    // Server menu

    public void open(Player player) {
        PluginConfig config = plugin.settings();
        Map<Integer, ServerEntry> slots = new LinkedHashMap<>();
        for (ServerEntry s : config.servers()) {
            if (canSee(player, s)) {
                slots.put(s.slot(), s);
            }
        }
        if (slots.isEmpty()) {
            plugin.messages().send(player, config.servers().isEmpty() ? "menu.no-servers" : "menu.no-visible");
            return;
        }
        SelectorHolder holder = new SelectorHolder(player.getUniqueId(), slots);
        Inventory inv = Bukkit.createInventory(holder, config.menu().size(), Text.parse(config.menu().title()));
        holder.inventory = inv;
        if (!config.menu().filler().isEmpty()) {
            ItemStack filler = Icons.filler(config.menu().filler());
            for (int i = 0; i < inv.getSize(); i++) {
                if (!slots.containsKey(i)) {
                    inv.setItem(i, filler);
                }
            }
        }
        slots.values().forEach(s -> render(holder, s, player));
        closeTracked(player);
        player.openInventory(inv);
        openMenus.add(holder);
        refresh(holder, true);
    }

    private void render(SelectorHolder holder, ServerEntry s, Player viewer) {
        ServerStatus status = plugin.settings().menu().showStatus()
                ? plugin.statuses().cached(s.address()).orElse(null)
                : null;
        holder.inventory.setItem(s.slot(),
                Icons.server(s, status, plugin.settings().menu(), plugin.messages(), viewer));
    }

    /** Pings servers whose status is out of date and updates the icons when the answers arrive. */
    private void refresh(SelectorHolder holder, boolean force) {
        PluginConfig config = plugin.settings();
        if (!config.menu().showStatus()) {
            return;
        }
        long maxAge = config.status().refreshSeconds() * 1000L;
        for (ServerEntry s : holder.slots.values()) {
            if (!force && plugin.statuses().isFresh(s.address(), maxAge)) {
                continue;
            }
            plugin.statuses().get(s.address(), maxAge).thenAccept(status -> plugin.sync(() -> {
                Player viewer = Bukkit.getPlayer(holder.viewer);
                if (viewer != null && openMenus.contains(holder)) {
                    render(holder, s, viewer);
                }
            }));
        }
    }

    /** Called every second. */
    public void tick() {
        for (SelectorHolder holder : List.copyOf(openMenus)) {
            refresh(holder, false);
        }
    }

    // Layout editor

    public void openEditor(Player player, int rows) {
        PluginConfig config = plugin.settings();
        int needed = (config.servers().size() + 8) / 9;
        if (rows < needed) {
            plugin.messages().send(player, "editor.too-small", config.servers().size(), needed);
            return;
        }
        EditorHolder holder = new EditorHolder(player.getUniqueId(), rows);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, plugin.messages().get(player, "editor.title"));
        holder.inventory = inv;
        List<ServerEntry> overflow = new ArrayList<>();
        for (ServerEntry s : config.servers()) {
            if (s.slot() < inv.getSize() && inv.getItem(s.slot()) == null) {
                inv.setItem(s.slot(), editorIcon(s, player));
            } else {
                overflow.add(s);
            }
        }
        for (ServerEntry s : overflow) {
            inv.setItem(inv.firstEmpty(), editorIcon(s, player));
        }
        closeTracked(player);
        player.openInventory(inv);
        openEditors.add(holder);
        plugin.messages().send(player, "editor.hint");
    }

    private ItemStack editorIcon(ServerEntry s, Player viewer) {
        ItemStack item = Icons.server(s, null, new PluginConfig.Menu("", 1, "", false, false), plugin.messages(), viewer);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(plugin.editorKey(), PersistentDataType.STRING, s.id()));
        return item;
    }

    private String editorId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(plugin.editorKey(), PersistentDataType.STRING);
    }

    private void saveEditor(EditorHolder holder, Player player) {
        Inventory inv = holder.inventory;
        Map<String, Integer> slots = new HashMap<>();
        for (int i = 0; i < inv.getSize(); i++) {
            String id = editorId(inv.getItem(i));
            if (id != null) {
                slots.put(id, i);
            }
        }
        // An icon still held on the cursor goes to the first free slot.
        String held = editorId(player.getItemOnCursor());
        if (held != null) {
            player.setItemOnCursor(null);
            int free = inv.firstEmpty();
            if (free >= 0) {
                slots.put(held, free);
            }
        }
        inv.clear();
        plugin.sync(() -> {
            removeEditorIcons(player);
            plugin.saveLayout(holder.rows, slots);
            plugin.messages().send(player, "editor.saved");
        });
    }

    /** Editor icons must never end up in a player's inventory. */
    private void removeEditorIcons(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (editorId(inv.getItem(i)) != null) {
                inv.setItem(i, null);
            }
        }
        if (editorId(player.getItemOnCursor()) != null) {
            player.setItemOnCursor(null);
        }
    }

    // Events

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (top.getHolder(false) instanceof SelectorHolder holder) {
            event.setCancelled(true);
            if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()
                    || !(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            ServerEntry s = holder.slots.get(event.getRawSlot());
            if (s != null) {
                player.closeInventory();
                plugin.transfers().request(player, s);
            }
        } else if (top.getHolder(false) instanceof EditorHolder) {
            boolean inTop = event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize();
            ItemStack cursor = event.getWhoClicked().getItemOnCursor();
            boolean cursorOk = cursor.getType() == Material.AIR || editorId(cursor) != null;
            if (!inTop || !cursorOk || !EDITOR_ACTIONS.contains(event.getAction())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        if (top.getHolder(false) instanceof SelectorHolder) {
            event.setCancelled(true);
        } else if (top.getHolder(false) instanceof EditorHolder) {
            boolean outside = event.getRawSlots().stream().anyMatch(s -> s >= top.getSize());
            if (outside || editorId(event.getOldCursor()) == null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (top.getHolder(false) instanceof SelectorHolder holder) {
            openMenus.remove(holder);
        } else if (top.getHolder(false) instanceof EditorHolder holder && openEditors.remove(holder)
                && event.getPlayer() instanceof Player player) {
            saveEditor(holder, player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        openMenus.removeIf(h -> h.viewer.equals(event.getPlayer().getUniqueId()));
    }

    private void closeTracked(HumanEntity player) {
        openMenus.removeIf(h -> h.viewer.equals(player.getUniqueId()));
    }

    public void closeAll() {
        for (SelectorHolder h : List.copyOf(openMenus)) {
            Player p = Bukkit.getPlayer(h.viewer);
            if (p != null) {
                p.closeInventory();
            }
        }
        openMenus.clear();
        for (EditorHolder h : List.copyOf(openEditors)) {
            Player p = Bukkit.getPlayer(h.viewer);
            if (p != null) {
                p.closeInventory();
            }
        }
    }
}
