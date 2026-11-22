package sh.aki.servertransfer.config;

import sh.aki.servertransfer.net.ServerAddress;

import java.util.List;

/** One server in the menu. {@code name} and {@code lore} are raw config text (MiniMessage or legacy). */
public record ServerEntry(
        String id,
        ServerAddress address,
        String name,
        String icon,
        int slot,
        List<String> lore,
        String permission
) {

    public boolean needsPermission() {
        return permission != null && !permission.isBlank();
    }
}
