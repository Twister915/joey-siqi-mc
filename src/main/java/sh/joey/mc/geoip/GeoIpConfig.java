package sh.joey.mc.geoip;

import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Configuration for GeoIP functionality.
 *
 * @param databasePath Path to the GeoLite2-City.mmdb file (relative to plugin folder)
 * @param enabled      Whether GeoIP lookups are enabled
 */
public record GeoIpConfig(
        String databasePath,
        boolean enabled
) {
    public static GeoIpConfig load(SiqiJoeyPlugin plugin) {
        var config = plugin.getConfig();
        return new GeoIpConfig(
                config.getString("geoip.database-path", "GeoLite2-City.mmdb"),
                config.getBoolean("geoip.enabled", true)
        );
    }
}
