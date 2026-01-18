package sh.joey.mc.pagination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatMetrics")
class ChatMetricsTest {

    @Nested
    @DisplayName("getCharWidth")
    class GetCharWidth {

        @Test
        @DisplayName("narrow chars returns 2")
        void getCharWidth_narrowChars_returns2() {
            assertThat(ChatMetrics.getCharWidth('i')).isEqualTo(2);
            assertThat(ChatMetrics.getCharWidth('l')).isEqualTo(2);
            assertThat(ChatMetrics.getCharWidth('!')).isEqualTo(2);
            assertThat(ChatMetrics.getCharWidth('|')).isEqualTo(2);
            assertThat(ChatMetrics.getCharWidth('.')).isEqualTo(2);
            assertThat(ChatMetrics.getCharWidth(',')).isEqualTo(2);
        }

        @Test
        @DisplayName("slightly narrow chars returns 4")
        void getCharWidth_slightlyNarrowChars_returns4() {
            assertThat(ChatMetrics.getCharWidth('I')).isEqualTo(4);
            assertThat(ChatMetrics.getCharWidth('t')).isEqualTo(4);
            assertThat(ChatMetrics.getCharWidth('f')).isEqualTo(4);
            assertThat(ChatMetrics.getCharWidth('(')).isEqualTo(4);
            assertThat(ChatMetrics.getCharWidth(')')).isEqualTo(4);
            assertThat(ChatMetrics.getCharWidth('[')).isEqualTo(4);
            assertThat(ChatMetrics.getCharWidth(']')).isEqualTo(4);
        }

        @Test
        @DisplayName("space returns 4")
        void getCharWidth_space_returns4() {
            assertThat(ChatMetrics.getCharWidth(' ')).isEqualTo(4);
        }

        @Test
        @DisplayName("wide chars returns 6")
        void getCharWidth_wideChars_returns6() {
            assertThat(ChatMetrics.getCharWidth('m')).isEqualTo(6);
            assertThat(ChatMetrics.getCharWidth('w')).isEqualTo(6);
            assertThat(ChatMetrics.getCharWidth('M')).isEqualTo(6);
            assertThat(ChatMetrics.getCharWidth('W')).isEqualTo(6);
            assertThat(ChatMetrics.getCharWidth('@')).isEqualTo(6);
        }

        @Test
        @DisplayName("default chars returns 6")
        void getCharWidth_defaultChars_returns6() {
            assertThat(ChatMetrics.getCharWidth('a')).isEqualTo(6);
            assertThat(ChatMetrics.getCharWidth('b')).isEqualTo(6);
            assertThat(ChatMetrics.getCharWidth('c')).isEqualTo(6);
            assertThat(ChatMetrics.getCharWidth('1')).isEqualTo(6);
            assertThat(ChatMetrics.getCharWidth('2')).isEqualTo(6);
        }

        @Test
        @DisplayName("bullet points return 2")
        void getCharWidth_bulletPoints_returns2() {
            assertThat(ChatMetrics.getCharWidth('•')).isEqualTo(2);
            assertThat(ChatMetrics.getCharWidth('▸')).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("calculatePixelWidth")
    class CalculatePixelWidth {

        @Test
        @DisplayName("empty string returns zero")
        void calculatePixelWidth_emptyString_returnsZero() {
            assertThat(ChatMetrics.calculatePixelWidth("")).isEqualTo(0);
        }

        @Test
        @DisplayName("single char returns char width")
        void calculatePixelWidth_singleChar_returnsCharWidth() {
            assertThat(ChatMetrics.calculatePixelWidth("a")).isEqualTo(6);
            assertThat(ChatMetrics.calculatePixelWidth("i")).isEqualTo(2);
        }

        @Test
        @DisplayName("mixed widths correct total")
        void calculatePixelWidth_mixedWidths_correctTotal() {
            // "ill" = 2 + 2 + 2 = 6
            assertThat(ChatMetrics.calculatePixelWidth("ill")).isEqualTo(6);

            // "abc" = 6 + 6 + 6 = 18
            assertThat(ChatMetrics.calculatePixelWidth("abc")).isEqualTo(18);

            // "a i" = 6 + 4 + 2 = 12
            assertThat(ChatMetrics.calculatePixelWidth("a i")).isEqualTo(12);
        }

        @Test
        @DisplayName("all narrow chars")
        void calculatePixelWidth_allNarrowChars() {
            // "!!!!!" = 5 * 2 = 10
            assertThat(ChatMetrics.calculatePixelWidth("!!!!!")).isEqualTo(10);
        }

        @Test
        @DisplayName("all wide chars")
        void calculatePixelWidth_allWideChars() {
            // "mmmm" = 4 * 6 = 24
            assertThat(ChatMetrics.calculatePixelWidth("mmmm")).isEqualTo(24);
        }
    }

    @Nested
    @DisplayName("calculateVisualLines")
    class CalculateVisualLines {

        @Test
        @DisplayName("short text returns one")
        void calculateVisualLines_shortText_returnsOne() {
            assertThat(ChatMetrics.calculateVisualLines("Hello")).isEqualTo(1);
        }

        @Test
        @DisplayName("empty string returns one")
        void calculateVisualLines_emptyString_returnsOne() {
            assertThat(ChatMetrics.calculateVisualLines("")).isEqualTo(1);
        }

        @Test
        @DisplayName("exactly one line width returns one")
        void calculateVisualLines_exactlyOneLine_returnsOne() {
            // Chat width is 320 pixels
            // "a" is 6 pixels, so 53 'a' chars = 318 pixels (fits in one line)
            String text = "a".repeat(53);
            assertThat(ChatMetrics.calculateVisualLines(text)).isEqualTo(1);
        }

        @Test
        @DisplayName("just over one line returns two")
        void calculateVisualLines_justOverOneLine_returnsTwo() {
            // 54 'a' chars = 324 pixels (needs two lines)
            String text = "a".repeat(54);
            assertThat(ChatMetrics.calculateVisualLines(text)).isEqualTo(2);
        }

        @Test
        @DisplayName("long text returns correct count")
        void calculateVisualLines_longText_correctCount() {
            // 160 'a' chars = 960 pixels = 3 lines (960 / 320 = 3)
            String text = "a".repeat(160);
            assertThat(ChatMetrics.calculateVisualLines(text)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Constants")
    class Constants {

        @Test
        @DisplayName("chat width is 320 pixels")
        void chatWidth_is320() {
            assertThat(ChatMetrics.CHAT_WIDTH_PIXELS).isEqualTo(320);
        }

        @Test
        @DisplayName("visible lines is 20")
        void visibleLines_is20() {
            assertThat(ChatMetrics.CHAT_VISIBLE_LINES).isEqualTo(20);
        }
    }
}
