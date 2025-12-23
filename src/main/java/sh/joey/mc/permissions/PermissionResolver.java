package sh.joey.mc.permissions;

import io.reactivex.rxjava3.core.Single;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the effective permissions and attributes for a player.
 * <p>
 * Resolution algorithm:
 * <ol>
 *   <li>Player's explicit attributes take highest priority (null values fall through)</li>
 *   <li>Group attributes resolved by highest priority group with a value</li>
 *   <li>Player's explicit permission grants are first in list (highest priority)</li>
 *   <li>Group permission grants ordered by group priority (descending)</li>
 *   <li>Filter grants by world (global + world-specific)</li>
 *   <li>For conflicts, more specific permissions win; same specificity = higher priority wins</li>
 * </ol>
 */
public final class PermissionResolver {

    private final PermissionStorage storage;

    public PermissionResolver(PermissionStorage storage) {
        this.storage = storage;
    }

    /**
     * Resolve all permissions and attributes for a player in a given world.
     *
     * @param playerId The player's UUID
     * @param worldId  The world to resolve permissions for
     * @return A Single emitting the resolved permissions
     */
    public Single<ResolvedPermissions> resolve(UUID playerId, UUID worldId) {
        return Single.zip(
                storage.getPlayerAttributes(playerId)
                        .defaultIfEmpty(PermissibleAttributes.EMPTY),
                storage.getPlayerPermissions(playerId).toList(),
                storage.getPlayerGroups(playerId)
                        .toSortedList((a, b) -> Integer.compare(b.priority(), a.priority())),
                (PermissibleAttributes playerAttrs, List<PermissionGrant> playerGrants, List<Group> groups) ->
                        buildResolvedPermissions(playerId, worldId, playerAttrs, playerGrants, groups)
        );
    }

    private ResolvedPermissions buildResolvedPermissions(
            UUID playerId,
            UUID worldId,
            PermissibleAttributes playerAttrs,
            List<PermissionGrant> playerGrants,
            List<Group> groups
    ) {
        // 1. Resolve attributes (player overrides group)
        PermissibleAttributes resolvedAttrs = resolveAttributes(playerAttrs, groups);

        // 2. Collect all grants with priority ordering
        List<PermissionGrant> allGrants = new ArrayList<>();

        // Player grants first (already have Integer.MAX_VALUE priority from storage)
        allGrants.addAll(playerGrants);

        // Group grants in priority order (groups already sorted by priority DESC)
        for (Group group : groups) {
            allGrants.addAll(group.grants());
        }

        // 3. Filter by world and resolve conflicts
        Map<String, Boolean> resolved = resolveGrants(allGrants, worldId);

        return new ResolvedPermissions(playerId, worldId, resolvedAttrs, resolved);
    }

    private PermissibleAttributes resolveAttributes(PermissibleAttributes playerAttrs, List<Group> groups) {
        PermissibleAttributes result = playerAttrs;

        // Merge with group attributes in priority order (highest first)
        for (Group group : groups) {
            result = result.merge(group.attributes());
        }

        return result;
    }

    private Map<String, Boolean> resolveGrants(List<PermissionGrant> grants, UUID worldId) {
        // Track best grant for each permission (by specificity then priority)
        Map<String, PermissionGrant> best = new HashMap<>();

        for (PermissionGrant grant : grants) {
            // Filter by world
            if (!grant.appliesTo(worldId)) {
                continue;
            }

            String key = grant.permission().asString().toLowerCase();
            PermissionGrant existing = best.get(key);

            if (existing == null) {
                best.put(key, grant);
            } else {
                // Compare: higher specificity wins, then higher priority
                int specCmp = Integer.compare(
                        grant.permission().specificity(),
                        existing.permission().specificity()
                );
                if (specCmp > 0 || (specCmp == 0 && grant.priority() > existing.priority())) {
                    best.put(key, grant);
                }
            }
        }

        // Convert to final map
        Map<String, Boolean> result = new HashMap<>();
        for (var entry : best.entrySet()) {
            result.put(entry.getKey(), entry.getValue().state());
        }
        return result;
    }
}
