package sh.joey.mc.punish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for PunishmentStorage.
 * Tests custom enum types, NULL handling, and active/expired/revoked states.
 */
class PunishmentStorageIntegrationTest extends PostgresIntegrationTest {

    private PunishmentStorage punishmentStorage;

    @BeforeEach
    void setUpStorage() {
        punishmentStorage = new PunishmentStorage(storage);
    }

    @Nested
    @DisplayName("Ban Operations")
    class BanTests {

        @Test
        @DisplayName("Create permanent ban")
        void createPermanentBan() throws SQLException {
            UUID targetId = UUID.randomUUID();
            UUID issuerId = UUID.randomUUID();

            blockingAwait(punishmentStorage.createBan(targetId, issuerId, "Griefing", null));

            Optional<Punishment> ban = blockingGet(punishmentStorage.getActiveBan(targetId));
            assertThat(ban).isPresent();
            assertThat(ban.get().targetPlayerId()).isEqualTo(targetId);
            assertThat(ban.get().issuedByPlayerId()).isEqualTo(issuerId);
            assertThat(ban.get().reason()).isEqualTo("Griefing");
            assertThat(ban.get().expiresAt()).isNull();
            assertThat(ban.get().isPermanent()).isTrue();
            assertThat(ban.get().type()).isEqualTo(PunishmentType.BAN);
        }

        @Test
        @DisplayName("Create temporary ban")
        void createTemporaryBan() {
            UUID targetId = UUID.randomUUID();
            Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

            blockingAwait(punishmentStorage.createBan(targetId, null, "Timeout", expiresAt));

            Optional<Punishment> ban = blockingGet(punishmentStorage.getActiveBan(targetId));
            assertThat(ban).isPresent();
            assertThat(ban.get().isPermanent()).isFalse();
            assertThat(ban.get().expiresAt()).isNotNull();
        }

        @Test
        @DisplayName("Get active ban returns empty when no ban")
        void getActiveBan_noBan_returnsEmpty() {
            UUID targetId = UUID.randomUUID();

            Optional<Punishment> ban = blockingGet(punishmentStorage.getActiveBan(targetId));

            assertThat(ban).isEmpty();
        }

        @Test
        @DisplayName("Get active ban returns empty when ban expired")
        void getActiveBan_expired_returnsEmpty() {
            UUID targetId = UUID.randomUUID();
            Instant expiredAt = Instant.now().minus(1, ChronoUnit.HOURS);

            blockingAwait(punishmentStorage.createBan(targetId, null, "Expired ban", expiredAt));

            Optional<Punishment> ban = blockingGet(punishmentStorage.getActiveBan(targetId));

            assertThat(ban).isEmpty();
        }

        @Test
        @DisplayName("Revoke ban makes ban inactive")
        void revokeBan() {
            UUID targetId = UUID.randomUUID();
            UUID revokedById = UUID.randomUUID();

            blockingAwait(punishmentStorage.createBan(targetId, null, "Ban to revoke", null));

            int revoked = blockingGet(punishmentStorage.revokeBans(targetId, revokedById));

            assertThat(revoked).isEqualTo(1);
            assertThat(blockingGet(punishmentStorage.getActiveBan(targetId))).isEmpty();
        }

