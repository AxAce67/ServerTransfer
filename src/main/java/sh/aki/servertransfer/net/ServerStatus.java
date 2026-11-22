package sh.aki.servertransfer.net;

/** Result of a status ping. {@code latencyMs} is the time to the status response. */
public record ServerStatus(boolean online, int players, int maxPlayers, long latencyMs, String version) {

    public static ServerStatus offline() {
        return new ServerStatus(false, 0, 0, -1, "");
    }
}
