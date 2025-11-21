package savage.commoneconomy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import org.sqlite.SQLiteConfig;
import java.io.File;

public class SqliteStorage extends SqlStorage {

    private File databaseFile; // Added declaration for databaseFile

    public SqliteStorage(savage.commoneconomy.EconomyManager manager, String tablePrefix) {
        super(manager, tablePrefix);
        // Initialize databaseFile here, similar to how dbPath was initialized
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("savs-common-economy");
        configDir.toFile().mkdirs();
        this.databaseFile = configDir.resolve("economy_data.sqlite").toFile();
    }

    @Override
    protected void setupDataSource() {
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        hikariConfig.setDataSourceClassName("org.sqlite.SQLiteDataSource");
        hikariConfig.addDataSourceProperty("url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        
        // Apply pool settings
        hikariConfig.setMaximumPoolSize(manager.getConfig().storage.poolSize);
        hikariConfig.setConnectionTimeout(manager.getConfig().storage.connectionTimeout);
        hikariConfig.setIdleTimeout(manager.getConfig().storage.idleTimeout);
        
        dataSource = new HikariDataSource(hikariConfig);
    }

    @Override
    protected String getTransactionsTableCreationSql() {
        return "CREATE TABLE IF NOT EXISTS " + tablePrefix + "transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "timestamp BIGINT NOT NULL, " +
                "source VARCHAR(16) NOT NULL, " +
                "target VARCHAR(16) NOT NULL, " +
                "amount DECIMAL(20, 2) NOT NULL, " +
                "type VARCHAR(16) NOT NULL, " +
                "details VARCHAR(255)" +
                ")";
    }
}
