package sh.aki.servertransfer.net;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusProtocolTest {

    @Test
    void varIntRoundTrip() throws IOException {
        for (int v : new int[]{0, 1, 127, 128, 255, 25565, 2097151, Integer.MAX_VALUE, -1, Integer.MIN_VALUE}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            StatusProtocol.writeVarInt(out, v);
            assertEquals(v, StatusProtocol.readVarInt(new DataInputStream(new ByteArrayInputStream(out.toByteArray()))));
        }
    }

    @Test
    void handshakeLayout() throws IOException {
        byte[] bytes = StatusProtocol.statusRequest("a.b", 25565);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        int length = StatusProtocol.readVarInt(in);
        assertEquals(0, StatusProtocol.readVarInt(in));
        assertEquals(-1, StatusProtocol.readVarInt(in));
        assertEquals(3, StatusProtocol.readVarInt(in));
        assertEquals("a.b", new String(in.readNBytes(3), StandardCharsets.US_ASCII));
        assertEquals(25565, in.readUnsignedShort());
        assertEquals(1, StatusProtocol.readVarInt(in));
        assertEquals(bytes.length - 2, length + 1);
        assertEquals(1, StatusProtocol.readVarInt(in));
        assertEquals(0, StatusProtocol.readVarInt(in));
        assertEquals(-1, in.read());
    }

    @Test
    void parsesStatusJson() {
        ServerStatus s = StatusProtocol.parse("""
                {"version":{"name":"Paper 1.21.4","protocol":769},
                 "players":{"max":100,"online":12,"sample":[]},
                 "description":{"text":"hello"}}""", 7);
        assertTrue(s.online());
        assertEquals(12, s.players());
        assertEquals(100, s.maxPlayers());
        assertEquals(7, s.latencyMs());
        assertEquals("Paper 1.21.4", s.version());

        ServerStatus minimal = StatusProtocol.parse("{\"description\":\"x\"}", 1);
        assertTrue(minimal.online());
        assertEquals(0, minimal.players());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IOException.class, () -> StatusProtocol.readStatusJson(new ByteArrayInputStream(new byte[]{0})));
        assertThrows(IOException.class, () -> StatusProtocol.readStatusJson(
                new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F})));
    }

    @Test
    void pingsAFakeServer() throws Exception {
        String json = "{\"version\":{\"name\":\"Fake\"},\"players\":{\"max\":20,\"online\":3}}";
        AtomicReference<String> seenHost = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread t = new Thread(() -> {
                try (Socket c = server.accept()) {
                    DataInputStream in = new DataInputStream(c.getInputStream());
                    StatusProtocol.readVarInt(in);
                    StatusProtocol.readVarInt(in);
                    StatusProtocol.readVarInt(in);
                    int hostLen = StatusProtocol.readVarInt(in);
                    seenHost.set(new String(in.readNBytes(hostLen), StandardCharsets.UTF_8));
                    in.readUnsignedShort();
                    StatusProtocol.readVarInt(in);
                    StatusProtocol.readVarInt(in);
                    StatusProtocol.readVarInt(in);
                    respond(c.getOutputStream(), json);
                } catch (IOException ignored) {
                    // the test fails on the client side
                }
            });
            t.start();
            ServerStatus s = StatusPinger.ping(new InetSocketAddress(InetAddress.getLoopbackAddress(),
                    server.getLocalPort()), "play.example.com", 25565, 2000);
            t.join(2000);
            assertTrue(s.online());
            assertEquals(3, s.players());
            assertEquals(20, s.maxPlayers());
            assertEquals("play.example.com", seenHost.get());
        }
    }

    @Test
    void closedPortIsOffline() throws IOException {
        int port;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = s.getLocalPort();
        }
        assertFalse(StatusPinger.ping(ServerAddress.parse("127.0.0.1:" + port), 500).online());
    }

    private static void respond(OutputStream out, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        StatusProtocol.writeVarInt(packet, 0);
        StatusProtocol.writeVarInt(packet, body.length);
        packet.writeBytes(body);
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        StatusProtocol.writeVarInt(framed, packet.size());
        framed.writeBytes(packet.toByteArray());
        out.write(framed.toByteArray());
        out.flush();
    }

}
