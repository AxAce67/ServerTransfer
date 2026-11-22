package sh.aki.servertransfer;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import sh.aki.servertransfer.command.StCommand;
import sh.aki.servertransfer.config.ConfigMigrator;
import sh.aki.servertransfer.config.PluginConfig;
import sh.aki.servertransfer.i18n.Messages;
import sh.aki.servertransfer.item.JoinItems;
import sh.aki.servertransfer.menu.Menus;
import sh.aki.servertransfer.net.StatusService;
import sh.aki.servertransfer.transfer.TransferService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ServerTransferPlugin extends JavaPlugin {

    private volatile PluginConfig settings;
    private volatile Messages messages;
    private StatusService statuses;
    private Menus menus;
    private TransferService transfers;
    private JoinItems joinItems;
    private NamespacedKey editorKey;
    private NamespacedKey joinItemKey;

    @Override
    public void onEnable() {
        editorKey = new NamespacedKey(this, "editor_server");
        joinItemKey = new NamespacedKey(this, "join_item");
        statuses = new StatusService(4, 1500);
        if (!load()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        menus = new Menus(this);
        transfers = new TransferService(this);
        joinItems = new JoinItems(this);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(transfers, this);
        getServer().getPluginManager().registerEvents(joinItems, this);

        StCommand command = new StCommand(this);
        PluginCommand st = Objects.requireNonNull(getCommand("st"));
        st.setExecutor(command);
        st.setTabCompleter(command);

        getServer().getScheduler().runTaskTimer(this, menus::tick, 20L, 20L);
        getServer().getOnlinePlayers().forEach(joinItems::apply);
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.closeAll();
        }
        if (transfers != null) {
            transfers.cancelAll();
        }
        if (statuses != null) {
            statuses.shutdown();
        }
    }

    /** Reloads config and messages. On failure the previous state is kept. */
    public boolean load() {
        try {
            saveDefaultConfig();
            migrateIfNeeded();
            reloadConfig();
            PluginConfig parsed = PluginConfig.parse(getConfig(), ServerTransferPlugin::isItem);
            Messages loaded = Messages.load(parsed.language(), getDataFolder().toPath().resolve("lang"));
            parsed.warnings().forEach(w -> getLogger().warning("config.yml: " + w));
            settings = parsed;
            messages = loaded;
            statuses.setTimeout(parsed.status().timeoutMs());
            statuses.clear();
            return true;
        } catch (IOException | RuntimeException e) {
            getLogger().severe("Could not load the configuration: " + e);
            return settings != null;
        }
    }

    public void reload() {
        menus.closeAll();
        load();
        getServer().getOnlinePlayers().forEach(joinItems::apply);
    }

    private void migrateIfNeeded() throws IOException {
        File file = new File(getDataFolder(), "config.yml");
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        if (!ConfigMigrator.isLegacy(current)) {
            return;
        }
        File backup = new File(getDataFolder(), "config-1.x.yml");
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        YamlConfiguration defaults;
        try (InputStream in = Objects.requireNonNull(getResource("config.yml"))) {
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        ConfigMigrator.migrate(current, defaults).save(file);
        getLogger().info("Converted config.yml from 1.x. The old file was saved as " + backup.getName());
    }

    public static boolean isItem(String name) {
        Material m = Material.matchMaterial(name);
        return m != null && m.isItem() && !m.isAir();
    }

    // Config changes made in game

    public void addServer(String id, String address, String icon, int slot) {
        FileConfiguration c = getConfig();
        String base = "servers." + id + ".";
        c.set(base + "address", address);
        c.set(base + "name", "<white>" + id);
        c.set(base + "icon", icon);
        if (slot >= 0) {
            c.set(base + "slot", slot);
        }
        c.set(base + "lore", List.of("<yellow>Click to join"));
        saveAndReload();
    }

    public void removeServer(String id) {
        getConfig().set("servers." + id, null);
        saveAndReload();
    }

    public void saveLayout(int rows, Map<String, Integer> slots) {
        FileConfiguration c = getConfig();
        c.set("menu.rows", rows);
        ConfigurationSection servers = c.getConfigurationSection("servers");
        if (servers != null) {
            slots.forEach((id, slot) -> {
                if (servers.isConfigurationSection(id)) {
                    servers.set(id + ".slot", slot);
                }
            });
        }
        saveAndReload();
    }

    private void saveAndReload() {
        saveConfig();
        reload();
    }

    /** Runs on the main thread, unless the plugin was disabled in the meantime. */
    public void sync(Runnable task) {
        if (isEnabled()) {
            getServer().getScheduler().runTask(this, task);
        }
    }

    public PluginConfig settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public StatusService statuses() {
        return statuses;
    }

    public Menus menus() {
        return menus;
    }

    public TransferService transfers() {
        return transfers;
    }

    public NamespacedKey editorKey() {
        return editorKey;
    }

    public NamespacedKey joinItemKey() {
        return joinItemKey;
    }
}
