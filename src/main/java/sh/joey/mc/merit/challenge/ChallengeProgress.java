package sh.joey.mc.merit.challenge;

import java.time.Instant;

/**
 * Progress on a weekly challenge.
 */
public record ChallengeProgress(
        Challenge challenge,
        long progress,
        boolean completed,
        Instant completedAt
) {
    /**
     * Calculate percentage complete (0-100).
     */
    public int percentComplete() {
        if (completed) return 100;
        if (challenge.target() <= 0) return 100;
        return (int) Math.min(100, (progress * 100) / challenge.target());
    }

    /**
     * Check if this progress is at a milestone (10%, 25%, 50%, 100%).
     */
    public boolean isAtMilestone() {
        int percent = percentComplete();
        return percent == 10 || percent == 25 || percent == 50 || percent == 100;
    }
}
