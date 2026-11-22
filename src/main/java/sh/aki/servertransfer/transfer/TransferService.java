package sh.aki.servertransfer.transfer;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import sh.aki.servertransfer.ServerTransferPlugin;
import sh.aki.servertransfer.config.PluginConfig;
import sh.aki.servertransfer.config.ServerEntry;
import sh.aki.servertransfer.i18n.Messages;
import sh.aki.servertransfer.menu.Menus;
import sh.aki.servertransfer.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ping check, countdown and the transfer itself. One pending transfer per player. */
public final class TransferService implements Listener {

    public static final String BYPASS = "servertransfer.bypass";
    private static final long PING_MAX_AGE_MS = 2000;

    private static final class Pending {
        final ServerEntry server;
        BukkitTask countdown;

        Pending(ServerEntry server) {
            this.server = server;
        }
    }

    private final ServerTransferPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Long> lastTransfer = new HashMap<>();

    public TransferService(ServerTransferPlugin plugin) {
        this.plugin = plugin;
    }

    /** A player asked to go to a server: permission, cooldown, ping, countdown. */
    public void request(Player player, ServerEntry server) {
        Messages m = plugin.messages();
        if (!Menus.canSee(player, server)) {
            m.send(player, "error.no-permission");
            return;
        }
        if (pending.containsKey(player.getUniqueId())) {
            m.send(player, "transfer.already");
            return;
        }
        boolean bypass = player.hasPermission(BYPASS);
        long cooldownMs = plugin.settings().transfer().cooldownSeconds() * 1000L;
        Long last = lastTransfer.get(player.getUniqueId());
        if (!bypass && last != null && System.currentTimeMillis() - last < cooldownMs) {
            long left = (cooldownMs - (System.currentTimeMillis() - last) + 999) / 1000;
            m.send(player, "transfer.cooldown", left);
            return;
        }
        start(player, server, bypass ? 0 : plugin.settings().transfer().countdownSeconds());
    }

    /** Sent by an admin: no countdown and no cooldown, but the target must still be online. */
    public void send(Player player, ServerEntry server) {
        cancel(player, null);
        start(player, server, 0);
    }

    private void start(Player player, ServerEntry server, int countdown) {
        Pending p = new Pending(server);
        pending.put(player.getUniqueId(), p);
        if (!plugin.settings().transfer().checkOnline()) {
            countdown(player, p, countdown);
            return;
        }
        plugin.messages().send(player, "transfer.checking", name(server));
        plugin.statuses().get(server.address(), PING_MAX_AGE_MS).thenAccept(status -> plugin.sync(() -> {
            if (pending.get(player.getUniqueId()) != p || !player.isOnline()) {
                return;
            }
            if (!status.online()) {
                pending.remove(player.getUniqueId());
                plugin.messages().send(player, "transfer.offline", name(server));
                sound(player, "entity.villager.no", 1f);
                return;
            }
            countdown(player, p, countdown);
        }));
    }

    private void countdown(Player player, Pending p, int seconds) {
        if (seconds <= 0) {
            transfer(player, p);
            return;
        }
        int[] left = {seconds};
        p.countdown = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (left[0] <= 0) {
                transfer(player, p);
                return;
            }
            player.sendActionBar(plugin.messages().get(player, "transfer.countdown", left[0], name(p.server)));
            sound(player, "block.note_block.hat", 1.2f);
            left[0]--;
        }, 0L, 20L);
    }

    private void transfer(Player player, Pending p) {
        if (p.countdown != null) {
            p.countdown.cancel();
        }
        pending.remove(player.getUniqueId());
        if (!player.isOnline()) {
            return;
        }
        lastTransfer.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendActionBar(plugin.messages().get(player, "transfer.sending", name(p.server)));
        sound(player, "entity.enderman.teleport", 1f);
        player.transfer(p.server.address().host(), p.server.address().port());
        plugin.getLogger().info(player.getName() + " -> " + p.server.id() + " (" + p.server.address() + ")");
    }

    /** @param reasonKey message for the player, or null for none */
    public void cancel(Player player, String reasonKey) {
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            return;
        }
        if (p.countdown != null) {
            p.countdown.cancel();
        }
        if (reasonKey != null) {
            plugin.messages().send(player, reasonKey);
            player.sendActionBar(Component.empty());
            sound(player, "block.note_block.bass", 0.6f);
        }
    }

    public void cancelAll() {
        pending.values().forEach(p -> {
            if (p.countdown != null) {
                p.countdown.cancel();
            }
        });
        pending.clear();
    }

    private boolean counting(Player player) {
        Pending p = pending.get(player.getUniqueId());
        return p != null && p.countdown != null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        PluginConfig.Transfer t = plugin.settings().transfer();
        if (t.cancelOnMove() && event.hasChangedBlock() && counting(event.getPlayer())) {
            cancel(event.getPlayer(), "transfer.cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && plugin.settings().transfer().cancelOnDamage()
                && counting(player)) {
            cancel(player, "transfer.cancelled-damage");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer(), null);
        long keep = plugin.settings().transfer().cooldownSeconds() * 1000L;
        lastTransfer.values().removeIf(t -> System.currentTimeMillis() - t > keep);
    }

    private static Component name(ServerEntry s) {
        return Text.parse(s.name(), Map.of("online", "-", "max", "-", "ping", "-"));
    }

    private static void sound(Player player, String key, float pitch) {
        player.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 1f, pitch));
    }
}
