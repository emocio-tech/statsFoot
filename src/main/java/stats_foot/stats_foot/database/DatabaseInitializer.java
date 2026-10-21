package stats_foot.stats_foot.database;

import java.nio.file.Path;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prepares the database at start-up: runs {@code db/schema.sql} (idempotent) and, when the
 * {@code player} table is empty, imports the data file in a single transaction.
 *
 * <p>As a {@link SmartInitializingSingleton} it runs after all singletons are created and
 * before the web server starts. It never throws an exception (fatal Errors are not caught): any failure is logged and stored in
 * {@link DatabaseStatus} so that the application starts anyway. A success clears any previous
 * failure.
 *
 * <p>The DataSource is always present in the application; the {@link ObjectProvider} only tolerates
 * its absence in reduced contexts (tests), where {@link #initialize()} then does nothing.
 *
 * <p>After the import transaction (and on every start-up, even when the database is already filled),
 * the identity columns are restarted by {@link DataFileImporter#restartIdentities()}.
 */
@Component
public class DatabaseInitializer implements SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final ObjectProvider<DataSource> dataSourceProvider;
    private final Path dataFile;
    private final DatabaseStatus status;

    @Autowired
    public DatabaseInitializer(ObjectProvider<DataSource> dataSourceProvider,
            @Value("${statsfoot.data-file:data/donneesStats.json}") Path dataFile, DatabaseStatus status) {
        this.dataSourceProvider = dataSourceProvider;
        this.dataFile = dataFile;
        this.status = status;
    }

    /** Direct constructor (tests, no Spring context). */
    public DatabaseInitializer(DataSource dataSource, Path dataFile, DatabaseStatus status) {
        this(new FixedProvider(dataSource), dataFile, status);
    }

    @Override
    public void afterSingletonsInstantiated() {
        initialize();
    }

    /** Runs the schema script and the import if needed; never throws. */
    public void initialize() {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            LOG.debug("No DataSource in the context: database initialization skipped.");
            return;
        }
        try {
            new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Integer players = jdbc.queryForObject("SELECT COUNT(*) FROM player", Integer.class);
            if (players != null && players == 0) {
                LOG.info("Empty database: importing the data file.");
                new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                        .executeWithoutResult(s -> new DataFileImporter(jdbc, dataFile).importFile());
                LOG.info("Data file imported.");
            }
            new DataFileImporter(jdbc, dataFile).restartIdentities();
            status.recordSuccess();
        } catch (Exception e) {
            LOG.error("Database initialization failed; player data will be unavailable.", e);
            status.recordFailure(e);
        }
    }

    /** Minimal provider returning a fixed DataSource. */
    private static final class FixedProvider implements ObjectProvider<DataSource> {

        private final DataSource dataSource;

        FixedProvider(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public DataSource getObject() {
            return dataSource;
        }

        @Override
        public DataSource getObject(Object... args) {
            return dataSource;
        }

        @Override
        public DataSource getIfAvailable() {
            return dataSource;
        }

        @Override
        public DataSource getIfUnique() {
            return dataSource;
        }
    }
}
