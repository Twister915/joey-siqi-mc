package sh.joey.mc.geoip;

import java.util.ArrayList;

/**
 * Immutable record representing geographic location data from GeoIP lookup.
 *
 * @param country      Country name (e.g., "United States")
 * @param countryCode  ISO country code (e.g., "US")
 * @param subdivision  State/province name (nullable)
 * @param city         City name (nullable)
 * @param postalCode   Postal/ZIP code (nullable)
 * @param latitude     Latitude coordinate (nullable)
 * @param longitude    Longitude coordinate (nullable)
 */
public record GeoLocation(
        String country,
        String countryCode,
        String subdivision,
        String city,
        String postalCode,
        Double latitude,
        Double longitude
) {
    /**
     * Returns a formatted location string like "San Francisco, California, US".
     * Handles null fields gracefully.
     */
    public String formatted() {
        var parts = new ArrayList<String>();
        if (city != null) parts.add(city);
        if (subdivision != null) parts.add(subdivision);
        if (countryCode != null) parts.add(countryCode);
        return parts.isEmpty() ? "Unknown" : String.join(", ", parts);
    }

    /**
     * Returns a short format showing just country code, or city + country if available.
     * Example: "US" or "San Francisco, US"
     */
    public String shortFormatted() {
        if (city != null && countryCode != null) {
            return city + ", " + countryCode;
        }
        if (countryCode != null) {
            return countryCode;
        }
        return "Unknown";
    }
}
