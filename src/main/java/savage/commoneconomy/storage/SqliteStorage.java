package savage.commoneconomy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class SqliteStorage extends SqlStorage {

    public SqliteStorage(String tablePrefix) {
        super(tablePrefix);
    }

    @Override
    protected void setupDataSource() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("savs-common-economy");
        configDir.toFile().mkdirs();
        Path dbPath = configDir.resolve("economy_data.sqlite");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath.toString());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(10);
        
        this.dataSource = new HikariDataSource(config);
    }
}
