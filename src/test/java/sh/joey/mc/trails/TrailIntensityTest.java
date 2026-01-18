package sh.joey.mc.trails;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrailIntensity")
class TrailIntensityTest {

    @Test
    @DisplayName("fromId valid intensity returns intensity")
    void fromId_validIntensity_returnsIntensity() {
        assertThat(TrailIntensity.fromId("low")).isEqualTo(TrailIntensity.LOW);
        assertThat(TrailIntensity.fromId("medium")).isEqualTo(TrailIntensity.MEDIUM);
        assertThat(TrailIntensity.fromId("high")).isEqualTo(TrailIntensity.HIGH);
    }

    @Test
    @DisplayName("fromId case insensitive returns intensity")
    void fromId_caseInsensitive_returnsIntensity() {
        assertThat(TrailIntensity.fromId("LOW")).isEqualTo(TrailIntensity.LOW);
        assertThat(TrailIntensity.fromId("Low")).isEqualTo(TrailIntensity.LOW);
        assertThat(TrailIntensity.fromId("MEDIUM")).isEqualTo(TrailIntensity.MEDIUM);
        assertThat(TrailIntensity.fromId("HIGH")).isEqualTo(TrailIntensity.HIGH);
    }

    @Test
    @DisplayName("fromId unknown returns null")
    void fromId_unknown_returnsNull() {
        assertThat(TrailIntensity.fromId("unknown")).isNull();
        assertThat(TrailIntensity.fromId("max")).isNull();
    }

    @Test
    @DisplayName("fromId null returns null")
    void fromId_null_returnsNull() {
        assertThat(TrailIntensity.fromId(null)).isNull();
    }

    @Test
    @DisplayName("defaultIntensity returns medium")
    void defaultIntensity_returnsMedium() {
        assertThat(TrailIntensity.defaultIntensity()).isEqualTo(TrailIntensity.MEDIUM);
    }

    @Test
    @DisplayName("id returns correct value")
    void id_returnsCorrectValue() {
        assertThat(TrailIntensity.LOW.id()).isEqualTo("low");
        assertThat(TrailIntensity.MEDIUM.id()).isEqualTo("medium");
        assertThat(TrailIntensity.HIGH.id()).isEqualTo("high");
    }

    @Test
    @DisplayName("particleCount returns correct value")
    void particleCount_returnsCorrectValue() {
        assertThat(TrailIntensity.LOW.particleCount()).isEqualTo(2);
        assertThat(TrailIntensity.MEDIUM.particleCount()).isEqualTo(4);
        assertThat(TrailIntensity.HIGH.particleCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("tickInterval returns correct value")
    void tickInterval_returnsCorrectValue() {
        assertThat(TrailIntensity.LOW.tickInterval()).isEqualTo(5);
        assertThat(TrailIntensity.MEDIUM.tickInterval()).isEqualTo(3);
        assertThat(TrailIntensity.HIGH.tickInterval()).isEqualTo(2);
    }
}
