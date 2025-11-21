package savage.commoneconomy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class MysqlStorage extends SqlStorage {
    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;

    public MysqlStorage(savage.commoneconomy.EconomyManager manager, String host, int port, String database, String user, String password, String tablePrefix) {
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
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        // Apply pool settings
        config.setMaximumPoolSize(manager.getConfig().storage.poolSize);
        config.setConnectionTimeout(manager.getConfig().storage.connectionTimeout);
        config.setIdleTimeout(manager.getConfig().storage.idleTimeout);

        this.dataSource = new HikariDataSource(config);
    }
    @Override
    protected String getTransactionsTableCreationSql() {
        return "CREATE TABLE IF NOT EXISTS " + tablePrefix + "transactions (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "timestamp BIGINT NOT NULL, " +
                "source VARCHAR(255) NOT NULL, " +
                "target VARCHAR(255) NOT NULL, " +
                "amount DECIMAL(20, 2) NOT NULL, " +
                "type VARCHAR(32) NOT NULL, " +
                "details TEXT" +
                ")";
    }
}
