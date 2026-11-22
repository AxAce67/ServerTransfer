package sh.aki.servertransfer.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** The status part of the Minecraft protocol (handshake, status request, JSON response). */
final class StatusProtocol {

    /** Servers answer status requests regardless of the version, so any protocol number works. */
    static final int PROTOCOL_VERSION = -1;
    private static final int MAX_RESPONSE = 1 << 20;

    private StatusProtocol() {
    }

    /** Handshake (next state: status) followed by the status request. */
    static byte[] statusRequest(String host, int port) {
        ByteArrayOutputStream handshake = new ByteArrayOutputStream();
        writeVarInt(handshake, 0x00);
        writeVarInt(handshake, PROTOCOL_VERSION);
        writeString(handshake, host);
        handshake.write((port >> 8) & 0xFF);
        handshake.write(port & 0xFF);
        writeVarInt(handshake, 1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, handshake.size());
        out.writeBytes(handshake.toByteArray());
        writeVarInt(out, 1);
        writeVarInt(out, 0x00);
        return out.toByteArray();
    }

    static String readStatusJson(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(raw);
        int length = readVarInt(in);
        if (length <= 0 || length > MAX_RESPONSE) {
            throw new IOException("bad packet length " + length);
        }
        int id = readVarInt(in);
        if (id != 0x00) {
            throw new IOException("unexpected packet " + id);
        }
        int size = readVarInt(in);
        if (size < 0 || size > length) {
            throw new IOException("bad string length " + size);
        }
        byte[] json = new byte[size];
        in.readFully(json);
        return new String(json, StandardCharsets.UTF_8);
    }

    static ServerStatus parse(String json, long latencyMs) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int online = 0;
        int max = 0;
        if (root.get("players") instanceof JsonObject players) {
            online = intOr(players.get("online"));
            max = intOr(players.get("max"));
        }
        String version = "";
        if (root.get("version") instanceof JsonObject v && v.get("name") != null && v.get("name").isJsonPrimitive()) {
            version = v.get("name").getAsString();
        }
        return new ServerStatus(true, online, max, latencyMs, version);
    }

    private static int intOr(JsonElement e) {
        try {
            return e == null || !e.isJsonPrimitive() ? 0 : e.getAsInt();
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("VarInt too long");
    }

    private static void writeString(ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }
}
