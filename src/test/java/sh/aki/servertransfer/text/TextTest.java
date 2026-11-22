package sh.aki.servertransfer.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void miniMessageAndLegacyBothWork() {
        assertEquals("Lobby", plain(Text.parse("<green>Lobby")));
        assertEquals("Lobby", plain(Text.parse("&aLobby")));
        assertEquals("Lobby", plain(Text.parse("§aLobby")));
        assertEquals(NamedTextColor.GREEN, Text.parse("&aLobby").children().isEmpty()
                ? Text.parse("&aLobby").color() : Text.parse("&aLobby").children().getFirst().color());
        assertTrue(Text.isLegacy("&#ff00ffPink"));
        assertFalse(Text.isLegacy("Tom & Jerry"));
        assertEquals("Tom & Jerry", plain(Text.parse("Tom & Jerry")));
    }

    @Test
    void placeholders() {
        assertEquals("3/20 players", plain(Text.parse("<gray>{online}/{max} players",
                Map.of("online", "3", "max", "20"))));
    }

    @Test
    void itemTextIsNotItalic() {
        Component c = Text.item(Text.parse("<green>Lobby"));
        assertEquals(TextDecoration.State.FALSE, c.decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.TRUE, Text.item(Text.parse("<italic>x")).decoration(TextDecoration.ITALIC));
    }
}
