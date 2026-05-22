package com.nyasha.store.configurations;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataMigrationBootstrapTest {

    @Mock
    private Flyway flyway;

    @Mock
    private MigrationInfoService infoService;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void runValidatesWhenNoPendingOrFailedMigrations() throws Exception {
        MigrationInfo applied = migration(MigrationState.SUCCESS);

        when(flyway.info()).thenReturn(infoService);
        when(infoService.pending()).thenReturn(new MigrationInfo[0]);
        when(infoService.all()).thenReturn(new MigrationInfo[]{applied});

        DataMigrationBootstrap bootstrap = new DataMigrationBootstrap(flyway);
        bootstrap.run(applicationArguments);

        verify(flyway).validate();
    }

    @Test
    void runFailsWhenPendingMigrationsExist() {
        MigrationInfo pending = migration(MigrationState.PENDING);
        MigrationInfo applied = migration(MigrationState.SUCCESS);

        when(flyway.info()).thenReturn(infoService);
        when(infoService.pending()).thenReturn(new MigrationInfo[]{pending});
        when(infoService.all()).thenReturn(new MigrationInfo[]{applied});

        DataMigrationBootstrap bootstrap = new DataMigrationBootstrap(flyway);

        assertThatThrownBy(() -> bootstrap.run(applicationArguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pending Flyway migrations detected");
    }

    @Test
    void runFailsWhenFailedMigrationsExist() {
        MigrationInfo applied = migration(MigrationState.SUCCESS);
        MigrationInfo failed = migrationWithScript("V2__broken.sql", MigrationState.FAILED);

        when(flyway.info()).thenReturn(infoService);
        when(infoService.pending()).thenReturn(new MigrationInfo[0]);
        when(infoService.all()).thenReturn(new MigrationInfo[]{applied, failed});

        DataMigrationBootstrap bootstrap = new DataMigrationBootstrap(flyway);

        assertThatThrownBy(() -> bootstrap.run(applicationArguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed Flyway migrations detected")
                .hasMessageContaining("V2__broken.sql");
    }

    private MigrationInfo migration(MigrationState state) {
        MigrationInfo info = mock(MigrationInfo.class);
        when(info.getState()).thenReturn(state);
        return info;
    }

    private MigrationInfo migrationWithScript(String script, MigrationState state) {
        MigrationInfo info = migration(state);
        when(info.getScript()).thenReturn(script);
        return info;
    }
}
