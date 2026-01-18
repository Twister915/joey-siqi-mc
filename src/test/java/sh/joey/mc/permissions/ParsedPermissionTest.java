package sh.joey.mc.permissions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import sh.joey.mc.permissions.ParsedPermission.PermDot;
import sh.joey.mc.permissions.ParsedPermission.PermLiteral;
import sh.joey.mc.permissions.ParsedPermission.PermWildcard;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ParsedPermission")
class ParsedPermissionTest {

    @Nested
    @DisplayName("Parsing")
    class Parsing {

        @Test
        @DisplayName("parse simple permission returns tokens")
        void parse_simplePermission_returnsTokens() {
            Optional<ParsedPermission> result = ParsedPermission.parse("smp.home");

            assertThat(result).isPresent();
            assertThat(result.get().tokens()).hasSize(3);
            assertThat(result.get().tokens().get(0)).isInstanceOf(PermLiteral.class);
            assertThat(result.get().tokens().get(1)).isInstanceOf(PermDot.class);
            assertThat(result.get().tokens().get(2)).isInstanceOf(PermLiteral.class);
            assertThat(((PermLiteral) result.get().tokens().get(0)).literal()).isEqualTo("smp");
            assertThat(((PermLiteral) result.get().tokens().get(2)).literal()).isEqualTo("home");
        }

        @Test
        @DisplayName("parse wildcard at end is valid")
        void parse_wildcardAtEnd_isValid() {
            Optional<ParsedPermission> result = ParsedPermission.parse("smp.*");

            assertThat(result).isPresent();
            assertThat(result.get().tokens()).hasSize(3);
            assertThat(result.get().tokens().get(2)).isInstanceOf(PermWildcard.class);
            assertThat(result.get().isWildcard()).isTrue();
        }

        @Test
        @DisplayName("parse single wildcard is valid")
        void parse_singleWildcard_isValid() {
            Optional<ParsedPermission> result = ParsedPermission.parse("*");

            assertThat(result).isPresent();
            assertThat(result.get().tokens()).hasSize(1);
            assertThat(result.get().tokens().get(0)).isInstanceOf(PermWildcard.class);
            assertThat(result.get().isWildcard()).isTrue();
        }

        @Test
        @DisplayName("parse empty string returns empty")
        void parse_emptyString_returnsEmpty() {
            assertThat(ParsedPermission.parse("")).isEmpty();
        }

        @Test
        @DisplayName("parse null returns empty")
        void parse_null_returnsEmpty() {
            assertThat(ParsedPermission.parse(null)).isEmpty();
        }

        @Test
        @DisplayName("parse leading dot returns empty")
        void parse_leadingDot_returnsEmpty() {
            assertThat(ParsedPermission.parse(".smp")).isEmpty();
        }

        @Test
        @DisplayName("parse trailing dot returns empty")
        void parse_trailingDot_returnsEmpty() {
            assertThat(ParsedPermission.parse("smp.")).isEmpty();
        }

        @Test
        @DisplayName("parse double dot returns empty")
        void parse_doubleDot_returnsEmpty() {
            assertThat(ParsedPermission.parse("smp..home")).isEmpty();
        }

        @Test
        @DisplayName("parse wildcard not at end returns empty")
        void parse_wildcardNotAtEnd_returnsEmpty() {
            assertThat(ParsedPermission.parse("smp.*.home")).isEmpty();
        }

        @Test
        @DisplayName("parse wildcard mixed with literal returns empty")
        void parse_wildcardMixedWithLiteral_returnsEmpty() {
            assertThat(ParsedPermission.parse("smp.ho*")).isEmpty();
        }

        @Test
        @DisplayName("parse invalid characters returns empty")
        void parse_invalidCharacters_returnsEmpty() {
            assertThat(ParsedPermission.parse("smp.home!")).isEmpty();
            assertThat(ParsedPermission.parse("smp home")).isEmpty();
            assertThat(ParsedPermission.parse("smp@home")).isEmpty();
        }

        @Test
        @DisplayName("parse with underscore and hyphen is valid")
        void parse_withUnderscoreAndHyphen_isValid() {
            Optional<ParsedPermission> result = ParsedPermission.parse("smp.home_set-command");

            assertThat(result).isPresent();
            assertThat(result.get().asString()).isEqualTo("smp.home_set-command");
        }

        @Test
        @DisplayName("parse with numbers is valid")
        void parse_withNumbers_isValid() {
            Optional<ParsedPermission> result = ParsedPermission.parse("smp.home1.set2");

            assertThat(result).isPresent();
            assertThat(result.get().asString()).isEqualTo("smp.home1.set2");
        }
    }

    @Nested
    @DisplayName("Matching")
    class Matching {

        @Test
        @DisplayName("matches exact match returns true")
        void matches_exactMatch_returnsTrue() {
            ParsedPermission perm1 = ParsedPermission.parse("smp.home").orElseThrow();
            ParsedPermission perm2 = ParsedPermission.parse("smp.home").orElseThrow();

            assertThat(perm1.matches(perm2)).isTrue();
        }

        @Test
        @DisplayName("matches different permission returns false")
        void matches_differentPermission_returnsFalse() {
            ParsedPermission perm1 = ParsedPermission.parse("smp.home").orElseThrow();
            ParsedPermission perm2 = ParsedPermission.parse("smp.warp").orElseThrow();

            assertThat(perm1.matches(perm2)).isFalse();
        }

