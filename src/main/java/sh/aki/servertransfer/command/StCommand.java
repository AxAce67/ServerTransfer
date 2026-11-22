package sh.aki.servertransfer.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sh.aki.servertransfer.ServerTransferPlugin;
import sh.aki.servertransfer.config.PluginConfig;
import sh.aki.servertransfer.config.ServerEntry;
import sh.aki.servertransfer.i18n.Messages;
import sh.aki.servertransfer.menu.Menus;
import sh.aki.servertransfer.net.ServerAddress;
import sh.aki.servertransfer.net.ServerStatus;
import sh.aki.servertransfer.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** {@code /st}: menu, direct transfer, and the admin commands. */
public final class StCommand implements TabExecutor {

    private static final String USE = "servertransfer.use";
    private static final String ADMIN = "servertransfer.admin";
    private static final List<String> PLAYER_SUBS = List.of("help", "list");
    private static final List<String> ADMIN_SUBS = List.of("send", "add", "remove", "edit", "reload");
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private final ServerTransferPlugin plugin;

    public StCommand(ServerTransferPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messages();
        if (!sender.hasPermission(USE)) {
            m.send(sender, "error.no-permission");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player p) {
                plugin.menus().open(p);
            } else {
                help(sender);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (ADMIN_SUBS.contains(sub) && !sender.hasPermission(ADMIN)) {
            m.send(sender, "error.no-permission");
            return true;
        }
        switch (sub) {
            case "help" -> help(sender);
            case "list" -> list(sender);
            case "send" -> send(sender, args);
            case "add" -> add(sender, args);
            case "remove" -> remove(sender, args);
            case "edit" -> edit(sender, args);
            case "reload" -> {
                plugin.reload();
                int warnings = plugin.settings().warnings().size();
                m.send(sender, warnings == 0 ? "reload.done" : "reload.warnings", plugin.settings().servers().size(),
                        warnings);
            }
            default -> join(sender, args[0]);
        }
        return true;
    }

    private void help(CommandSender sender) {
        Messages m = plugin.messages();
        m.send(sender, "help.header", plugin.getPluginMeta().getVersion());
        Stream.of("menu", "server", "list", "help").forEach(k -> m.send(sender, "help." + k));
        if (sender.hasPermission(ADMIN)) {
            ADMIN_SUBS.forEach(k -> m.send(sender, "help." + k));
        }
    }

    private void join(CommandSender sender, String id) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "error.player-only");
            return;
        }
        Optional<ServerEntry> server = plugin.settings().server(id).filter(s -> Menus.canSee(player, s));
        if (server.isEmpty()) {
            plugin.messages().send(sender, "error.unknown-server", id);
            return;
        }
        plugin.transfers().request(player, server.get());
    }

    private void list(CommandSender sender) {
        Messages m = plugin.messages();
        PluginConfig config = plugin.settings();
        List<ServerEntry> servers = config.servers().stream()
                .filter(s -> !(sender instanceof Player p) || Menus.canSee(p, s)).toList();
        if (servers.isEmpty()) {
            m.send(sender, "menu.no-servers");
            return;
        }
        List<CompletableFuture<ServerStatus>> pings = servers.stream()
                .map(s -> plugin.statuses().get(s.address(), config.status().refreshSeconds() * 1000L)).toList();
        CompletableFuture.allOf(pings.toArray(CompletableFuture[]::new)).thenRun(() -> plugin.sync(() -> {
            m.send(sender, "list.header", servers.size());
            for (int i = 0; i < servers.size(); i++) {
                sender.sendMessage(listLine(sender, servers.get(i), pings.get(i).join()));
            }
        }));
    }

    private Component listLine(CommandSender sender, ServerEntry s, ServerStatus status) {
        Messages m = plugin.messages();
        Component name = Text.parse(s.name(), Map.of("online", "-", "max", "-", "ping", "-"));
        Component line = status.online()
                ? m.get(sender, "list.online", name, status.players(), status.maxPlayers(), status.latencyMs())
                : m.get(sender, "list.offline", name);
        if (sender instanceof Player) {
            line = line.clickEvent(ClickEvent.runCommand("/st " + s.id()))
                    .hoverEvent(HoverEvent.showText(m.get(sender, "list.hover", s.id(), s.address().toString())));
        } else {
            line = line.append(Component.text("  " + s.id() + " " + s.address()));
        }
        return line;
    }

    private void send(CommandSender sender, String[] args) {
        Messages m = plugin.messages();
        if (args.length < 3) {
            m.send(sender, "usage.send");
            return;
        }
        Optional<ServerEntry> server = plugin.settings().server(args[2]);
        if (server.isEmpty()) {
            m.send(sender, "error.unknown-server", args[2]);
            return;
        }
        List<Player> targets = new ArrayList<>();
        try {
            for (Entity e : plugin.getServer().selectEntities(sender, args[1])) {
                if (e instanceof Player p) {
                    targets.add(p);
                }
            }
        } catch (IllegalArgumentException e) {
            m.send(sender, "error.bad-selector", args[1]);
            return;
        }
        if (targets.isEmpty()) {
            m.send(sender, "error.no-players", args[1]);
            return;
        }
        for (Player p : targets) {
            plugin.transfers().send(p, server.get());
        }
        m.send(sender, "send.done", targets.size(), Text.parse(server.get().name()));
    }

    private void add(CommandSender sender, String[] args) {
        Messages m = plugin.messages();
        if (args.length < 3) {
            m.send(sender, "usage.add");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches() || PLAYER_SUBS.contains(id) || ADMIN_SUBS.contains(id)) {
            m.send(sender, "add.bad-id", id);
            return;
        }
        if (plugin.settings().server(id).isPresent()) {
            m.send(sender, "add.exists", id);
            return;
        }
        try {
            ServerAddress.parse(args[2]);
        } catch (IllegalArgumentException e) {
            m.send(sender, "add.bad-address", args[2], e.getMessage());
            return;
        }
        int slot = -1;
        if (args.length >= 4) {
            slot = parseInt(args[3], -1);
            if (slot < 0 || slot >= PluginConfig.MAX_ROWS * 9) {
                m.send(sender, "add.bad-slot", args[3]);
                return;
            }
            int wanted = slot;
            if (plugin.settings().servers().stream().anyMatch(s -> s.slot() == wanted)) {
                m.send(sender, "add.slot-used", slot);
                return;
            }
        }
        String icon = "GRASS_BLOCK";
        if (sender instanceof Player p) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() != Material.AIR) {
                icon = hand.getType().name();
            }
        }
        plugin.addServer(id, args[2], icon, slot);
        m.send(sender, "add.done", id, args[2]);
    }

    private void remove(CommandSender sender, String[] args) {
        Messages m = plugin.messages();
        if (args.length < 2) {
            m.send(sender, "usage.remove");
            return;
        }
        Optional<ServerEntry> server = plugin.settings().server(args[1]);
        if (server.isEmpty()) {
            m.send(sender, "error.unknown-server", args[1]);
            return;
        }
        plugin.removeServer(server.get().id());
        m.send(sender, "remove.done", server.get().id());
    }

    private void edit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "error.player-only");
            return;
        }
        int rows = plugin.settings().menu().rows();
        if (args.length >= 2) {
            rows = parseInt(args[1], -1);
            if (rows < 1 || rows > PluginConfig.MAX_ROWS) {
                plugin.messages().send(sender, "usage.edit");
                return;
            }
        }
        plugin.menus().openEditor(player, rows);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(USE)) {
            return List.of();
        }
        boolean admin = sender.hasPermission(ADMIN);
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(PLAYER_SUBS);
            if (admin) {
                options.addAll(ADMIN_SUBS);
            }
            options.addAll(serverIds(sender));
        } else if (admin) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("send") && args.length == 2) {
                options.add("@a");
                plugin.getServer().getOnlinePlayers().forEach(p -> options.add(p.getName()));
            } else if ((sub.equals("send") && args.length == 3) || (sub.equals("remove") && args.length == 2)) {
                plugin.settings().servers().forEach(s -> options.add(s.id()));
            } else if (sub.equals("edit") && args.length == 2) {
                options.addAll(List.of("1", "2", "3", "4", "5", "6"));
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private List<String> serverIds(CommandSender sender) {
        return plugin.settings().servers().stream()
                .filter(s -> !(sender instanceof Player p) || Menus.canSee(p, s))
                .map(ServerEntry::id).toList();
    }
}
