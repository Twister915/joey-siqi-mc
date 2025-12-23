package sh.joey.mc.permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Represents a parsed permission string with tokenized components.
 * <p>
 * Valid permission formats:
 * <ul>
 *   <li>{@code worldedit.wand} - specific permission</li>
 *   <li>{@code plugin.*} - wildcard matching all under plugin</li>
 *   <li>{@code *} - matches everything</li>
 *   <li>{@code some.nested.permission} - multi-level permission</li>
 * </ul>
 * <p>
 * Invalid formats:
 * <ul>
 *   <li>{@code .foo} - leading dot</li>
 *   <li>{@code foo.} - trailing dot</li>
 *   <li>{@code foo..bar} - double dot</li>
 *   <li>{@code *foo} or {@code foo*bar} - wildcard not at end</li>
 * </ul>
 */
public record ParsedPermission(List<PermissionToken> tokens) {

    public sealed interface PermissionToken permits PermLiteral, PermDot, PermWildcard {}

    public record PermLiteral(String literal) implements PermissionToken {}
    public record PermDot() implements PermissionToken {}
    public record PermWildcard() implements PermissionToken {}

    /**
     * Parse a permission string into tokens.
     *
     * @param input the permission string to parse
     * @return parsed permission, or empty if invalid syntax
     */
    public static Optional<ParsedPermission> parse(String input) {
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }

        List<PermissionToken> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '.') {
                // Leading dot or double dot is invalid
                if (current.isEmpty() && !tokens.isEmpty()) {
                    return Optional.empty();
                }
                // Can't have a leading dot
                if (current.isEmpty() && tokens.isEmpty()) {
                    return Optional.empty();
                }
                // Flush current literal
                if (!current.isEmpty()) {
                    tokens.add(new PermLiteral(current.toString()));
                    current = new StringBuilder();
                }
                tokens.add(new PermDot());
            } else if (c == '*') {
                // Wildcard must be at the end and either be the first token or follow a dot
                if (!current.isEmpty()) {
                    // Mixed with literals like "foo*" is invalid
                    return Optional.empty();
                }
                if (i != input.length() - 1) {
                    // Wildcard not at end
                    return Optional.empty();
                }
                // Must follow a dot (or be the only token for just "*")
                if (!tokens.isEmpty() && !(tokens.get(tokens.size() - 1) instanceof PermDot)) {
                    return Optional.empty();
                }
                tokens.add(new PermWildcard());
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                current.append(c);
            } else {
                // Invalid character
                return Optional.empty();
            }
        }

        // Flush remaining literal
        if (!current.isEmpty()) {
            tokens.add(new PermLiteral(current.toString()));
        }

        // Validate: can't end with dot, must have at least one token
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        if (tokens.get(tokens.size() - 1) instanceof PermDot) {
            return Optional.empty();
        }

        return Optional.of(new ParsedPermission(List.copyOf(tokens)));
    }

    /**
     * Check if this permission matches another permission.
     * <p>
     * Wildcards in THIS permission can match any suffix in the target.
     * For example:
     * <ul>
     *   <li>{@code plugin.*} matches {@code plugin.command} and {@code plugin.a.b.c}</li>
     *   <li>{@code plugin.command} matches only itself</li>
     *   <li>{@code *} matches everything</li>
     * </ul>
     *
     * @param target the permission to check against
     * @return true if this permission matches the target
     */
    public boolean matches(ParsedPermission target) {
        int thisIdx = 0;
        int targetIdx = 0;

        while (thisIdx < tokens.size() && targetIdx < target.tokens.size()) {
            PermissionToken thisToken = tokens.get(thisIdx);
            PermissionToken targetToken = target.tokens.get(targetIdx);

            if (thisToken instanceof PermWildcard) {
                // Wildcard matches rest of target
                return true;
            }

            if (thisToken instanceof PermDot && targetToken instanceof PermDot) {
                thisIdx++;
                targetIdx++;
            } else if (thisToken instanceof PermLiteral thisLit
                       && targetToken instanceof PermLiteral targetLit) {
                if (!thisLit.literal().equalsIgnoreCase(targetLit.literal())) {
                    return false;
                }
                thisIdx++;
                targetIdx++;
            } else {
                // Mismatched token types
                return false;
            }
        }

        // Both must be exhausted for exact match (unless wildcard already returned)
        return thisIdx == tokens.size() && targetIdx == target.tokens.size();
    }

    /**
     * Returns the original string representation.
     */
    public String asString() {
        StringBuilder sb = new StringBuilder();
        for (PermissionToken token : tokens) {
            if (token instanceof PermLiteral lit) {
                sb.append(lit.literal());
            } else if (token instanceof PermDot) {
                sb.append('.');
            } else if (token instanceof PermWildcard) {
                sb.append('*');
            }
        }
        return sb.toString();
    }

    /**
     * Returns true if this permission ends with a wildcard.
     */
    public boolean isWildcard() {
        return !tokens.isEmpty() && tokens.get(tokens.size() - 1) instanceof PermWildcard;
    }

    /**
     * Returns the number of non-wildcard path segments (for specificity ranking).
     * <p>
     * Higher specificity means a more specific permission.
     * Used for conflict resolution: more specific permissions take precedence.
     */
    public int specificity() {
        int count = 0;
        for (PermissionToken token : tokens) {
            if (token instanceof PermLiteral) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return asString();
    }
}
