package sh.aki.servertransfer.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerAddressTest {

    @Test
    void hostOnlyUsesDefaultPortAndSrv() {
        ServerAddress a = ServerAddress.parse(" Lobby.Example.com ");
        assertEquals("lobby.example.com", a.host());
        assertEquals(25565, a.port());
        assertFalse(a.portGiven());
        assertTrue(a.usesSrv());
        assertEquals("lobby.example.com", a.toString());
    }

    @Test
    void explicitPort() {
        ServerAddress a = ServerAddress.parse("mc.example.com:25570");
        assertEquals(25570, a.port());
        assertTrue(a.portGiven());
        assertFalse(a.usesSrv());
        assertEquals("mc.example.com:25570", a.toString());
    }

    @Test
    void ipLiteralsSkipSrv() {
        assertFalse(ServerAddress.parse("127.0.0.1").usesSrv());
        ServerAddress v6 = ServerAddress.parse("[::1]:25566");
        assertEquals("::1", v6.host());
        assertEquals(25566, v6.port());
        assertEquals("[::1]:25566", v6.toString());
        assertFalse(ServerAddress.parse("[2001:db8::1]").usesSrv());
    }

    @Test
    void rejectsBadInput() {
        for (String bad : new String[]{"", "  ", ":25565", "host:", "host:abc", "host:0", "host:70000",
                "::1", "[::1", "[::1]x", "a b"}) {
            assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse(bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse(null));
    }
}
