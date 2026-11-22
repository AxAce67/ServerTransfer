package sh.aki.servertransfer.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Moves settings from the 1.x config layout into the current one. */
public final class ConfigMigrator {

    private ConfigMigrator() {
    }

    public static boolean isLegacy(ConfigurationSection c) {
        return !c.contains("config-version")
                && (c.contains("gui-title") || c.contains("gui-size") || c.contains("countdown-time"));
    }

    /**
     * @param old      the 1.x config
     * @param defaults a fresh copy of the bundled config, which receives the old values
     */
    public static YamlConfiguration migrate(ConfigurationSection old, YamlConfiguration defaults) {
        YamlConfiguration c = defaults;
        c.set("config-version", PluginConfig.CURRENT_VERSION);
        copy(old, "language", c, "language");
        copy(old, "gui-title", c, "menu.title");
        if (old.contains("gui-size")) {
            c.set("menu.rows", Math.clamp(old.getInt("gui-size") / 9, 1, PluginConfig.MAX_ROWS));
        }
        copy(old, "show-status", c, "menu.show-status");
        copy(old, "show-player-count", c, "menu.show-players");
        copy(old, "countdown-time", c, "transfer.countdown-seconds");
        copy(old, "give-join-item", c, "join-item.enabled");
        copy(old, "join-item-material", c, "join-item.material");
        copy(old, "join-item-slot", c, "join-item.slot");
        copy(old, "join-item-name", c, "join-item.name");

        c.set("servers", null);
        ConfigurationSection servers = old.getConfigurationSection("servers");
        if (servers != null) {
            for (String id : servers.getKeys(false)) {
                ConfigurationSection s = servers.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                String base = "servers." + id + ".";
                c.set(base + "address", s.getString("address", "localhost"));
                c.set(base + "name", s.getString("display_name", id));
                c.set(base + "icon", s.getString("material", "GRASS_BLOCK"));
                if (s.contains("slot")) {
                    c.set(base + "slot", s.getInt("slot"));
                }
                c.set(base + "lore", s.getStringList("lore"));
                String perm = s.getString("permission", "");
                if (!perm.isBlank()) {
                    c.set(base + "permission", perm);
                }
            }
        }
        if (!c.isConfigurationSection("servers")) {
            c.createSection("servers");
        }
        return c;
    }

    private static void copy(ConfigurationSection from, String fromPath, ConfigurationSection to, String toPath) {
        if (from.contains(fromPath)) {
            to.set(toPath, from.get(fromPath));
        }
    }
}
