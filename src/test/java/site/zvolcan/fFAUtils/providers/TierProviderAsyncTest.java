package site.zvolcan.fFAUtils.providers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.zvolcan.fFAUtils.managers.TierManager;
import site.zvolcan.fFAUtils.objects.TierProfile;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Proves that no tier lookup ever blocks the thread that asks for it.
 *
 * <p>
 * On a Paper server that thread is the main thread, and a blocked main thread
 * is dropped ticks. Rather than trusting that {@code sendAsync} behaves, these
 * tests point a provider at a local server that accepts the connection and then
 * never answers, so anything that blocks would visibly block for the full
 * request timeout.
 */
class TierProviderAsyncTest {

    /** Long enough that blocking would be unmistakable against the assertions. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    /** A generous ceiling for "returned without waiting on the network". */
    private static final long NON_BLOCKING_MILLIS = 1_000L;

    private ServerSocket stallingServer;
    private ExecutorService executor;
    private HttpClient httpClient;
    /** Counts down once the stalling server has a request on the wire. */
    private CountDownLatch requestOnTheWire;

    @BeforeEach
    void setUp() throws Exception {
        stallingServer = new ServerSocket();
        stallingServer.bind(new InetSocketAddress("127.0.0.1", 0));
        requestOnTheWire = new CountDownLatch(1);

        Thread accepter = new Thread(() -> {
            while (!stallingServer.isClosed()) {
                try {
                    Socket socket = stallingServer.accept();
                    Thread handler = new Thread(() -> {
                        requestOnTheWire.countDown();
                        try (InputStream in = socket.getInputStream()) {
                            in.read();
                            Thread.sleep(120_000); // never reply
                        } catch (Exception ignored) {
                            // Test is over.
                        }
                    });
                    handler.setDaemon(true);
                    handler.start();
                } catch (Exception ignored) {
                    return;
                }
            }
        });
        accepter.setDaemon(true);
        accepter.start();

        executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "test-tier-pool");
            thread.setDaemon(true);
            return thread;
        });
        httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .executor(executor)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        httpClient.shutdownNow();
        executor.shutdownNow();
        stallingServer.close();
    }

    /** The URL of the server that accepts and then stalls forever. */
    private String stallingUrl() {
        return "http://127.0.0.1:" + stallingServer.getLocalPort();
    }

    /** A server that answers every request with a minimal valid profile. */
    private ServerSocket respondingServer() throws Exception {
        ServerSocket server = new ServerSocket();
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        Thread accepter = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.getInputStream().read(new byte[1024]);
                    String json = "{\"name\":\"x\",\"rankings\":{\"vanilla\":{\"tier\":1,\"pos\":0}}}";
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                            + "Content-Length: " + json.length() + "\r\nConnection: close\r\n\r\n"
                            + json).getBytes());
                    socket.getOutputStream().flush();
                } catch (Exception ignored) {
                    return;
                }
            }
        });
        accepter.setDaemon(true);
        accepter.start();
        return server;
    }

    private McTiersProvider stallingProvider() {
        return new McTiersProvider(httpClient, Logger.getLogger("TierProviderAsyncTest"),
                new TierProvider.ProviderSettings(stallingUrl(), REQUEST_TIMEOUT, 60_000L, 0L, "FFAUtils/test"),
                executor);
    }

    // ------------------------------------------------------------------
    // The lookup itself
    // ------------------------------------------------------------------

    @Test
    void fetchProfile_returnsWithoutWaitingOnTheNetwork() {
        McTiersProvider provider = stallingProvider();

        long start = System.nanoTime();
        CompletableFuture<TierProfile> future = provider.fetchProfile(UUID.randomUUID());
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsed < NON_BLOCKING_MILLIS,
                "fetchProfile blocked the caller for " + elapsed + "ms; it must dispatch and return");
        assertFalse(future.isDone(), "the request should still be in flight");
    }

    @Test
    void fetchProfile_dispatchesOnTheDedicatedPoolNotTheCaller() throws Exception {
        // Records which thread the provider hands work to.
        AtomicReference<String> dispatchThread = new AtomicReference<>();
        CountDownLatch dispatched = new CountDownLatch(1);
        McTiersProvider provider = new McTiersProvider(httpClient,
                Logger.getLogger("TierProviderAsyncTest"),
                new TierProvider.ProviderSettings(stallingUrl(), REQUEST_TIMEOUT, 60_000L, 0L, "FFAUtils/test"),
                task -> executor.execute(() -> {
                    dispatchThread.compareAndSet(null, Thread.currentThread().getName());
                    dispatched.countDown();
                    task.run();
                }));

        String callerThread = Thread.currentThread().getName();
        provider.fetchProfile(UUID.randomUUID());

        assertTrue(dispatched.await(10, TimeUnit.SECONDS), "the request was never dispatched");
        assertNotEquals(callerThread, dispatchThread.get(),
                "the request was built and sent on the calling thread");
        assertTrue(dispatchThread.get().startsWith("test-tier-pool"),
                "expected the dedicated pool, got " + dispatchThread.get());
    }

    @Test
    void responseHandlingAndParsing_happenOffTheCallingThread() throws Exception {
        // A real response, so this covers the parse path rather than a timeout.
        try (ServerSocket server = respondingServer()) {
            McTiersProvider provider = new McTiersProvider(httpClient,
                    Logger.getLogger("TierProviderAsyncTest"),
                    new TierProvider.ProviderSettings("http://127.0.0.1:" + server.getLocalPort(),
                            REQUEST_TIMEOUT, 60_000L, 0L, "FFAUtils/test"),
                    executor);

            String callerThread = Thread.currentThread().getName();
            AtomicReference<String> handledOn = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);

            provider.fetchProfile(UUID.randomUUID()).whenComplete((profile, error) -> {
                handledOn.set(Thread.currentThread().getName());
                done.countDown();
            });

            assertTrue(done.await(30, TimeUnit.SECONDS), "the lookup never completed");
            assertNotEquals(callerThread, handledOn.get(),
                    "the response was parsed on the calling thread");
        }
    }

    @Test
    void manyLookups_doNotBlockTheCaller() {
        McTiersProvider provider = stallingProvider();

        long start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            provider.fetchProfile(UUID.randomUUID());
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        // Even with more lookups than pool threads, dispatch only queues them.
        assertTrue(elapsed < NON_BLOCKING_MILLIS,
                "20 lookups blocked the caller for " + elapsed + "ms");
    }

    @Test
    void prefetch_doesNotBlockTheCaller() {
        McTiersProvider provider = stallingProvider();

        long start = System.nanoTime();
        provider.prefetch(UUID.randomUUID(), "SomePlayer");
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsed < NON_BLOCKING_MILLIS, "prefetch blocked for " + elapsed + "ms");
    }

    @Test
    void warmUp_doesNotBlockTheCaller() {
        // warmUp runs during onEnable, on the main thread.
        EliteStormProvider provider = new EliteStormProvider(httpClient,
                Logger.getLogger("TierProviderAsyncTest"),
                new TierProvider.ProviderSettings(stallingUrl(), REQUEST_TIMEOUT, 60_000L, 0L, "FFAUtils/test"),
                executor, EliteStormProvider.DEFAULT_GUILD_ID, true);

        long start = System.nanoTime();
        provider.warmUp();
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsed < NON_BLOCKING_MILLIS, "warmUp blocked for " + elapsed + "ms");
        // The built-in vocabulary stays usable while the request is outstanding.
        assertEquals("vanilla", provider.getModeSlugs().get(8));
    }

    @Test
    void cacheReads_neverTouchTheNetwork() {
        McTiersProvider provider = stallingProvider();
        UUID uuid = UUID.randomUUID();

        long start = System.nanoTime();
        assertFalse(provider.isCached(uuid));
        assertNull(provider.getCachedProfile(uuid));
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsed < NON_BLOCKING_MILLIS, "a cache miss blocked for " + elapsed + "ms");
    }

    // ------------------------------------------------------------------
    // Teardown, which used to block on HttpClient.close()
    // ------------------------------------------------------------------

    @Test
    void managerShutdown_doesNotBlockOnInFlightRequests() throws Exception {
        TierManager manager = managerPointedAtStallingServer();
        // Leave a request outstanding against a server that never answers, and
        // wait until it is genuinely on the wire so the measurement is real.
        manager.getProvider(McTiersProvider.ID).fetchProfile(UUID.randomUUID());
        assertTrue(requestOnTheWire.await(10, TimeUnit.SECONDS), "the request never reached the server");

        long start = System.nanoTime();
        manager.shutdown();
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        // HttpClient.close() would wait here for the full request timeout,
        // freezing the server's main thread; shutdown() must not.
        assertTrue(elapsed < NON_BLOCKING_MILLIS,
                "shutdown blocked for " + elapsed + "ms with a request in flight");
    }

    @Test
    void managerReload_doesNotBlockOnInFlightRequests() throws Exception {
        TierManager manager = managerPointedAtStallingServer();
        manager.getProvider(McTiersProvider.ID).fetchProfile(UUID.randomUUID());
        assertTrue(requestOnTheWire.await(10, TimeUnit.SECONDS), "the request never reached the server");

        // loadSettings tears the old client down; /tiers reload runs it on the
        // main thread.
        long start = System.nanoTime();
        manager.loadSettings();
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsed < NON_BLOCKING_MILLIS,
                "reload blocked for " + elapsed + "ms with a request in flight");
        manager.shutdown();
    }

    private TierManager managerPointedAtStallingServer() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tiers.enabled", true);
        config.set("tiers.timeout-seconds", REQUEST_TIMEOUT.toSeconds());
        config.set("tiers.providers.mctiers.api-url", stallingUrl());
        config.set("tiers.providers.pvptiers.api-url", stallingUrl());
        config.set("tiers.providers.elitestorm.api-url", stallingUrl());

        JavaPlugin plugin = mock(JavaPlugin.class);
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("TierProviderAsyncTest"));
        lenient().when(plugin.getDescription())
                .thenReturn(new PluginDescriptionFile("FFAUtils", "1.0.0-TEST", "Main"));
        return new TierManager(plugin);
    }
}
