package sh.aki.servertransfer.net;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Blocking status ping. Call it off the main thread. */
public final class StatusPinger {

    private StatusPinger() {
    }

    public static ServerStatus ping(ServerAddress address, int timeoutMs) {
        try {
            InetSocketAddress target = SrvResolver.resolve(address);
            if (target.isUnresolved()) {
                return ServerStatus.offline();
            }
            return ping(target, address.host(), address.port(), timeoutMs);
        } catch (IOException | RuntimeException e) {
            return ServerStatus.offline();
        }
    }

    static ServerStatus ping(InetSocketAddress target, String handshakeHost, int handshakePort, int timeoutMs)
            throws IOException {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeoutMs);
            socket.setTcpNoDelay(true);
            socket.connect(target, timeoutMs);
            OutputStream out = socket.getOutputStream();
            out.write(StatusProtocol.statusRequest(handshakeHost, handshakePort));
            out.flush();
            String json = StatusProtocol.readStatusJson(socket.getInputStream());
            long latency = (System.nanoTime() - start) / 1_000_000;
            return StatusProtocol.parse(json, latency);
        }
    }
}
