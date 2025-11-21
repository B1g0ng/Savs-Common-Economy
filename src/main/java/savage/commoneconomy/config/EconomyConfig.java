package savage.commoneconomy.config;

import java.math.BigDecimal;

public class EconomyConfig {
    public BigDecimal defaultBalance = BigDecimal.valueOf(1000);
    public String currencySymbol = "$";
    public boolean symbolBeforeAmount = true;
    public boolean enableSellCommands = false;
    public boolean enableChestShops = true;
    
    public StorageConfig storage = new StorageConfig();

    public static class StorageConfig {
        public String type = "JSON"; // JSON, SQLITE, MYSQL, POSTGRESQL
        public String host = "localhost";
        public int port = 3306;
        public String database = "savs_economy";
        public String user = "root";
        public String password = "password";
        public String tablePrefix = "savs_eco_";
        
        // Connection Pool Settings
        public int poolSize = 10;
        public long connectionTimeout = 30000; // 30 seconds
        public long idleTimeout = 600000; // 10 minutes
    }
}
