package sh.joey.mc.merit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LevelCalculator.
 */
class LevelCalculatorTest {

    private LevelCalculator calculator;

    @BeforeEach
    void setUp() {
        // Create a config with default values including early level discount
        MeritConfig config = new MeritConfig(
                true, 100, 1.8, 20, 0.7, 10, 30, 500, 30, 8
        );
        calculator = new LevelCalculator(config);
    }

    @Test
    @DisplayName("Level 1 requires 0 merit")
    void level1_requiresZeroMerit() {
        assertThat(calculator.meritForLevel(1)).isEqualTo(0);
    }

    @Test
    @DisplayName("Level 2 requires some merit")
    void level2_requiresMerit() {
        long merit = calculator.meritForLevel(2);
        assertThat(merit).isGreaterThan(0);
    }

    @Test
    @DisplayName("Higher levels require more merit")
    void higherLevels_requireMoreMerit() {
        long level5 = calculator.meritForLevel(5);
        long level10 = calculator.meritForLevel(10);
        long level50 = calculator.meritForLevel(50);

        assertThat(level10).isGreaterThan(level5);
        assertThat(level50).isGreaterThan(level10);
    }

    @Test
    @DisplayName("Merit to level - zero merit is level 1")
    void meritToLevel_zeroMerit_isLevel1() {
        assertThat(calculator.levelForMerit(0)).isEqualTo(1);
    }

    @Test
    @DisplayName("Merit to level - negative merit is level 1")
    void meritToLevel_negativeMerit_isLevel1() {
        assertThat(calculator.levelForMerit(-100)).isEqualTo(1);
    }

    @Test
    @DisplayName("Merit to level - exact threshold")
    void meritToLevel_exactThreshold() {
        long meritFor10 = calculator.meritForLevel(10);
        assertThat(calculator.levelForMerit(meritFor10)).isEqualTo(10);
    }

    @Test
    @DisplayName("Merit to level - just under threshold")
    void meritToLevel_justUnderThreshold() {
        long meritFor10 = calculator.meritForLevel(10);
        assertThat(calculator.levelForMerit(meritFor10 - 1)).isEqualTo(9);
    }

    @Test
    @DisplayName("Merit to level - just over threshold")
    void meritToLevel_justOverThreshold() {
        long meritFor10 = calculator.meritForLevel(10);
        assertThat(calculator.levelForMerit(meritFor10 + 1)).isEqualTo(10);
    }

    @Test
    @DisplayName("Progress to next level - at level start is 0")
    void progressToNextLevel_atLevelStart() {
        long meritFor5 = calculator.meritForLevel(5);
        double progress = calculator.progressToNextLevel(meritFor5);
        assertThat(progress).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Progress to next level - halfway through")
    void progressToNextLevel_halfway() {
        long meritFor5 = calculator.meritForLevel(5);
        long meritFor6 = calculator.meritForLevel(6);
        long halfway = (meritFor5 + meritFor6) / 2;
        double progress = calculator.progressToNextLevel(halfway);
        assertThat(progress).isBetween(0.4, 0.6);
    }

    @Test
    @DisplayName("Merit to next level - just leveled up")
    void meritToNextLevel_justLeveledUp() {
        long meritFor5 = calculator.meritForLevel(5);
        long meritFor6 = calculator.meritForLevel(6);
        long toNext = calculator.meritToNextLevel(meritFor5);
        assertThat(toNext).isEqualTo(meritFor6 - meritFor5);
    }

    @Test
    @DisplayName("Merit for level only - returns incremental amount")
    void meritForLevelOnly_returnsIncremental() {
        long total5 = calculator.meritForLevel(5);
        long total6 = calculator.meritForLevel(6);
        long only6 = calculator.meritForLevelOnly(6);
        assertThat(only6).isEqualTo(total6 - total5);
    }

    @Test
    @DisplayName("High level calculation works")
    void highLevel_calculationWorks() {
        long meritFor100 = calculator.meritForLevel(100);
        int level = calculator.levelForMerit(meritFor100);
        assertThat(level).isEqualTo(100);
    }

    @Test
    @DisplayName("Round trip - merit to level to merit")
    void roundTrip_meritToLevelToMerit() {
        for (int targetLevel = 1; targetLevel <= 50; targetLevel++) {
            long merit = calculator.meritForLevel(targetLevel);
            int calculatedLevel = calculator.levelForMerit(merit);
            assertThat(calculatedLevel).isEqualTo(targetLevel);
        }
    }
}
