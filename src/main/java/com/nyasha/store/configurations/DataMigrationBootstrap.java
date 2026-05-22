package com.nyasha.store.configurations;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class DataMigrationBootstrap implements ApplicationRunner {

    private final Flyway flyway;

    public DataMigrationBootstrap(Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public void run(ApplicationArguments args) {
        MigrationInfoService migrationInfo = flyway.info();
        MigrationInfo[] pending = safe(migrationInfo.pending());
        MigrationInfo[] all = safe(migrationInfo.all());

        MigrationInfo[] failed = Arrays.stream(all)
                .filter(Objects::nonNull)
                .filter(info -> info.getState() != null && info.getState().isFailed())
                .toArray(MigrationInfo[]::new);

        if (pending.length > 0) {
            throw new IllegalStateException("Pending Flyway migrations detected: "
                    + describe(pending)
                    + ". Resolve and rerun before serving traffic.");
        }
        if (failed.length > 0) {
            throw new IllegalStateException("Failed Flyway migrations detected: "
                    + describe(failed)
                    + ". Run Flyway repair/migrate before serving traffic.");
        }

        flyway.validate();
    }

    private MigrationInfo[] safe(MigrationInfo[] infos) {
        return infos == null ? new MigrationInfo[0] : infos;
    }

    private String describe(MigrationInfo[] infos) {
        return Arrays.stream(safe(infos))
                .filter(Objects::nonNull)
                .map(info -> info.getScript() + "[" + info.getState() + "]")
                .collect(Collectors.joining(", "));
    }
}
