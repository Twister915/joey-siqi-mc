Hi Claude, here is a spec for a permissions system I'd like you to implement. Please remember that you should output a plan markdown for this to plans/ folder in the project.


# Permissions System

The permissions system should be implemented in the following stages. Please note there is already a package for permissions with very little work done.

## Permission Tokenizer

Permissions in minecraft are things like `plugin.command` for example, `worldedit.wand`. Often, people will want to grant groups of permissions using syntax with a wildcard such as `*`.

I want to structure this syntax properly. I want a java sealed interface for the token types, and to have a process which parses a permission string into it's component tokens.

I have already begun this process under the `permissions` package.

## Permissible

A permissible is an abstract term which applies to players and groups. The attributes of a permissible entity are:
* The list of "permission grants" Default state = empty
* The chat prefix, suffix; tablist prefix, suffix; nameplate prefix, suffix. Default state for all is NULL which should be interpreted as "undeclared"

## Permission Grants

Permission grants are specified as having:
* `permission` (the valid text representation of the permission syntax)
* `world` (a UUID of a world in which the permission grant applies, or NULL if the permission is applied globally)
* `state` (true / false, allowing us to declare a permission as ALLOW or DENY)

## Groups

Groups are to be stored in the SQL table `groups`, and have the following attributes:
* cannonical_name: a lowercase version of the name provided by the user (the primary key)
* name: the display version of the group's name, as provided by the user
* priority: a non-negative integer which allows us to order groups to resolve permission conflicts
* default: a boolean, which, if true... makes all users a member of this group

Players can be marked as members of any group using the pivot table `player_groups`, which simply stores a `player_id` and `group_cannonical_name`.

Groups are permissible entities, which means they also have all the permissible attributes: chat_prefix, chat_suffix, tablist_prefix, tablist_suffix, nameplate_prefix, nameplate_suffix (all nullable TEXT columns in the `groups` table).

The `groups` table should also have standard timestamps like `inserted_at` and `updated_at`.

Finally, there is a table for the one-to-many relationship between groups and permission grants. `group_permissions` will store the `group_cannonical_name` and all columns required to store permission grant data (see the permission grants section).

## Players

Players are also permissible, which means there will be these three tables:
* `player_permissions` - a table which stores the list of permission grants applied directly to specific players, including the `player_id` (UUID), and the required attributes for permission grants.
* `perm_players` - a table which stores a row if any permissible setting (such as chat_prefix, chat_suffix, tablist_prefix, tablist_suffix, nameplate_prefix, nameplate_suffix) is explicitly set for a player.
* `player_groups` (already explained above)

## Data Model

Considering all we have declared above, we can compute a "permission state" for each and every player according to this algorthim:

1. Each player may have the following scalar properties: [`chat_prefix`, `chat_suffix`, `tablist_prefix`, `tablist_suffix`, `nameplate_prefix`, `nameplate_suffix`], AND a list of permission grants.
2. If any of the scalars are declared explicitly for a player directly, then we use that value for the property. The player's explicit declarations take the highest priority. Similarly, any permission grants declared explicitly for a player will be included first in the list of the player's permission grants.
3. For each declared group, the player may or may not be a member of those groups. For groups the player is a member of, we determine the value of scalar properties by taking the first declared value with the highest priority. For permission grants coming from groups, we collect them into a list ordered by the priority of the group, descending.
4. We take the list of permission grants, and determine the conflict-free version of this list, and filter out permission grants which do not apply in the current world.

This state will include all the scalar properties and a list of permission grants. Then, our permissions plugin will actualize this desired state when necessary.

## Username Resolution

All commands using players should be careful to use the existing database views to identify player names and convert them to UUIDs so we can always support offline player lookup.

## Prefixes and Suffixes

The purpose of the prefix / suffix variables is to stylize the chat, tab list, and the name plate displayed above the player. I believe you will want to do some research about how to implement this, but as far as I know, the best way to implement this is using scoreboard teams for tab list and name plate. If it is not possible to have different values displayed in the tablist and nameplate, then you can consolidate these as nameplate and disregard the tablist thing.

Our plugin already controls chat formatting, so we should modify that code to account for the chat prefix and suffix.

# Commands

I want to use a system of commands inspired by pex, but here is my exact specification by example:

* /perm group Admin create - creates a group called Admin with cannonical name "admin" (gives error if this group already exists)
* /perm group Player default true - sets the default flag for group Player (cannonical name = "player") to true
* /perm group Player set worldedit.* true - adds the permission grant `worldedit.*, state = true` to the list of permission grants for group `Player` (cannonical name = "player").
* /perm group Player unset worldedit.* - removes any permission grant which is exactly `worldedit.*`
* /perm group Player list grants - lists all grants associated with a group
* /perm group Player priority 10 - sets the priority integer to 10 for the group
* /perm player Joey set worldedit.* true - adds the permission grant directly to a player
* /perm player Joey unset worldedit.* - removes the permission grant from the player
* /perm group Admin add Joey - adds the group `Admin` to the player Joey
* /perm group Admin remove Joey - removes the group `Admin` from the player Joey
* /perm player Joey tablist prefix &aGoat - sets the tablist_prefix for player Joey to "&aGoat" (should be interpreted as green "Goat")
* /perm group Admin nameplate prefix &cADMIN - sets the nameplate_prefix for group Admin to "&cADMIN" (should be interpreted as red "ADMIN")
* /perm player Joey inspect - gives a debug sheet with information about the player Joey
* /perm group Admin inspect - gives a debug sheet with information about the group Admin