package sh.aki.servertransfer.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    private static final String V1 = """
            language: ja
            gui-title: "&1Server Selector"
            gui-size: 18
            show-status: true
            show-player-count: false
            countdown-time: 5
            status-separator: "&7----------------"
            give-join-item: true
            join-item-material: "CLOCK"
            join-item-slot: 4
            join-item-name: "&b&lMenu"
            servers:
              server0:
                material: DIAMOND
                display_name: "&aLobby"
                slot: 3
                address: "lobby.example.com:25566"
                permission: ""
                lore:
                  - "&7Click"
              server1:
                material: GRASS_BLOCK
                display_name: "&cPvP"
                slot: 4
                address: localhost
                permission: "group.pvp"
            """;

    @Test
    void convertsTheOldLayout() throws Exception {
        YamlConfiguration old = PluginConfigTest.yaml(V1);
        assertTrue(ConfigMigrator.isLegacy(old));
        YamlConfiguration migrated = ConfigMigrator.migrate(old, PluginConfigTest.bundled());
        assertFalse(ConfigMigrator.isLegacy(migrated));
        assertEquals(PluginConfig.CURRENT_VERSION, migrated.getInt("config-version"));

        // round trip through text, as it is saved to disk
        PluginConfig c = PluginConfig.parse(PluginConfigTest.yaml(migrated.saveToString()),
                Set.of("DIAMOND", "GRASS_BLOCK", "CLOCK", "COMPASS")::contains);
        assertEquals(List.of(), c.warnings());
        assertEquals("ja", c.language());
        assertEquals("&1Server Selector", c.menu().title());
        assertEquals(2, c.menu().rows());
        assertFalse(c.menu().showPlayers());
        assertEquals(5, c.transfer().countdownSeconds());
        assertTrue(c.joinItem().enabled());
        assertEquals("CLOCK", c.joinItem().material());
        assertEquals(4, c.joinItem().slot());

        ServerEntry lobby = c.server("server0").orElseThrow();
        assertEquals("&aLobby", lobby.name());
        assertEquals("DIAMOND", lobby.icon());
        assertEquals(3, lobby.slot());
        assertEquals(25566, lobby.address().port());
        assertEquals(List.of("&7Click"), lobby.lore());
        assertFalse(lobby.needsPermission());
        assertEquals("group.pvp", c.server("server1").orElseThrow().permission());
    }

    @Test
    void currentConfigIsNotLegacy() throws Exception {
        assertFalse(ConfigMigrator.isLegacy(PluginConfigTest.bundled()));
        assertFalse(ConfigMigrator.isLegacy(PluginConfigTest.yaml("servers: {}")));
    }
}