        @Test
        @DisplayName("Revoke ban when no ban returns zero")
        void revokeBan_noBan_returnsZero() {
            UUID targetId = UUID.randomUUID();

            int revoked = blockingGet(punishmentStorage.revokeBans(targetId, null));

            assertThat(revoked).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("IP Ban Operations")
    class IpBanTests {

        @Test
        @DisplayName("Create IP ban")
        void createIpBan() {
            String ip = "192.168.1.100";
            UUID associatedPlayer = UUID.randomUUID();
            UUID issuerId = UUID.randomUUID();

            blockingAwait(punishmentStorage.createIpBan(ip, associatedPlayer, issuerId, "VPN detected"));

            Optional<Punishment> ipBan = blockingGet(punishmentStorage.getActiveIpBan(ip));
            assertThat(ipBan).isPresent();
            assertThat(ipBan.get().targetIp()).isEqualTo(ip);
            assertThat(ipBan.get().targetPlayerId()).isEqualTo(associatedPlayer);
            assertThat(ipBan.get().reason()).isEqualTo("VPN detected");
            assertThat(ipBan.get().type()).isEqualTo(PunishmentType.IP_BAN);
        }

        @Test
        @DisplayName("Create IP ban without associated player")
        void createIpBan_noAssociatedPlayer() {
            String ip = "10.0.0.50";

            blockingAwait(punishmentStorage.createIpBan(ip, null, null, "Suspicious IP"));

            Optional<Punishment> ipBan = blockingGet(punishmentStorage.getActiveIpBan(ip));
            assertThat(ipBan).isPresent();
            assertThat(ipBan.get().targetPlayerId()).isNull();
        }

        @Test
        @DisplayName("Revoke IP bans by IP address")
        void revokeIpBans() {
            String ip = "172.16.0.1";

            blockingAwait(punishmentStorage.createIpBan(ip, null, null, "Ban 1"));

            int revoked = blockingGet(punishmentStorage.revokeIpBans(ip, null));

            assertThat(revoked).isEqualTo(1);
            assertThat(blockingGet(punishmentStorage.getActiveIpBan(ip))).isEmpty();
        }

        @Test
        @DisplayName("Revoke IP bans by player ID")
        void revokeIpBansByPlayer() {
            UUID playerId = UUID.randomUUID();
            String ip = "192.168.1.50";

            blockingAwait(punishmentStorage.createIpBan(ip, playerId, null, "Associated IP ban"));

            int revoked = blockingGet(punishmentStorage.revokeIpBansByPlayer(playerId, null));

            assertThat(revoked).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Mute Operations")
    class MuteTests {

        @Test
        @DisplayName("Create permanent mute")
        void createPermanentMute() {
            UUID targetId = UUID.randomUUID();

            blockingAwait(punishmentStorage.createMute(targetId, null, "Spam", null));

            Optional<Punishment> mute = blockingGet(punishmentStorage.getActiveMute(targetId));
            assertThat(mute).isPresent();
            assertThat(mute.get().type()).isEqualTo(PunishmentType.MUTE);
            assertThat(mute.get().isPermanent()).isTrue();
        }

        @Test
        @DisplayName("Create temporary mute")
        void createTemporaryMute() {
            UUID targetId = UUID.randomUUID();
            Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

            blockingAwait(punishmentStorage.createMute(targetId, null, "Timeout", expiresAt));

            Optional<Punishment> mute = blockingGet(punishmentStorage.getActiveMute(targetId));
            assertThat(mute).isPresent();
            assertThat(mute.get().isPermanent()).isFalse();
        }

        @Test
        @DisplayName("Get active mute returns empty when expired")
        void getActiveMute_expired_returnsEmpty() {
            UUID targetId = UUID.randomUUID();
            Instant expiredAt = Instant.now().minus(1, ChronoUnit.MINUTES);

            blockingAwait(punishmentStorage.createMute(targetId, null, "Expired", expiredAt));

            Optional<Punishment> mute = blockingGet(punishmentStorage.getActiveMute(targetId));

            assertThat(mute).isEmpty();
        }

        @Test
        @DisplayName("Revoke mutes")
        void revokeMutes() {
            UUID targetId = UUID.randomUUID();

            blockingAwait(punishmentStorage.createMute(targetId, null, "To revoke", null));

            int revoked = blockingGet(punishmentStorage.revokeMutes(targetId, null));

            assertThat(revoked).isEqualTo(1);
            assertThat(blockingGet(punishmentStorage.getActiveMute(targetId))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Kick Operations")
    class KickTests {

        @Test
        @DisplayName("Create kick record")
        void createKick() throws SQLException {
            UUID targetId = UUID.randomUUID();
            UUID issuerId = UUID.randomUUID();

            blockingAwait(punishmentStorage.createKick(targetId, issuerId, "AFK too long"));

            int count = countRows("SELECT COUNT(*) FROM punishments WHERE target_player_id = '" + targetId + "' AND type = 'KICK'");
            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Warning Operations")
    class WarningTests {

        @Test
        @DisplayName("Create warning")
        void createWarning() throws SQLException {
            UUID targetId = UUID.randomUUID();
            UUID issuerId = UUID.randomUUID();

            blockingAwait(punishmentStorage.createWarning(targetId, issuerId, "First warning"));

            int count = countRows("SELECT COUNT(*) FROM punishments WHERE target_player_id = '" + targetId + "' AND type = 'WARN'");
            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Punishment History")
    class HistoryTests {

        @Test
        @DisplayName("Get punishment history returns all types")
        void getPunishmentHistory_returnsAllTypes() {
            UUID targetId = UUID.randomUUID();

            blockingAwait(punishmentStorage.createBan(targetId, null, "Ban", null));
            blockingAwait(punishmentStorage.createMute(targetId, null, "Mute", null));
            blockingAwait(punishmentStorage.createKick(targetId, null, "Kick"));
            blockingAwait(punishmentStorage.createWarning(targetId, null, "Warn"));

            List<Punishment> history = blockingList(punishmentStorage.getPunishmentHistory(targetId));

            assertThat(history).hasSize(4);
            assertThat(history).extracting(Punishment::type)
                    .containsExactlyInAnyOrder(PunishmentType.BAN, PunishmentType.MUTE, PunishmentType.KICK, PunishmentType.WARN);
        }

        @Test
        @DisplayName("Get punishment history returns empty when no history")
        void getPunishmentHistory_noHistory_returnsEmpty() {
            UUID targetId = UUID.randomUUID();

            List<Punishment> history = blockingList(punishmentStorage.getPunishmentHistory(targetId));

            assertThat(history).isEmpty();
        }

        @Test
        @DisplayName("Punishment history is ordered by created_at descending")
        void punishmentHistory_orderedDescending() throws InterruptedException {
            UUID targetId = UUID.randomUUID();

            blockingAwait(punishmentStorage.createWarning(targetId, null, "First"));
            Thread.sleep(10);
            blockingAwait(punishmentStorage.createWarning(targetId, null, "Second"));
            Thread.sleep(10);
            blockingAwait(punishmentStorage.createWarning(targetId, null, "Third"));

            List<Punishment> history = blockingList(punishmentStorage.getPunishmentHistory(targetId));

            assertThat(history).hasSize(3);
            assertThat(history.get(0).reason()).isEqualTo("Third");
            assertThat(history.get(1).reason()).isEqualTo("Second");
            assertThat(history.get(2).reason()).isEqualTo("First");
        }

        @Test
        @DisplayName("Get all punishments respects limit")
        void getAllPunishments_respectsLimit() {
            for (int i = 0; i < 10; i++) {
                blockingAwait(punishmentStorage.createWarning(UUID.randomUUID(), null, "Warning " + i));
            }

            List<Punishment> punishments = blockingList(punishmentStorage.getAllPunishments(5));

            assertThat(punishments).hasSize(5);
        }
    }

    @Nested
    @DisplayName("NULL Handling")
    class NullHandlingTests {

        @Test
        @DisplayName("NULL issuer is handled correctly")
        void nullIssuer_handledCorrectly() {
            UUID targetId = UUID.randomUUID();

            blockingAwait(punishmentStorage.createBan(targetId, null, "Console ban", null));

            Optional<Punishment> ban = blockingGet(punishmentStorage.getActiveBan(targetId));
            assertThat(ban).isPresent();
            assertThat(ban.get().issuedByPlayerId()).isNull();
        }

        @Test
        @DisplayName("NULL reason is handled correctly")
        void nullReason_handledCorrectly() {
            UUID targetId = UUID.randomUUID();

            blockingAwait(punishmentStorage.createBan(targetId, null, null, null));

            Optional<Punishment> ban = blockingGet(punishmentStorage.getActiveBan(targetId));
            assertThat(ban).isPresent();
            assertThat(ban.get().reason()).isNull();
        }

        @Test
        @DisplayName("NULL expiry means permanent")
        void nullExpiry_meansPermanent() {
            UUID targetId = UUID.randomUUID();

            blockingAwait(punishmentStorage.createBan(targetId, null, "Permanent", null));

            Optional<Punishment> ban = blockingGet(punishmentStorage.getActiveBan(targetId));
            assertThat(ban).isPresent();
            assertThat(ban.get().expiresAt()).isNull();
            assertThat(ban.get().isPermanent()).isTrue();
        }
    }

    @Nested
    @DisplayName("Punishment States")
    class PunishmentStateTests {

        @Test
        @DisplayName("Active punishment isActive returns true")
        void activePunishment_isActive_returnsTrue() {
            UUID targetId = UUID.randomUUID();

            blockingAwait(punishmentStorage.createBan(targetId, null, "Active", null));

            Optional<Punishment> ban = blockingGet(punishmentStorage.getActiveBan(targetId));
            assertThat(ban).isPresent();
            assertThat(ban.get().isActive()).isTrue();
            assertThat(ban.get().isRevoked()).isFalse();
            assertThat(ban.get().isExpired()).isFalse();
        }

        @Test
        @DisplayName("Revoked punishment has revokedAt set")
        void revokedPunishment_hasRevokedAt() {
            UUID targetId = UUID.randomUUID();
            UUID revokedById = UUID.randomUUID();

            blockingAwait(punishmentStorage.createBan(targetId, null, "To revoke", null));
            blockingGet(punishmentStorage.revokeBans(targetId, revokedById));

            List<Punishment> history = blockingList(punishmentStorage.getPunishmentHistory(targetId));
            assertThat(history).hasSize(1);
            assertThat(history.get(0).revokedAt()).isNotNull();
            assertThat(history.get(0).revokedByPlayerId()).isEqualTo(revokedById);
            assertThat(history.get(0).isRevoked()).isTrue();
        }
    }

    @Test
    @DisplayName("Different players have separate punishments")
    void differentPlayers_separatePunishments() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        blockingAwait(punishmentStorage.createBan(player1, null, "Player 1 ban", null));
        blockingAwait(punishmentStorage.createMute(player2, null, "Player 2 mute", null));

        assertThat(blockingGet(punishmentStorage.getActiveBan(player1))).isPresent();
        assertThat(blockingGet(punishmentStorage.getActiveMute(player1))).isEmpty();

        assertThat(blockingGet(punishmentStorage.getActiveBan(player2))).isEmpty();
        assertThat(blockingGet(punishmentStorage.getActiveMute(player2))).isPresent();
    }
}
