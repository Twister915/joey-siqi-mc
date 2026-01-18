package sh.joey.mc.steve;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A historical Steve Q&A entry.
 */
public record SteveHistoryEntry(
        UUID id,
        UUID playerId,
        String question,
        String answer,
        List<SteveAnswer.Citation> citations,
        Optional<Double> costCents,
        Optional<String> modelName,
        int contextCount,
        Instant askedAt
) {}
