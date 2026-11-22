package sh.aki.servertransfer.net;

import java.util.Locale;

/**
 * A configured server address: {@code host}, {@code host:port}, {@code [ipv6]} or {@code [ipv6]:port}.
 * The host is kept as written, because players are transferred to the name, not the resolved IP
 * (proxies and hosting providers route by it).
 */
public record ServerAddress(String host, int port, boolean portGiven) {

    public static final int DEFAULT_PORT = 25565;

    public ServerAddress {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("empty host");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
    }

    public static ServerAddress parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("empty address");
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("empty address");
        }
        String host;
        String port = null;
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            if (end < 0) {
                throw new IllegalArgumentException("missing ] in " + raw);
            }
            host = s.substring(1, end);
            String rest = s.substring(end + 1);
            if (!rest.isEmpty()) {
                if (!rest.startsWith(":")) {
                    throw new IllegalArgumentException("unexpected text after ] in " + raw);
                }
                port = rest.substring(1);
            }
        } else {
            int colon = s.indexOf(':');
            if (colon >= 0 && s.indexOf(':', colon + 1) >= 0) {
                throw new IllegalArgumentException("IPv6 addresses need brackets: [" + s + "]");
            }
            host = colon < 0 ? s : s.substring(0, colon);
            port = colon < 0 ? null : s.substring(colon + 1);
        }
        if (host.isBlank() || host.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("invalid host in " + raw);
        }
        if (port == null) {
            return new ServerAddress(host.toLowerCase(Locale.ROOT), DEFAULT_PORT, false);
        }
        int p;
        try {
            p = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid port in " + raw);
        }
        return new ServerAddress(host.toLowerCase(Locale.ROOT), p, true);
    }

    /** Like the vanilla client, an SRV record is only looked up for the default port. */
    public boolean usesSrv() {
        return port == DEFAULT_PORT && !isIpLiteral();
    }

    public boolean isIpLiteral() {
        return host.contains(":") || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    @Override
    public String toString() {
        String h = host.contains(":") ? "[" + host + "]" : host;
        return portGiven ? h + ":" + port : h;
    }
}
