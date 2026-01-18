package sh.joey.mc.punish;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DurationParser")
class DurationParserTest {

    @Nested
    @DisplayName("Valid Formats")
    class ValidFormats {

        @Test
        @DisplayName("parse days returns duration")
        void parse_days_returnsDuration() {
            Optional<Duration> result = DurationParser.parse("2d");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(Duration.ofDays(2));
        }

        @Test
        @DisplayName("parse hours returns duration")
        void parse_hours_returnsDuration() {
            Optional<Duration> result = DurationParser.parse("5h");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(Duration.ofHours(5));
        }

        @Test
        @DisplayName("parse minutes returns duration")
        void parse_minutes_returnsDuration() {
            Optional<Duration> result = DurationParser.parse("30m");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("parse seconds returns duration")
        void parse_seconds_returnsDuration() {
            Optional<Duration> result = DurationParser.parse("15s");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(Duration.ofSeconds(15));
        }

        @Test
        @DisplayName("parse combined returns duration")
        void parse_combined_returnsDuration() {
            Optional<Duration> result = DurationParser.parse("2d5h30m15s");

            assertThat(result).isPresent();
            Duration expected = Duration.ofDays(2)
                    .plusHours(5)
                    .plusMinutes(30)
                    .plusSeconds(15);
            assertThat(result.get()).isEqualTo(expected);
        }

        @Test
        @DisplayName("parse partial combined returns duration")
        void parse_partialCombined_returnsDuration() {
            Optional<Duration> result = DurationParser.parse("1d12h");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(Duration.ofDays(1).plusHours(12));
        }

        @Test
        @DisplayName("parse uppercase returns duration")
        void parse_uppercase_returnsDuration() {
            Optional<Duration> result = DurationParser.parse("2D5H");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(Duration.ofDays(2).plusHours(5));
        }

        @Test
        @DisplayName("parse large values returns duration")
        void parse_largeValues_returnsDuration() {
            Optional<Duration> result = DurationParser.parse("365d");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(Duration.ofDays(365));
        }
    }

    @Nested
    @DisplayName("Invalid Formats")
    class InvalidFormats {

        @Test
        @DisplayName("parse empty string returns empty")
        void parse_emptyString_returnsEmpty() {
            assertThat(DurationParser.parse("")).isEmpty();
        }

        @Test
        @DisplayName("parse null returns empty")
        void parse_null_returnsEmpty() {
            assertThat(DurationParser.parse(null)).isEmpty();
        }

        @Test
        @DisplayName("parse blank string returns empty")
        void parse_blankString_returnsEmpty() {
            assertThat(DurationParser.parse("   ")).isEmpty();
        }

        @Test
        @DisplayName("parse no unit returns empty")
        void parse_noUnit_returnsEmpty() {
            assertThat(DurationParser.parse("30")).isEmpty();
        }

        @Test
        @DisplayName("parse invalid unit returns empty")
        void parse_invalidUnit_returnsEmpty() {
            assertThat(DurationParser.parse("30x")).isEmpty();
        }

        @Test
        @DisplayName("parse unit without number returns empty")
        void parse_unitWithoutNumber_returnsEmpty() {
            assertThat(DurationParser.parse("d")).isEmpty();
            assertThat(DurationParser.parse("h")).isEmpty();
        }

        @Test
        @DisplayName("parse zero value returns empty")
        void parse_zeroValue_returnsEmpty() {
            assertThat(DurationParser.parse("0s")).isEmpty();
            assertThat(DurationParser.parse("0d")).isEmpty();
        }

        @Test
        @DisplayName("parse mixed invalid returns empty")
        void parse_mixedInvalid_returnsEmpty() {
            assertThat(DurationParser.parse("1d30")).isEmpty();
            assertThat(DurationParser.parse("1d2")).isEmpty();
        }
    }
}
