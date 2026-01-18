package sh.joey.mc.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DurationFormat")
class DurationFormatTest {

    @Nested
    @DisplayName("formatShort")
    class FormatShort {

        @Test
        @DisplayName("all units correct format")
        void formatShort_allUnits_correctFormat() {
            Duration duration = Duration.ofDays(2)
                    .plusHours(5)
                    .plusMinutes(30)
                    .plusSeconds(15);

            assertThat(DurationFormat.formatShort(duration)).isEqualTo("2d 5h 30m 15s");
        }

        @Test
        @DisplayName("only seconds shows seconds")
        void formatShort_onlySeconds_showsSeconds() {
            Duration duration = Duration.ofSeconds(45);

            assertThat(DurationFormat.formatShort(duration)).isEqualTo("45s");
        }

        @Test
        @DisplayName("only minutes shows minutes and seconds")
        void formatShort_onlyMinutes_showsMinutesAndSeconds() {
            Duration duration = Duration.ofMinutes(30);

            assertThat(DurationFormat.formatShort(duration)).isEqualTo("30m 0s");
        }

        @Test
        @DisplayName("zero duration shows zero seconds")
        void formatShort_zeroDuration_showsZeroSeconds() {
            assertThat(DurationFormat.formatShort(Duration.ZERO)).isEqualTo("0s");
        }

        @Test
        @DisplayName("negative duration shows zero seconds")
        void formatShort_negativeDuration_showsZeroSeconds() {
            assertThat(DurationFormat.formatShort(Duration.ofSeconds(-10))).isEqualTo("0s");
        }

        @Test
        @DisplayName("days and hours shows all intermediate units")
        void formatShort_daysAndHours_showsIntermediateUnits() {
            Duration duration = Duration.ofDays(1).plusHours(2);

            assertThat(DurationFormat.formatShort(duration)).isEqualTo("1d 2h 0m 0s");
        }
    }

    @Nested
    @DisplayName("formatCompact")
    class FormatCompact {

        @Test
        @DisplayName("no spaces in output")
        void formatCompact_noSpaces() {
            Duration duration = Duration.ofDays(2)
                    .plusHours(5)
                    .plusMinutes(30)
                    .plusSeconds(15);

            assertThat(DurationFormat.formatCompact(duration)).isEqualTo("2d5h30m15s");
        }

        @Test
        @DisplayName("only non-zero components")
        void formatCompact_onlyNonZeroComponents() {
            Duration duration = Duration.ofHours(5).plusSeconds(30);

            assertThat(DurationFormat.formatCompact(duration)).isEqualTo("5h30s");
        }

        @Test
        @DisplayName("zero duration shows zero seconds")
        void formatCompact_zeroDuration_showsZeroSeconds() {
            assertThat(DurationFormat.formatCompact(Duration.ZERO)).isEqualTo("0s");
        }

        @Test
        @DisplayName("negative duration shows zero seconds")
        void formatCompact_negativeDuration_showsZeroSeconds() {
            assertThat(DurationFormat.formatCompact(Duration.ofSeconds(-10))).isEqualTo("0s");
        }
    }

    @Nested
    @DisplayName("formatHumanReadable")
    class FormatHumanReadable {

        @Test
        @DisplayName("multiple units uses commas")
        void formatHumanReadable_multipleUnits_usesCommas() {
            Duration duration = Duration.ofDays(2)
                    .plusHours(5)
                    .plusMinutes(30);

            assertThat(DurationFormat.formatHumanReadable(duration))
                    .isEqualTo("2 days, 5 hours, 30 minutes");
        }

        @Test
        @DisplayName("singular units correct grammar")
        void formatHumanReadable_singularUnits_correctGrammar() {
            Duration duration = Duration.ofDays(1)
                    .plusHours(1)
                    .plusMinutes(1)
                    .plusSeconds(1);

            assertThat(DurationFormat.formatHumanReadable(duration))
                    .isEqualTo("1 day, 1 hour, 1 minute, 1 second");
        }

        @Test
        @DisplayName("zero duration shows now")
        void formatHumanReadable_zeroDuration_showsNow() {
            assertThat(DurationFormat.formatHumanReadable(Duration.ZERO)).isEqualTo("now");
        }

        @Test
        @DisplayName("negative duration shows now")
        void formatHumanReadable_negativeDuration_showsNow() {
            assertThat(DurationFormat.formatHumanReadable(Duration.ofSeconds(-10))).isEqualTo("now");
        }

        @Test
        @DisplayName("only seconds shows seconds")
        void formatHumanReadable_onlySeconds_showsSeconds() {
            Duration duration = Duration.ofSeconds(45);

            assertThat(DurationFormat.formatHumanReadable(duration)).isEqualTo("45 seconds");
        }

        @Test
        @DisplayName("skips zero intermediate components")
        void formatHumanReadable_skipsZeroComponents() {
            Duration duration = Duration.ofDays(1).plusSeconds(30);

            assertThat(DurationFormat.formatHumanReadable(duration)).isEqualTo("1 day, 30 seconds");
        }
    }
}
