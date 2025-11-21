package savage.commoneconomy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class PostgresStorage extends SqlStorage {
    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;

    public PostgresStorage(savage.commoneconomy.EconomyManager manager, String host, int port, String database, String user, String password, String tablePrefix) {
        super(manager, tablePrefix);
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
    }

    @Override
    protected void setupDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Apply pool settings
        config.setMaximumPoolSize(manager.getConfig().storage.poolSize);
        config.setConnectionTimeout(manager.getConfig().storage.connectionTimeout);
        config.setIdleTimeout(manager.getConfig().storage.idleTimeout);
        
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    protected String getTransactionsTableCreationSql() {
        return "CREATE TABLE IF NOT EXISTS " + tablePrefix + "transactions (" +
                "id SERIAL PRIMARY KEY, " +
                "timestamp BIGINT NOT NULL, " +
                "source VARCHAR(16) NOT NULL, " +
                "target VARCHAR(16) NOT NULL, " +
                "amount DECIMAL(20, 2) NOT NULL, " +
                "type VARCHAR(16) NOT NULL, " +
                "details VARCHAR(255)" +
                ")";
    }
}
