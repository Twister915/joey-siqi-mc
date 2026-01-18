package sh.joey.mc.home;

import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("HomeLimitResolver")
class HomeLimitResolverTest {

    @Nested
    @DisplayName("resolve")
    class ResolveTests {

        @Test
        @DisplayName("no limits configured returns unlimited")
        void noLimitsConfigured_returnsUnlimited() {
            Permissible permissible = mock(Permissible.class);
            HomeLimitConfig config = new HomeLimitConfig(Map.of());

            OptionalInt limit = HomeLimitResolver.resolve(permissible, config);

            assertThat(limit).isEmpty();
        }

        @Test
        @DisplayName("permissible with no matching permissions returns unlimited (backwards compat)")
        void noMatchingPermissions_returnsUnlimited() {
            Permissible permissible = mock(Permissible.class);
            when(permissible.hasPermission("smp.home.player")).thenReturn(false);
            when(permissible.hasPermission("smp.home.donor")).thenReturn(false);

            HomeLimitConfig config = new HomeLimitConfig(Map.of(
                    "player", OptionalInt.of(5),
                    "donor", OptionalInt.of(12)
            ));

            OptionalInt limit = HomeLimitResolver.resolve(permissible, config);

            assertThat(limit).isEmpty();
        }

        @Test
        @DisplayName("permissible with single numeric permission returns that limit")
        void singleNumericPermission_returnsThatLimit() {
            Permissible permissible = mock(Permissible.class);
            when(permissible.hasPermission("smp.home.player")).thenReturn(true);
            when(permissible.hasPermission("smp.home.donor")).thenReturn(false);

            HomeLimitConfig config = new HomeLimitConfig(Map.of(
                    "player", OptionalInt.of(5),
                    "donor", OptionalInt.of(12)
            ));

            OptionalInt limit = HomeLimitResolver.resolve(permissible, config);

            assertThat(limit).hasValue(5);
        }

        @Test
        @DisplayName("permissible with multiple numeric permissions returns highest")
        void multipleNumericPermissions_returnsHighest() {
            Permissible permissible = mock(Permissible.class);
            when(permissible.hasPermission("smp.home.player")).thenReturn(true);
            when(permissible.hasPermission("smp.home.donor")).thenReturn(true);
            when(permissible.hasPermission("smp.home.vip")).thenReturn(false);

            HomeLimitConfig config = new HomeLimitConfig(Map.of(
                    "player", OptionalInt.of(5),
                    "donor", OptionalInt.of(12),
                    "vip", OptionalInt.of(20)
            ));

            OptionalInt limit = HomeLimitResolver.resolve(permissible, config);

            assertThat(limit).hasValue(12);
        }

        @Test
        @DisplayName("permissible with unlimited permission returns unlimited")
        void unlimitedPermission_returnsUnlimited() {
            Permissible permissible = mock(Permissible.class);
            when(permissible.hasPermission("smp.home.player")).thenReturn(true);
            when(permissible.hasPermission("smp.home.vip")).thenReturn(true);

            HomeLimitConfig config = new HomeLimitConfig(Map.of(
                    "player", OptionalInt.of(5),
                    "vip", OptionalInt.empty() // unlimited
            ));

            OptionalInt limit = HomeLimitResolver.resolve(permissible, config);

            assertThat(limit).isEmpty();
        }

        @Test
        @DisplayName("unlimited permission wins over numeric permissions")
        void unlimitedWinsOverNumeric() {
            Permissible permissible = mock(Permissible.class);
            when(permissible.hasPermission("smp.home.player")).thenReturn(true);
            when(permissible.hasPermission("smp.home.donor")).thenReturn(true);
            when(permissible.hasPermission("smp.home.vip")).thenReturn(true);

            HomeLimitConfig config = new HomeLimitConfig(Map.of(
                    "player", OptionalInt.of(5),
                    "donor", OptionalInt.of(100),
                    "vip", OptionalInt.empty() // unlimited
            ));

            OptionalInt limit = HomeLimitResolver.resolve(permissible, config);

            assertThat(limit).isEmpty();
        }

        @Test
        @DisplayName("zero limit is valid")
        void zeroLimit_isValid() {
            Permissible permissible = mock(Permissible.class);
            when(permissible.hasPermission("smp.home.banned")).thenReturn(true);

            HomeLimitConfig config = new HomeLimitConfig(Map.of(
                    "banned", OptionalInt.of(0)
            ));

            OptionalInt limit = HomeLimitResolver.resolve(permissible, config);

            assertThat(limit).hasValue(0);
        }
    }

    @Nested
    @DisplayName("canCreateHome")
    class CanCreateHomeTests {

        @Test
        @DisplayName("returns true when unlimited")
        void unlimited_returnsTrue() {
            Permissible permissible = mock(Permissible.class);
            HomeLimitConfig config = new HomeLimitConfig(Map.of());

            boolean canCreate = HomeLimitResolver.canCreateHome(permissible, config, 100);

            assertThat(canCreate).isTrue();
        }

        @Test
        @DisplayName("returns true when under limit")
        void underLimit_returnsTrue() {
            Permissible permissible = mock(Permissible.class);
            when(permissible.hasPermission("smp.home.player")).thenReturn(true);

            HomeLimitConfig config = new HomeLimitConfig(Map.of(
                    "player", OptionalInt.of(5)
            ));

            boolean canCreate = HomeLimitResolver.canCreateHome(permissible, config, 3);

            assertThat(canCreate).isTrue();
        }

        @Test
        @DisplayName("returns true when one below limit")
        void oneBelowLimit_returnsTrue() {
            Permissible permissible = mock(Permissible.class);
            when(permissible.hasPermission("smp.home.player")).thenReturn(true);

            HomeLimitConfig config = new HomeLimitConfig(Map.of(
                    "player", OptionalInt.of(5)
            ));

            boolean canCreate = HomeLimitResolver.canCreateHome(permissible, config, 4);

            assertThat(canCreate).isTrue();
        }

        @Test
        @DisplayName("returns false when at limit")
        void atLimit_returnsFalse() {
            Permissible permissible = mock(Permissible.class);
            when(permissible.hasPermission("smp.home.player")).thenReturn(true);

            HomeLimitConfig config = new HomeLimitConfig(Map.of(
                    "player", OptionalInt.of(5)
            ));

            boolean canCreate = HomeLimitResolver.canCreateHome(permissible, config, 5);

            assertThat(canCreate).isFalse();
        }

        @Test
        @DisplayName("returns false when over limit")
        void overLimit_returnsFalse() {
            Permissible permissible = mock(Permissible.class);
            when(permissible.hasPermission("smp.home.player")).thenReturn(true);

            HomeLimitConfig config = new HomeLimitConfig(Map.of(
                    "player", OptionalInt.of(5)
            ));

            boolean canCreate = HomeLimitResolver.canCreateHome(permissible, config, 10);

            assertThat(canCreate).isFalse();
        }

        @Test
        @DisplayName("zero limit blocks all creation")
        void zeroLimit_blockAllCreation() {
            Permissible permissible = mock(Permissible.class);
            when(permissible.hasPermission("smp.home.banned")).thenReturn(true);

            HomeLimitConfig config = new HomeLimitConfig(Map.of(
                    "banned", OptionalInt.of(0)
            ));

            boolean canCreate = HomeLimitResolver.canCreateHome(permissible, config, 0);

            assertThat(canCreate).isFalse();
        }
    }
}
