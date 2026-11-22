package sh.aki.servertransfer.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigTest {

    private static final Predicate<String> ITEMS = Set.of("GRASS_BLOCK", "COMPASS", "DIAMOND",
            "GRAY_STAINED_GLASS_PANE")::contains;

    static YamlConfiguration yaml(String s) throws InvalidConfigurationException {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(s);
        return c;
    }

    static YamlConfiguration bundled() throws IOException {
        try (InputStream in = PluginConfigTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void bundledConfigLoadsCleanly() throws IOException {
        PluginConfig c = PluginConfig.parse(bundled(), ITEMS);
        assertEquals(List.of(), c.warnings());
        assertEquals("auto", c.language());
        assertEquals(1, c.menu().rows());
        assertEquals(3, c.transfer().countdownSeconds());
        assertTrue(c.servers().isEmpty());
    }

    @Test
    void placesServersAndResolvesConflicts() throws Exception {
        PluginConfig c = PluginConfig.parse(yaml("""
                servers:
                  a: {address: "a.example.com", slot: 2}
                  b: {address: "b.example.com", slot: 2}
                  c: {address: "c.example.com"}
                  d: {address: "not valid:x"}
                  e: {address: "e.example.com", icon: NOPE, slot: 99}
                """), ITEMS);
        assertEquals(List.of("b", "c", "a", "e"), c.servers().stream().map(ServerEntry::id).toList());
        assertEquals(List.of(0, 1, 2, 3), c.servers().stream().map(ServerEntry::slot).toList());
        assertEquals("GRASS_BLOCK", c.server("E").orElseThrow().icon());
        assertEquals(4, c.warnings().size());
    }

    @Test
    void rowsGrowToFitSlots() throws Exception {
        PluginConfig c = PluginConfig.parse(yaml("""
                menu: {rows: 1}
                servers:
                  far: {address: "x", slot: 20}
                """), ITEMS);
        assertEquals(3, c.menu().rows());
        assertTrue(c.warnings().isEmpty());
    }

    @Test
    void clampsOutOfRangeValues() throws Exception {
        PluginConfig c = PluginConfig.parse(yaml("""
                menu: {rows: 9, filler: NOPE}
                join-item: {slot: 12, material: NOPE}
                status: {timeout-ms: 5}
                """), ITEMS);
        assertEquals(6, c.menu().rows());
        assertEquals("", c.menu().filler());
        assertEquals(8, c.joinItem().slot());
        assertEquals("COMPASS", c.joinItem().material());
        assertEquals(100, c.status().timeoutMs());
        assertEquals(4, c.warnings().size());
        assertFalse(c.joinItem().enabled());
    }
}
