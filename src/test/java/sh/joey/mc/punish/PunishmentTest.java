package sh.joey.mc.punish;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Punishment")
class PunishmentTest {

    private static final UUID PUNISHMENT_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID ISSUER_ID = UUID.randomUUID();

    private Punishment createPunishment(Instant expiresAt, Instant revokedAt) {
        return new Punishment(
                PUNISHMENT_ID,
                PLAYER_ID,
                null,
                PunishmentType.BAN,
                ISSUER_ID,
                "Test reason",
                expiresAt,
                Instant.now(),
                revokedAt,
                null
        );
    }

    @Nested
    @DisplayName("isActive")
    class IsActive {

        @Test
        @DisplayName("not revoked not expired returns true")
        void isActive_notRevokedNotExpired_returnsTrue() {
            Punishment punishment = createPunishment(
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    null
            );

            assertThat(punishment.isActive()).isTrue();
        }

        @Test
        @DisplayName("permanent not revoked returns true")
        void isActive_permanentNotRevoked_returnsTrue() {
            Punishment punishment = createPunishment(null, null);

            assertThat(punishment.isActive()).isTrue();
        }

        @Test
        @DisplayName("revoked returns false")
        void isActive_revoked_returnsFalse() {
            Punishment punishment = createPunishment(
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    Instant.now()
            );

            assertThat(punishment.isActive()).isFalse();
        }

        @Test
        @DisplayName("expired returns false")
        void isActive_expired_returnsFalse() {
            Punishment punishment = createPunishment(
                    Instant.now().minus(1, ChronoUnit.DAYS),
                    null
            );

            assertThat(punishment.isActive()).isFalse();
        }

        @Test
        @DisplayName("revoked and expired returns false")
        void isActive_revokedAndExpired_returnsFalse() {
            Punishment punishment = createPunishment(
                    Instant.now().minus(1, ChronoUnit.DAYS),
                    Instant.now().minus(2, ChronoUnit.DAYS)
            );

            assertThat(punishment.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("isPermanent")
    class IsPermanent {

        @Test
        @DisplayName("null expiry returns true")
        void isPermanent_nullExpiry_returnsTrue() {
            Punishment punishment = createPunishment(null, null);

            assertThat(punishment.isPermanent()).isTrue();
        }

        @Test
        @DisplayName("future expiry returns false")
        void isPermanent_futureExpiry_returnsFalse() {
            Punishment punishment = createPunishment(
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    null
            );

            assertThat(punishment.isPermanent()).isFalse();
        }

        @Test
        @DisplayName("past expiry returns false")
        void isPermanent_pastExpiry_returnsFalse() {
            Punishment punishment = createPunishment(
                    Instant.now().minus(1, ChronoUnit.DAYS),
                    null
            );

            assertThat(punishment.isPermanent()).isFalse();
        }
    }

    @Nested
    @DisplayName("isExpired")
    class IsExpired {

        @Test
        @DisplayName("past expiry returns true")
        void isExpired_pastExpiry_returnsTrue() {
            Punishment punishment = createPunishment(
                    Instant.now().minus(1, ChronoUnit.DAYS),
                    null
            );

            assertThat(punishment.isExpired()).isTrue();
        }

        @Test
        @DisplayName("future expiry returns false")
        void isExpired_futureExpiry_returnsFalse() {
            Punishment punishment = createPunishment(
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    null
            );

            assertThat(punishment.isExpired()).isFalse();
        }

        @Test
        @DisplayName("null expiry returns false")
        void isExpired_nullExpiry_returnsFalse() {
            Punishment punishment = createPunishment(null, null);

            assertThat(punishment.isExpired()).isFalse();
        }
    }

    @Nested
    @DisplayName("isRevoked")
    class IsRevoked {

        @Test
        @DisplayName("has revokedAt returns true")
        void isRevoked_hasRevokedAt_returnsTrue() {
            Punishment punishment = createPunishment(
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    Instant.now()
            );

            assertThat(punishment.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("null revokedAt returns false")
        void isRevoked_nullRevokedAt_returnsFalse() {
            Punishment punishment = createPunishment(
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    null
            );

            assertThat(punishment.isRevoked()).isFalse();
        }
    }
}
