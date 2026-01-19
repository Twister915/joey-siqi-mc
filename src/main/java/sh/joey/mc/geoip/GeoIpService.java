package sh.joey.mc.geoip;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import sh.joey.mc.SiqiJoeyPlugin;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service for GeoIP lookups using MaxMind GeoLite2-City database.
 * <p>
 * The DatabaseReader is thread-safe and reused for all lookups.
 * Lookups are performed on the IO scheduler to avoid blocking the main thread.
 */
public final class GeoIpService implements Disposable {

    private final SiqiJoeyPlugin plugin;
    private final DatabaseReader reader;
    private volatile boolean disposed = false;

    public GeoIpService(SiqiJoeyPlugin plugin, GeoIpConfig config) throws IOException {
        this.plugin = plugin;

        Path dbPath = plugin.getDataFolder().toPath().resolve(config.databasePath());

        if (!Files.exists(dbPath)) {
            throw new IOException("GeoIP database not found: " + dbPath);
        }

        this.reader = new DatabaseReader.Builder(dbPath.toFile())
                .withCache(new CHMCache())
                .build();

        plugin.getLogger().info("GeoIP database loaded: " + dbPath);
    }

    /**
     * Look up location for an IP address.
     * Returns Maybe.empty() if IP is not found, is private/local, or lookup fails.
     *
     * @param ipAddress The IP address string to look up
     * @return Maybe containing GeoLocation, or empty if not found
     */
    public Maybe<GeoLocation> lookup(String ipAddress) {
        return Maybe.<GeoLocation>fromCallable(() -> {
            try {
                InetAddress addr = InetAddress.getByName(ipAddress);

                // Skip private/local addresses
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() ||
                        addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                    return null;
                }

                var response = reader.city(addr);

                return new GeoLocation(
                        response.country().name(),
                        response.country().isoCode(),
                        response.mostSpecificSubdivision().name(),
                        response.city().name(),
                        response.postal().code(),
                        response.location().latitude(),
                        response.location().longitude()
                );
            } catch (AddressNotFoundException e) {
                // IP not in database (common for some ranges)
                return null;
            } catch (GeoIp2Exception e) {
                plugin.getLogger().warning("GeoIP lookup failed for " + ipAddress + ": " + e.getMessage());
                return null;
            }
        }).subscribeOn(Schedulers.io());
    }

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            try {
                reader.close();
                plugin.getLogger().info("GeoIP database closed");
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to close GeoIP reader: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
