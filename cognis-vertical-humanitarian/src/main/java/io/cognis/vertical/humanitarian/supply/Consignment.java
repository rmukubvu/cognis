package io.cognis.vertical.humanitarian.supply;

import java.time.Instant;

public record Consignment(
    String id,
    ConsignmentStatus status,
    String location,
    String recipientPhone,
    Instant createdAt,
    Instant updatedAt
) {
    public Consignment withStatus(ConsignmentStatus newStatus, String newLocation) {
        return new Consignment(id, newStatus, newLocation, recipientPhone, createdAt, Instant.now());
    }
}
