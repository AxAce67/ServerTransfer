package sh.aki.servertransfer.net;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/** Pings servers in the background and remembers the last answer per address. */
public final class StatusService {

    private record Entry(ServerStatus status, long at) {
    }

    private final ExecutorService pool;
    private final Map<ServerAddress, Entry> cache = new ConcurrentHashMap<>();
    private final Map<ServerAddress, CompletableFuture<ServerStatus>> inFlight = new ConcurrentHashMap<>();
    private volatile int timeoutMs;

    public StatusService(int threads, int timeoutMs) {
        AtomicInteger n = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "ServerTransfer-ping-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        this.timeoutMs = timeoutMs;
    }

    public void setTimeout(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Optional<ServerStatus> cached(ServerAddress address) {
        return Optional.ofNullable(cache.get(address)).map(Entry::status);
    }

    public boolean isFresh(ServerAddress address, long maxAgeMs) {
        Entry e = cache.get(address);
        return e != null && System.currentTimeMillis() - e.at() <= maxAgeMs;
    }

    /** The cached status if it is recent enough, otherwise a new ping (shared with any ping already running). */
    public CompletableFuture<ServerStatus> get(ServerAddress address, long maxAgeMs) {
        Entry e = cache.get(address);
        if (e != null && System.currentTimeMillis() - e.at() <= maxAgeMs) {
            return CompletableFuture.completedFuture(e.status());
        }
        CompletableFuture<ServerStatus> created = new CompletableFuture<>();
        CompletableFuture<ServerStatus> running = inFlight.putIfAbsent(address, created);
        if (running != null) {
            return running;
        }
        try {
            pool.execute(() -> {
                ServerStatus result = StatusPinger.ping(address, timeoutMs);
                cache.put(address, new Entry(result, System.currentTimeMillis()));
                inFlight.remove(address, created);
                created.complete(result);
            });
        } catch (RejectedExecutionException rejected) {
            inFlight.remove(address, created);
            created.complete(ServerStatus.offline());
        }
        return created;
    }

    public void clear() {
        cache.clear();
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
