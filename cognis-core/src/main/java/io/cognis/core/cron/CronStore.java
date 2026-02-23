package io.cognis.core.cron;

import java.io.IOException;
import java.util.List;

public interface CronStore {
    List<CronJob> load() throws IOException;

    void save(List<CronJob> jobs) throws IOException;
}
