package sh.joey.mc.messages;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WordBanks")
class WordBanksTest {

    @Nested
    @DisplayName("article")
    class Article {

        @Test
        @DisplayName("vowel start returns an")
        void article_vowelStart_returnsAn() {
            assertThat(WordBanks.article("apple")).isEqualTo("an");
            assertThat(WordBanks.article("elephant")).isEqualTo("an");
            assertThat(WordBanks.article("igloo")).isEqualTo("an");
            assertThat(WordBanks.article("orange")).isEqualTo("an");
            assertThat(WordBanks.article("umbrella")).isEqualTo("an");
        }

        @Test
        @DisplayName("consonant start returns a")
        void article_consonantStart_returnsA() {
            assertThat(WordBanks.article("banana")).isEqualTo("a");
            assertThat(WordBanks.article("creeper")).isEqualTo("a");
            assertThat(WordBanks.article("diamond")).isEqualTo("a");
        }

        @Test
        @DisplayName("uppercase vowel returns an")
        void article_uppercaseVowel_returnsAn() {
            assertThat(WordBanks.article("Apple")).isEqualTo("an");
            assertThat(WordBanks.article("ELEPHANT")).isEqualTo("an");
        }

        @Test
        @DisplayName("uppercase consonant returns a")
        void article_uppercaseConsonant_returnsA() {
            assertThat(WordBanks.article("Banana")).isEqualTo("a");
            assertThat(WordBanks.article("CREEPER")).isEqualTo("a");
        }

        @Test
        @DisplayName("null returns a")
        void article_null_returnsA() {
            assertThat(WordBanks.article(null)).isEqualTo("a");
        }

        @Test
        @DisplayName("empty string returns a")
        void article_emptyString_returnsA() {
            assertThat(WordBanks.article("")).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("Article (capitalized)")
    class ArticleCapitalized {

        @Test
        @DisplayName("vowel start returns An")
        void Article_vowelStart_returnsCapitalizedAn() {
            assertThat(WordBanks.Article("apple")).isEqualTo("An");
            assertThat(WordBanks.Article("elephant")).isEqualTo("An");
        }

        @Test
        @DisplayName("consonant start returns A")
        void Article_consonantStart_returnsCapitalizedA() {
            assertThat(WordBanks.Article("banana")).isEqualTo("A");
            assertThat(WordBanks.Article("creeper")).isEqualTo("A");
        }

        @Test
        @DisplayName("null returns A")
        void Article_null_returnsA() {
            assertThat(WordBanks.Article(null)).isEqualTo("A");
        }

        @Test
        @DisplayName("empty string returns A")
        void Article_emptyString_returnsA() {
            assertThat(WordBanks.Article("")).isEqualTo("A");
        }
    }

    @Nested
    @DisplayName("Word Bank Constants")
    class WordBankConstants {

        @Test
        @DisplayName("activities list is not empty")
        void activities_isNotEmpty() {
            assertThat(WordBanks.ACTIVITIES).isNotEmpty();
        }

        @Test
        @DisplayName("mobs list is not empty")
        void mobs_isNotEmpty() {
            assertThat(WordBanks.MOBS).isNotEmpty();
        }

        @Test
        @DisplayName("biomes list is not empty")
        void biomes_isNotEmpty() {
            assertThat(WordBanks.BIOMES).isNotEmpty();
        }

        @Test
        @DisplayName("adjectives list is not empty")
        void adjectives_isNotEmpty() {
            assertThat(WordBanks.ADJECTIVES).isNotEmpty();
        }
    }
}
