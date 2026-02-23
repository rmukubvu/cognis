package io.cognis.core.observability;

import java.io.IOException;
import java.util.List;

public interface AuditStore {
    List<AuditEvent> load() throws IOException;

    void save(List<AuditEvent> events) throws IOException;
}
