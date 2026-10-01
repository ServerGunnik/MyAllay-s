package dev.servergunnik.allymod.data;

import java.util.UUID;

public record Relation(UUID uuid, String lastKnownName, RelationKind kind, long updatedAtMillis) {
}
