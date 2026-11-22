package sh.aki.servertransfer.config;

import org.bukkit.configuration.ConfigurationSection;
import sh.aki.servertransfer.net.ServerAddress;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/** The parsed and validated config. Problems are collected as warnings instead of failing the load. */
public record PluginConfig(
        String language,
        Menu menu,
        Status status,
        Transfer transfer,
        JoinItem joinItem,
        List<ServerEntry> servers,
        List<String> warnings
) {

    public static final int CURRENT_VERSION = 2;
    public static final int MAX_ROWS = 6;

    public record Menu(String title, int rows, String filler, boolean showStatus, boolean showPlayers) {
        public int size() {
            return rows * 9;
        }
    }

    public record Status(int refreshSeconds, int timeoutMs) {
    }

    public record Transfer(int countdownSeconds, boolean cancelOnMove, boolean cancelOnDamage, boolean checkOnline,
                           int cooldownSeconds) {
    }

    public record JoinItem(boolean enabled, String material, int slot, String name, List<String> lore,
                           boolean locked) {
    }

    public Optional<ServerEntry> server(String id) {
        return servers.stream().filter(s -> s.id().equalsIgnoreCase(id)).findFirst();
    }

    public static PluginConfig parse(ConfigurationSection c, Predicate<String> validItem) {
        List<String> warnings = new ArrayList<>();

        int rows = c.getInt("menu.rows", 1);
        if (rows < 1 || rows > MAX_ROWS) {
            warnings.add("menu.rows must be 1-6, got " + rows);
            rows = Math.clamp(rows, 1, MAX_ROWS);
        }
        String filler = c.getString("menu.filler", "").trim();
        if (!filler.isEmpty() && !validItem.test(filler)) {
            warnings.add("menu.filler: unknown item " + filler);
            filler = "";
        }

        List<ServerEntry> servers = servers(c.getConfigurationSection("servers"), validItem, warnings);
        int needed = servers.stream().mapToInt(ServerEntry::slot).max().orElse(-1) / 9 + 1;
        if (needed > rows) {
            rows = needed;
        }
        Menu menu = new Menu(c.getString("menu.title", "Server Selector"), rows, filler,
                c.getBoolean("menu.show-status", true), c.getBoolean("menu.show-players", true));

        Status status = new Status(Math.max(1, c.getInt("status.refresh-seconds", 5)),
                Math.clamp(c.getInt("status.timeout-ms", 1500), 100, 10_000));

        Transfer transfer = new Transfer(Math.max(0, c.getInt("transfer.countdown-seconds", 3)),
                c.getBoolean("transfer.cancel-on-move", true), c.getBoolean("transfer.cancel-on-damage", true),
                c.getBoolean("transfer.check-online", true), Math.max(0, c.getInt("transfer.cooldown-seconds", 3)));

        String joinMaterial = c.getString("join-item.material", "COMPASS");
        boolean joinEnabled = c.getBoolean("join-item.enabled", false);
        if (!validItem.test(joinMaterial)) {
            warnings.add("join-item.material: unknown item " + joinMaterial);
            joinMaterial = "COMPASS";
        }
        int joinSlot = c.getInt("join-item.slot", 8);
        if (joinSlot < 0 || joinSlot > 8) {
            warnings.add("join-item.slot must be 0-8, got " + joinSlot);
            joinSlot = Math.clamp(joinSlot, 0, 8);
        }
        JoinItem joinItem = new JoinItem(joinEnabled, joinMaterial, joinSlot,
                c.getString("join-item.name", "Server Menu"), c.getStringList("join-item.lore"),
                c.getBoolean("join-item.locked", true));

        return new PluginConfig(c.getString("language", "auto"), menu, status, transfer, joinItem,
                List.copyOf(servers), List.copyOf(warnings));
    }

    private static List<ServerEntry> servers(ConfigurationSection section, Predicate<String> validItem,
                                             List<String> warnings) {
        List<ServerEntry> result = new ArrayList<>();
        if (section == null) {
            return result;
        }
        int max = MAX_ROWS * 9;
        boolean[] taken = new boolean[max];
        List<ServerEntry> unplaced = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) {
                warnings.add("servers." + id + " is not a section");
                continue;
            }
            ServerAddress address;
            try {
                address = ServerAddress.parse(s.getString("address"));
            } catch (IllegalArgumentException e) {
                warnings.add("servers." + id + ".address: " + e.getMessage() + " (server skipped)");
                continue;
            }
            String icon = s.getString("icon", "GRASS_BLOCK").toUpperCase(Locale.ROOT);
            if (!validItem.test(icon)) {
                warnings.add("servers." + id + ".icon: unknown item " + icon);
                icon = "GRASS_BLOCK";
            }
            int slot = s.getInt("slot", -1);
            if (s.contains("slot") && (slot < 0 || slot >= max)) {
                warnings.add("servers." + id + ".slot must be 0-" + (max - 1) + ", got " + slot);
                slot = -1;
            } else if (slot >= 0 && taken[slot]) {
                warnings.add("servers." + id + ".slot " + slot + " is already used");
                slot = -1;
            }
            ServerEntry entry = new ServerEntry(id, address, s.getString("name", id), icon, slot,
                    s.getStringList("lore"), s.getString("permission", ""));
            if (slot < 0) {
                unplaced.add(entry);
            } else {
                taken[slot] = true;
                result.add(entry);
            }
        }
        int next = 0;
        for (ServerEntry e : unplaced) {
            while (next < max && taken[next]) {
                next++;
            }
            if (next >= max) {
                warnings.add("servers." + e.id() + ": no free slot left (server skipped)");
                continue;
            }
            taken[next] = true;
            result.add(withSlot(e, next));
        }
        result.sort(Comparator.comparingInt(ServerEntry::slot));
        return result;
    }

    static ServerEntry withSlot(ServerEntry e, int slot) {
        return new ServerEntry(e.id(), e.address(), e.name(), e.icon(), slot, e.lore(), e.permission());
    }
}
