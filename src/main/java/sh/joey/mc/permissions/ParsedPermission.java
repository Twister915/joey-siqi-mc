package sh.joey.mc.permissions;

import java.util.List;

public record ParsedPermission(List<PermissionToken> tokens) {

    public sealed interface PermissionToken permits PermLiteral, PermDot, PermWildcard {}

    public record PermLiteral(String literal) implements PermissionToken {}
    public record PermDot() implements PermissionToken {}
    public record PermWildcard() implements PermissionToken {}

}