        @Test
        @DisplayName("matches case insensitive returns true")
        void matches_caseInsensitive_returnsTrue() {
            ParsedPermission perm1 = ParsedPermission.parse("SMP.Home").orElseThrow();
            ParsedPermission perm2 = ParsedPermission.parse("smp.home").orElseThrow();

            assertThat(perm1.matches(perm2)).isTrue();
        }

        @Test
        @DisplayName("matches wildcard matches one level")
        void matches_wildcardMatchesOneLevel() {
            ParsedPermission wildcard = ParsedPermission.parse("smp.*").orElseThrow();
            ParsedPermission target = ParsedPermission.parse("smp.home").orElseThrow();

            assertThat(wildcard.matches(target)).isTrue();
        }

        @Test
        @DisplayName("matches wildcard matches multiple levels")
        void matches_wildcardMatchesMultipleLevels() {
            ParsedPermission wildcard = ParsedPermission.parse("smp.*").orElseThrow();
            ParsedPermission target = ParsedPermission.parse("smp.home.set").orElseThrow();

            assertThat(wildcard.matches(target)).isTrue();
        }

        @Test
        @DisplayName("matches root wildcard matches all")
        void matches_rootWildcard_matchesAll() {
            ParsedPermission wildcard = ParsedPermission.parse("*").orElseThrow();
            ParsedPermission target1 = ParsedPermission.parse("anything").orElseThrow();
            ParsedPermission target2 = ParsedPermission.parse("smp.home.set.other").orElseThrow();

            assertThat(wildcard.matches(target1)).isTrue();
            assertThat(wildcard.matches(target2)).isTrue();
        }

        @Test
        @DisplayName("matches prefix mismatch returns false")
        void matches_prefixMismatch_returnsFalse() {
            ParsedPermission wildcard = ParsedPermission.parse("smp.*").orElseThrow();
            ParsedPermission target = ParsedPermission.parse("other.home").orElseThrow();

            assertThat(wildcard.matches(target)).isFalse();
        }

        @Test
        @DisplayName("matches longer permission does not match shorter")
        void matches_longerDoesNotMatchShorter() {
            ParsedPermission longer = ParsedPermission.parse("smp.home.set").orElseThrow();
            ParsedPermission shorter = ParsedPermission.parse("smp.home").orElseThrow();

            assertThat(longer.matches(shorter)).isFalse();
        }

        @Test
        @DisplayName("matches shorter permission does not match longer")
        void matches_shorterDoesNotMatchLonger() {
            ParsedPermission shorter = ParsedPermission.parse("smp.home").orElseThrow();
            ParsedPermission longer = ParsedPermission.parse("smp.home.set").orElseThrow();

            assertThat(shorter.matches(longer)).isFalse();
        }
    }

    @Nested
    @DisplayName("Specificity")
    class Specificity {

        @Test
        @DisplayName("specificity more segments higher specificity")
        void specificity_moreSegments_higherSpecificity() {
            ParsedPermission perm1 = ParsedPermission.parse("smp.home.set").orElseThrow();
            ParsedPermission perm2 = ParsedPermission.parse("smp.home").orElseThrow();

            assertThat(perm1.specificity()).isGreaterThan(perm2.specificity());
        }

        @Test
        @DisplayName("specificity literal vs wildcard literal higher")
        void specificity_literalVsWildcard_literalHigher() {
            ParsedPermission literal = ParsedPermission.parse("smp.home").orElseThrow();
            ParsedPermission wildcard = ParsedPermission.parse("smp.*").orElseThrow();

            assertThat(literal.specificity()).isGreaterThan(wildcard.specificity());
        }

        @Test
        @DisplayName("specificity counts only literals")
        void specificity_countsOnlyLiterals() {
            ParsedPermission perm1 = ParsedPermission.parse("a.b.c").orElseThrow();
            ParsedPermission perm2 = ParsedPermission.parse("a.b.*").orElseThrow();

            assertThat(perm1.specificity()).isEqualTo(3);
            assertThat(perm2.specificity()).isEqualTo(2);
        }

        @Test
        @DisplayName("specificity root wildcard has zero specificity")
        void specificity_rootWildcard_hasZeroSpecificity() {
            ParsedPermission wildcard = ParsedPermission.parse("*").orElseThrow();

            assertThat(wildcard.specificity()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("String Representation")
    class StringRepresentation {

        @Test
        @DisplayName("asString returns original format")
        void asString_returnsOriginalFormat() {
            ParsedPermission perm = ParsedPermission.parse("smp.home.set").orElseThrow();

            assertThat(perm.asString()).isEqualTo("smp.home.set");
        }

        @Test
        @DisplayName("asString with wildcard")
        void asString_withWildcard() {
            ParsedPermission perm = ParsedPermission.parse("smp.*").orElseThrow();

            assertThat(perm.asString()).isEqualTo("smp.*");
        }

        @Test
        @DisplayName("toString equals asString")
        void toString_equalsAsString() {
            ParsedPermission perm = ParsedPermission.parse("smp.home").orElseThrow();

            assertThat(perm.toString()).isEqualTo(perm.asString());
        }
    }

    @Nested
    @DisplayName("isWildcard")
    class IsWildcard {

        @Test
        @DisplayName("isWildcard returns true for wildcard permission")
        void isWildcard_returnsTrue_forWildcard() {
            ParsedPermission perm = ParsedPermission.parse("smp.*").orElseThrow();

            assertThat(perm.isWildcard()).isTrue();
        }

        @Test
        @DisplayName("isWildcard returns false for literal permission")
        void isWildcard_returnsFalse_forLiteral() {
            ParsedPermission perm = ParsedPermission.parse("smp.home").orElseThrow();

            assertThat(perm.isWildcard()).isFalse();
        }
    }
}
