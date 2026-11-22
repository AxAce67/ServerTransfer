package sh.aki.servertransfer.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import sh.aki.servertransfer.config.PluginConfig;
import sh.aki.servertransfer.config.ServerEntry;
import sh.aki.servertransfer.i18n.Messages;
import sh.aki.servertransfer.net.ServerStatus;
import sh.aki.servertransfer.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds the items shown in the menu. */
final class Icons {

    private Icons() {
    }

    static ItemStack server(ServerEntry s, ServerStatus status, PluginConfig.Menu menu, Messages m, CommandSender viewer) {
        Map<String, String> values = status == null || !status.online()
                ? Map.of("online", "-", "max", "-", "ping", "-")
                : Map.of("online", Integer.toString(status.players()), "max", Integer.toString(status.maxPlayers()),
                "ping", Long.toString(status.latencyMs()));
        ItemStack item = new ItemStack(Objects.requireNonNull(Material.matchMaterial(s.icon())));
        item.editMeta(meta -> {
            meta.displayName(Text.item(Text.parse(s.name(), values)));
            List<Component> lore = new ArrayList<>();
            for (String line : s.lore()) {
                lore.add(Text.item(Text.parse(line, values)));
            }
            if (menu.showStatus()) {
                if (!lore.isEmpty()) {
                    lore.add(Component.empty());
                }
                lore.add(Text.item(statusLine(status, menu.showPlayers(), m, viewer)));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    static Component statusLine(ServerStatus status, boolean players, Messages m, CommandSender viewer) {
        if (status == null) {
            return m.get(viewer, "menu.status.checking");
        }
        if (!status.online()) {
            return m.get(viewer, "menu.status.offline");
        }
        return players
                ? m.get(viewer, "menu.status.online-players", status.players(), status.maxPlayers())
                : m.get(viewer, "menu.status.online");
    }

    static ItemStack filler(String material) {
        ItemStack item = new ItemStack(Objects.requireNonNull(Material.matchMaterial(material)));
        item.editMeta(meta -> {
            meta.displayName(Component.space());
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }
}
