package io.cognis.vertical.humanitarian.supply;

import java.util.List;
import java.util.Optional;

public interface SupplyStore {
    void save(Consignment consignment);
    Optional<Consignment> findById(String id);
    List<Consignment> findAll();
    List<Consignment> findByStatus(ConsignmentStatus status);
}
