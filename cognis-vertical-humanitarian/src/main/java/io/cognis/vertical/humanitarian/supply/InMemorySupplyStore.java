package io.cognis.vertical.humanitarian.supply;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link SupplyStore} implementation — suitable for tests and lightweight deployments.
 * For production, swap in a file-backed or database-backed implementation.
 */
public final class InMemorySupplyStore implements SupplyStore {
    private final Map<String, Consignment> store = new ConcurrentHashMap<>();

    @Override
    public void save(Consignment consignment) {
        store.put(consignment.id(), consignment);
    }

    @Override
    public Optional<Consignment> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Consignment> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Consignment> findByStatus(ConsignmentStatus status) {
        return store.values().stream()
            .filter(c -> c.status() == status)
            .toList();
    }
}
