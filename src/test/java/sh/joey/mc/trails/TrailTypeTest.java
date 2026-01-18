package sh.joey.mc.trails;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrailType")
class TrailTypeTest {

    @Test
    @DisplayName("fromId valid type returns type")
    void fromId_validType_returnsType() {
        assertThat(TrailType.fromId("elytra")).isEqualTo(TrailType.ELYTRA);
        assertThat(TrailType.fromId("ghast")).isEqualTo(TrailType.GHAST);
        assertThat(TrailType.fromId("walk")).isEqualTo(TrailType.WALK);
    }

    @Test
    @DisplayName("fromId case insensitive returns type")
    void fromId_caseInsensitive_returnsType() {
        assertThat(TrailType.fromId("ELYTRA")).isEqualTo(TrailType.ELYTRA);
        assertThat(TrailType.fromId("Elytra")).isEqualTo(TrailType.ELYTRA);
        assertThat(TrailType.fromId("GHAST")).isEqualTo(TrailType.GHAST);
    }

    @Test
    @DisplayName("fromId unknown returns null")
    void fromId_unknown_returnsNull() {
        assertThat(TrailType.fromId("unknown")).isNull();
        assertThat(TrailType.fromId("flame")).isNull();
    }

    @Test
    @DisplayName("fromId null returns null")
    void fromId_null_returnsNull() {
        assertThat(TrailType.fromId(null)).isNull();
    }

    @Test
    @DisplayName("id returns correct value")
    void id_returnsCorrectValue() {
        assertThat(TrailType.ELYTRA.id()).isEqualTo("elytra");
        assertThat(TrailType.GHAST.id()).isEqualTo("ghast");
        assertThat(TrailType.WALK.id()).isEqualTo("walk");
    }

    @Test
    @DisplayName("permission returns correct value")
    void permission_returnsCorrectValue() {
        assertThat(TrailType.ELYTRA.permission()).isEqualTo("smp.trails.elytra");
        assertThat(TrailType.GHAST.permission()).isEqualTo("smp.trails.ghast");
        assertThat(TrailType.WALK.permission()).isEqualTo("smp.trails.walk");
    }
}
