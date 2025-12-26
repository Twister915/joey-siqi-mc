package sh.joey.mc.nickname;

import io.reactivex.rxjava3.core.Single;
import sh.joey.mc.session.PlayerSessionStorage;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates nicknames for the nickname system.
 * <p>
 * Validation rules:
 * <ul>
 *   <li>Length: 3-16 characters</li>
 *   <li>Characters: alphanumeric + underscore only</li>
 *   <li>Not a reserved command name</li>
 *   <li>Not matching another player's username</li>
 *   <li>Not already taken by another player</li>
 * </ul>
 */
public final class NicknameValidator {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 16;
    private static final Pattern VALID_CHARS = Pattern.compile("^[a-zA-Z0-9_]+$");

    private static final Set<String> RESERVED_NAMES = Set.of(
            "clear", "reset", "set", "remove", "delete",
            "server", "admin", "moderator", "mod", "owner",
            "console", "system", "null", "undefined"
    );

    private final PlayerSessionStorage sessionStorage;
    private final NicknameStorage nicknameStorage;

    public NicknameValidator(PlayerSessionStorage sessionStorage, NicknameStorage nicknameStorage) {
        this.sessionStorage = sessionStorage;
        this.nicknameStorage = nicknameStorage;
    }

    /**
     * Validate a nickname for a player.
     *
     * @param playerId the player who wants to use this nickname
     * @param nickname the desired nickname
     * @return a Single containing the validation result
     */
    public Single<ValidationResult> validate(UUID playerId, String nickname) {
        // Synchronous checks first
        ValidationResult syncResult = validateSync(nickname);
        if (!syncResult.valid()) {
            return Single.just(syncResult);
        }

        // Async checks: username collision and nickname availability
        return checkUsernameCollision(nickname)
                .flatMap(usernameExists -> {
                    if (usernameExists) {
                        return Single.just(ValidationResult.error(
                                "That name is already a player's username."));
                    }
                    return checkNicknameAvailability(playerId, nickname);
                });
    }

    /**
     * Perform synchronous validation checks.
     */
    private ValidationResult validateSync(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return ValidationResult.error("Nickname cannot be empty.");
        }

        String trimmed = nickname.trim();

        if (trimmed.length() < MIN_LENGTH) {
            return ValidationResult.error(
                    "Nickname must be at least " + MIN_LENGTH + " characters.");
        }

        if (trimmed.length() > MAX_LENGTH) {
            return ValidationResult.error(
                    "Nickname must be at most " + MAX_LENGTH + " characters.");
        }

        if (!VALID_CHARS.matcher(trimmed).matches()) {
            return ValidationResult.error(
                    "Nickname can only contain letters, numbers, and underscores.");
        }

        if (RESERVED_NAMES.contains(trimmed.toLowerCase())) {
            return ValidationResult.error("That nickname is reserved.");
        }

        return ValidationResult.ok();
    }

    /**
     * Check if the nickname matches an existing username.
     */
    private Single<Boolean> checkUsernameCollision(String nickname) {
        return sessionStorage.usernameExists(nickname);
    }

    /**
     * Check if the nickname is available for the given player.
     */
    private Single<ValidationResult> checkNicknameAvailability(UUID playerId, String nickname) {
        return nicknameStorage.isNicknameAvailableFor(playerId, nickname)
                .map(available -> {
                    if (!available) {
                        return ValidationResult.error("That nickname is already taken.");
                    }
                    return ValidationResult.ok();
                });
    }

    /**
     * Result of nickname validation.
     */
    public record ValidationResult(boolean valid, String errorMessage) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
    }
}
