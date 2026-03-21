package me.aki.serverTransfer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ServerTransfer extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private final Component SETTING_TITLE = color("&4&lGUI Editor (Close to Save)");
    private final Map<UUID, BukkitTask> countdowns = new HashMap<>();

    @Override
    public void onEnable() {
        try {
            Player.class.getMethod("transfer", String.class, int.class);
        } catch (NoSuchMethodException e) {
            getLogger().severe("==================================================");
            getLogger().severe("Critical Error: Incompatible Server Version!");
            getLogger().severe("This plugin requires Minecraft 1.20.5 or newer.");
            getLogger().severe("Disabling plugin...");
            getLogger().severe("==================================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        var cmd = getCommand("st");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    private String msg(String key) {
        boolean isJa = getConfig().getString("language", "en").equalsIgnoreCase("ja");
        return switch (key) {
            case "reload" -> isJa ? "&a設定を再読み込みしました。" : "&aConfiguration reloaded.";
            case "no-perm" -> isJa ? "&c権限がありません。" : "&cNo permission.";
            case "usage" -> isJa ? "&c使用法: /st help を参照してください。" : "&cUsage: See /st help.";
            case "connecting" -> isJa ? "&7接続確認中..." : "&7Checking connection...";
            case "offline" -> isJa ? "&cサーバーがオフラインです。" : "&cServer is offline.";
            case "online-tag" -> isJa ? "&a● オンライン" : "&a● Online";
            case "offline-tag" -> isJa ? "&c● オフライン" : "&c● Offline";
            case "countdown" -> isJa ? "&e%s 秒後に転送します... (動くとキャンセル)" : "&eTeleporting in %s sec... (Move to cancel)";
            case "cancelled" -> isJa ? "&c移動したため転送をキャンセルしました。" : "&cTeleport canceled due to movement.";
            case "saved" -> isJa ? "&aレイアウトを保存しました。詳細は ServerTransfer/config.yml で編集可能です。" : "&aLayout saved. You can edit details in ServerTransfer/config.yml.";
            case "item-name" -> isJa ? "&b&lサーバーメニュー &7(右クリック)" : "&b&lServer Menu &7(Right Click)";
            case "help-title" -> isJa ? "&b--- ServerTransfer ヘルプ ---" : "&b--- ServerTransfer Help ---";
            case "help-st" -> isJa ? "&e/st &7- サーバー選択GUIを開きます。" : "&e/st &7- Open server selector GUI.";
            case "help-reload" -> isJa ? "&e/st reload &7- 設定を再読み込みします。" : "&e/st reload &7- Reload configuration.";
            case "help-setting" -> isJa ? "&e/st setting [size] &7- GUIエディターを開きます。" : "&e/st setting [size] &7- Open GUI editor.";
            case "help-help" -> isJa ? "&e/st help &7- このヘルプを表示します。" : "&e/st help &7- Show this help.";
            default -> "";
        };
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (!player.hasPermission("servertransfer.use")) {
            player.sendMessage(color(msg("no-perm")));
            return true;
        }

        if (args.length >= 1) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "help" -> {
                    sendHelp(player);
                    return true;
                }
                case "reload" -> {
                    if (!player.hasPermission("servertransfer.admin")) {
                        player.sendMessage(color(msg("no-perm")));
                        return true;
                    }
                    reloadConfig();
                    player.sendMessage(color(msg("reload")));
                    return true;
                }
                case "setting" -> {
                    if (!player.hasPermission("servertransfer.admin")) {
                        player.sendMessage(color(msg("no-perm")));
                        return true;
                    }
                    int size = (args.length == 2) ? Integer.parseInt(args[1]) : 9;
                    openSettingPanel(player, size);
                    return true;
                }
            }
        }
        openGUIWithStatus(player);
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(color(msg("help-title")));
        player.sendMessage(color(msg("help-st")));
        player.sendMessage(color(msg("help-help")));
        if (player.hasPermission("servertransfer.admin")) {
            player.sendMessage(color(msg("help-reload")));
            player.sendMessage(color(msg("help-setting")));
        }
    }

    public void openGUIWithStatus(Player player) {
        int size = getConfig().getInt("gui-size", 9);
        Inventory inv = Bukkit.createInventory(null, size, color(getConfig().getString("gui-title", "&1Server Selector")));
        setupInventory(player, inv);
        player.openInventory(inv);

        if (!getConfig().getBoolean("show-status", true)) return;
        ConfigurationSection section = getConfig().getConfigurationSection("servers");
        if (section == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            for (String key : section.getKeys(false)) {
                String perm = section.getString(key + ".permission");
                if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) continue;

                String rawAddr = section.getString(key + ".address", "localhost");
                int slot = section.getInt(key + ".slot");

                ResolvedAddress res = resolve(rawAddr);
                PingResult result = ping(res.host(), res.port());
                String statusLine = result.online ? msg("online-tag") : msg("offline-tag");
                if (result.online && getConfig().getBoolean("show-player-count", true)) {
                    statusLine += " &7(" + result.players + "/" + result.maxPlayers + ")";
                }

                final String finalStatusLine = statusLine;
                Bukkit.getScheduler().runTask(this, () -> {
                    ItemStack item = inv.getItem(slot);
                    if (item != null) item.editMeta(meta -> {
                        List<Component> lore = meta.hasLore() ? Objects.requireNonNull(meta.lore()) : new ArrayList<>();
                        lore.add(color(getConfig().getString("status-separator", "&7----------------")));
                        lore.add(color(finalStatusLine));
                        meta.lore(lore);
                    });
                });
            }
        });
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!event.getView().title().equals(color(getConfig().getString("gui-title", "&1Server Selector")))) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        Player player = (Player) event.getWhoClicked();
        ConfigurationSection section = getConfig().getConfigurationSection("servers");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            if (Objects.equals(item.getItemMeta().displayName(), color(section.getString(key + ".display_name", "")))) {
                String rawAddr = section.getString(key + ".address", "localhost");
                String perm = section.getString(key + ".permission");
                if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
                    player.sendMessage(color(msg("no-perm")));
                    return;
                }

                player.closeInventory();
                player.sendMessage(color(msg("connecting")));

                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    ResolvedAddress res = resolve(rawAddr);
                    if (isServerReachable(res.host(), res.port())) {
                        Bukkit.getScheduler().runTask(this, () -> startTransferSequence(player, res));
                    } else {
                        Bukkit.getScheduler().runTask(this, () -> {
                            player.sendMessage(color(msg("offline")));
                            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 2f);
                        });
                    }
                });
                return;
            }
        }
    }

    private void startTransferSequence(Player player, ResolvedAddress res) {
        int seconds = getConfig().getInt("countdown-time", 3);
        if (seconds <= 0) {
            performTransfer(player, res);
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            int timeLeft = seconds;
            @Override
            public void run() {
                if (timeLeft > 0) {
                    player.sendActionBar(color(String.format(msg("countdown"), timeLeft)));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                    timeLeft--;
                } else {
                    Optional.ofNullable(countdowns.remove(player.getUniqueId())).ifPresent(BukkitTask::cancel);
                    performTransfer(player, res);
                }
            }
        }, 0L, 20L);
        countdowns.put(player.getUniqueId(), task);
    }

    @SuppressWarnings("UnstableApiUsage")
    private void performTransfer(Player player, ResolvedAddress res) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        player.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
        player.transfer(res.host(), res.port());
    }

    @EventHandler
    public void onMove(@NotNull PlayerMoveEvent event) {
        if (event.hasChangedBlock() && countdowns.containsKey(event.getPlayer().getUniqueId())) {
            Optional.ofNullable(countdowns.remove(event.getPlayer().getUniqueId())).ifPresent(BukkitTask::cancel);
            event.getPlayer().sendMessage(color(msg("cancelled")));
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        }
    }

    private PingResult ping(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            out.write(new byte[]{(byte) 0xFE, (byte) 0x01});
            if (in.read() != 0xFF) return new PingResult(true, 0, 0);
            int len = in.readShort();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) sb.append(in.readChar());
            String[] data = sb.toString().split("\0");
            if (data.length >= 6) return new PingResult(true, Integer.parseInt(data[4]), Integer.parseInt(data[5]));
            return new PingResult(true, 0, 0);
        } catch (Exception e) {
            return new PingResult(false, 0, 0);
        }
    }

    private ResolvedAddress resolve(String addr) {
        if (addr.contains(":")) {
            String[] p = addr.split(":");
            try { return new ResolvedAddress(p[0], Integer.parseInt(p[1])); } catch (Exception e) { return new ResolvedAddress(p[0], 25565); }
        }
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            Attributes attrs = new InitialDirContext(env).getAttributes("_minecraft._tcp." + addr, new String[]{"SRV"});
            Attribute srv = attrs.get("srv");
            if (srv != null) {
                String[] data = srv.get().toString().split(" ");
                return new ResolvedAddress(data[3].replaceAll("\\.$", ""), Integer.parseInt(data[2]));
            }
        } catch (Exception ignored) {}
        return new ResolvedAddress(addr, 25565);
    }

    private record ResolvedAddress(String host, int port) {}
    private record PingResult(boolean online, int players, int maxPlayers) {}

    private boolean isServerReachable(String h, int p) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(h, p), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private Component color(String m) {
        return SERIALIZER.deserialize(m != null ? m : "");
    }

    private void setupInventory(Player player, Inventory inv) {
        ConfigurationSection section = getConfig().getConfigurationSection("servers");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String perm = section.getString(key + ".permission");
            if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) continue;
            try {
                Material mat = Material.valueOf(section.getString(key + ".material", "GRASS_BLOCK").toUpperCase());
                ItemStack item = new ItemStack(mat);
                item.editMeta(meta -> {
                    meta.displayName(color(section.getString(key + ".display_name", key)));
                    meta.lore(section.getStringList(key + ".lore").stream().map(this::color).collect(Collectors.toList()));
                });
                inv.setItem(section.getInt(key + ".slot"), item);
            } catch (Exception ignored) {}
        }
    }

    public void openSettingPanel(Player player, int size) {
        Inventory inv = Bukkit.createInventory(null, size, SETTING_TITLE);
        setupInventory(player, inv);
        player.openInventory(inv);
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String a, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> sub = new ArrayList<>(List.of("help"));
            if (s.hasPermission("servertransfer.admin")) {
                sub.add("reload");
                sub.add("setting");
            }
            return sub.stream().filter(x -> x.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent e) {
        if (getConfig().getBoolean("give-join-item", true)) {
            ItemStack i = new ItemStack(Material.valueOf(getConfig().getString("join-item-material", "COMPASS").toUpperCase()));
            i.editMeta(m -> m.displayName(color(msg("item-name"))));
            e.getPlayer().getInventory().setItem(getConfig().getInt("join-item-slot", 8), i);
        }
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent e) {
        if (e.getAction().name().contains("RIGHT_CLICK") && e.getItem() != null) {
            String mat = getConfig().getString("join-item-material", "COMPASS").toUpperCase();
            if (e.getItem().getType() == Material.valueOf(mat)) {
                if (e.getPlayer().hasPermission("servertransfer.use")) {
                    openGUIWithStatus(e.getPlayer());
                } else {
                    e.getPlayer().sendMessage(color(msg("no-perm")));
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!event.getView().title().equals(SETTING_TITLE)) return;
        Inventory inv = event.getInventory();
        getConfig().set("gui-size", inv.getSize());
        ConfigurationSection old = getConfig().getConfigurationSection("servers");
        getConfig().set("servers", null);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            ItemMeta meta = item.getItemMeta();
            String name = SERIALIZER.serialize(Objects.requireNonNullElse(meta.displayName(), Component.text(item.getType().name())));
            String key = "server" + i;

            getConfig().set("servers." + key + ".material", item.getType().name());
            getConfig().set("servers." + key + ".display_name", name);
            getConfig().set("servers." + key + ".slot", i);

            String addr = "localhost";
            String perm = "";

            if (old != null) {
                for (String ok : old.getKeys(false)) {
                    if (Objects.equals(color(old.getString(ok + ".display_name", "")), meta.displayName())) {
                        addr = old.getString(ok + ".address", "localhost");
                        perm = old.getString(ok + ".permission", "");
                        break;
                    }
                }
            }
            getConfig().set("servers." + key + ".address", addr);
            getConfig().set("servers." + key + ".permission", perm);
        }
        saveConfig();
        event.getPlayer().sendMessage(color(msg("saved")));
    }
}
