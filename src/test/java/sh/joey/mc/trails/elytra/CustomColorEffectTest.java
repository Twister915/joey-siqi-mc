package sh.joey.mc.trails.elytra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomColorEffect")
class CustomColorEffectTest {

    @Nested
    @DisplayName("isValidHex")
    class IsValidHex {

        @Test
        @DisplayName("six digits returns true")
        void isValidHex_sixDigits_returnsTrue() {
            assertThat(CustomColorEffect.isValidHex("ff00ff")).isTrue();
        }

        @Test
        @DisplayName("valid uppercase returns true")
        void isValidHex_validUppercase_returnsTrue() {
            assertThat(CustomColorEffect.isValidHex("FF00FF")).isTrue();
        }

        @Test
        @DisplayName("mixed case returns true")
        void isValidHex_mixedCase_returnsTrue() {
            assertThat(CustomColorEffect.isValidHex("Ff00Ff")).isTrue();
        }

        @Test
        @DisplayName("five digits returns false")
        void isValidHex_fiveDigits_returnsFalse() {
            assertThat(CustomColorEffect.isValidHex("ff00f")).isFalse();
        }

        @Test
        @DisplayName("seven digits returns false")
        void isValidHex_sevenDigits_returnsFalse() {
            assertThat(CustomColorEffect.isValidHex("ff00fff")).isFalse();
        }

        @Test
        @DisplayName("invalid chars returns false")
        void isValidHex_invalidChars_returnsFalse() {
            assertThat(CustomColorEffect.isValidHex("gggggg")).isFalse();
            assertThat(CustomColorEffect.isValidHex("ff00g0")).isFalse();
        }

        @Test
        @DisplayName("null returns false")
        void isValidHex_null_returnsFalse() {
            assertThat(CustomColorEffect.isValidHex(null)).isFalse();
        }

        @Test
        @DisplayName("empty string returns false")
        void isValidHex_emptyString_returnsFalse() {
            assertThat(CustomColorEffect.isValidHex("")).isFalse();
        }

        @Test
        @DisplayName("all zeros is valid")
        void isValidHex_allZeros_isValid() {
            assertThat(CustomColorEffect.isValidHex("000000")).isTrue();
        }

        @Test
        @DisplayName("all f is valid")
        void isValidHex_allF_isValid() {
            assertThat(CustomColorEffect.isValidHex("ffffff")).isTrue();
        }
    }

    @Nested
    @DisplayName("isCustomColor")
    class IsCustomColor {

        @Test
        @DisplayName("rgb prefix returns true")
        void isCustomColor_rgbPrefix_returnsTrue() {
            assertThat(CustomColorEffect.isCustomColor("rgb:ff00ff")).isTrue();
        }

        @Test
        @DisplayName("rgb prefix uppercase returns true")
        void isCustomColor_rgbPrefixUppercase_returnsTrue() {
            assertThat(CustomColorEffect.isCustomColor("RGB:FF00FF")).isTrue();
        }

        @Test
        @DisplayName("no prefix returns false")
        void isCustomColor_noPrefix_returnsFalse() {
            assertThat(CustomColorEffect.isCustomColor("ff00ff")).isFalse();
        }

        @Test
        @DisplayName("null returns false")
        void isCustomColor_null_returnsFalse() {
            assertThat(CustomColorEffect.isCustomColor(null)).isFalse();
        }

        @Test
        @DisplayName("empty string returns false")
        void isCustomColor_emptyString_returnsFalse() {
            assertThat(CustomColorEffect.isCustomColor("")).isFalse();
        }

        @Test
        @DisplayName("other effect id returns false")
        void isCustomColor_otherEffectId_returnsFalse() {
            assertThat(CustomColorEffect.isCustomColor("flame")).isFalse();
            assertThat(CustomColorEffect.isCustomColor("rainbow")).isFalse();
        }
    }

    @Nested
    @DisplayName("fromId")
    class FromId {

        @Test
        @DisplayName("valid hex returns effect")
        void fromId_validHex_returnsEffect() {
            CustomColorEffect effect = CustomColorEffect.fromId("rgb:ff5500");

            assertThat(effect).isNotNull();
            assertThat(effect.hexCode()).isEqualTo("ff5500");
        }

        @Test
        @DisplayName("uppercase hex returns effect")
        void fromId_uppercaseHex_returnsEffect() {
            CustomColorEffect effect = CustomColorEffect.fromId("rgb:FF5500");

            assertThat(effect).isNotNull();
            // Hex code is normalized to lowercase
            assertThat(effect.hexCode()).isEqualTo("ff5500");
        }

        @Test
        @DisplayName("invalid hex returns null")
        void fromId_invalidHex_returnsNull() {
            assertThat(CustomColorEffect.fromId("rgb:gggggg")).isNull();
            assertThat(CustomColorEffect.fromId("rgb:ff00")).isNull();
        }

        @Test
        @DisplayName("no prefix returns null")
        void fromId_noPrefix_returnsNull() {
            assertThat(CustomColorEffect.fromId("ff5500")).isNull();
        }

        @Test
        @DisplayName("null returns null")
        void fromId_null_returnsNull() {
            assertThat(CustomColorEffect.fromId(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Effect Properties")
    class EffectProperties {

        @Test
        @DisplayName("id includes rgb prefix")
        void id_includesRgbPrefix() {
            CustomColorEffect effect = new CustomColorEffect("ff5500");

            assertThat(effect.id()).isEqualTo("rgb:ff5500");
        }

        @Test
        @DisplayName("displayName includes hex code")
        void displayName_includesHexCode() {
            CustomColorEffect effect = new CustomColorEffect("ff5500");

            assertThat(effect.displayName()).contains("FF5500");
        }

        @Test
        @DisplayName("hexCode normalized to lowercase")
        void hexCode_normalizedToLowercase() {
            CustomColorEffect effect = new CustomColorEffect("FF5500");

            assertThat(effect.hexCode()).isEqualTo("ff5500");
        }
    }
}
