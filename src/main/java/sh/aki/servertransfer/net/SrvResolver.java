package sh.aki.servertransfer.net;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import java.net.InetSocketAddress;
import java.util.Hashtable;

/** Finds where to actually connect for a status ping, following {@code _minecraft._tcp} SRV records. */
final class SrvResolver {

    private SrvResolver() {
    }

    static InetSocketAddress resolve(ServerAddress address) {
        if (address.usesSrv()) {
            InetSocketAddress srv = lookup(address.host());
            if (srv != null) {
                return srv;
            }
        }
        return new InetSocketAddress(address.host(), address.port());
    }

    private static InetSocketAddress lookup(String host) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "1000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            Attribute srv = new InitialDirContext(env)
                    .getAttributes("_minecraft._tcp." + host, new String[]{"SRV"})
                    .get("SRV");
            if (srv == null || srv.size() == 0) {
                return null;
            }
            // priority weight port target
            String[] parts = srv.get(0).toString().trim().split("\\s+");
            if (parts.length < 4) {
                return null;
            }
            String target = parts[3].endsWith(".") ? parts[3].substring(0, parts[3].length() - 1) : parts[3];
            return new InetSocketAddress(target, Integer.parseInt(parts[2]));
        } catch (NamingException | RuntimeException e) {
            return null;
        }
    }
}
