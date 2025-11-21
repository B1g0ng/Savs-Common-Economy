package savage.commoneconomy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import savage.commoneconomy.config.EconomyConfig;
import savage.commoneconomy.config.WorthConfig;
import savage.commoneconomy.storage.EconomyStorage;
import savage.commoneconomy.storage.JsonStorage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {
    private static EconomyManager instance;
    private EconomyStorage storage;
    private final Gson gson;
    private EconomyConfig config;

    public EconomyConfig getConfig() {
        return config;
    }

    public static EconomyStorage getStorage() {
        return getInstance().storage;
    }

    public static EconomyManager getInstance() {
        if (instance == null) {
            instance = new EconomyManager();
        }
        return instance;
    }

    private EconomyManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadConfig();
        
        String type = config.storage.type.toUpperCase();
        switch (type) {
            case "MYSQL":
                storage = new savage.commoneconomy.storage.MysqlStorage(this, config.storage.host, config.storage.port, config.storage.database, config.storage.user, config.storage.password, config.storage.tablePrefix);
                break;
            case "SQLITE":
                storage = new savage.commoneconomy.storage.SqliteStorage(this, config.storage.tablePrefix);
                break;
            case "POSTGRES":
            case "POSTGRESQL":
                storage = new savage.commoneconomy.storage.PostgresStorage(this, config.storage.host, config.storage.port, config.storage.database, config.storage.user, config.storage.password, config.storage.tablePrefix);
                break;
            default:
                storage = new JsonStorage(this);
                break;
        }
    }

    private void loadConfig() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("savs-common-economy").resolve("config.json");
        File configFile = configPath.toFile();

        if (!configFile.exists()) {
            // Ensure directory exists
            configFile.getParentFile().mkdirs();
            
            this.config = new EconomyConfig();
            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(this.config, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try (FileReader reader = new FileReader(configFile)) {
                this.config = gson.fromJson(reader, EconomyConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
                this.config = new EconomyConfig();
            }
        }
    }

    public void load() {
        storage.load();
    }

    public void save() {
        storage.save();
    }

    public BigDecimal getBalance(UUID uuid) {
        return storage.getBalance(uuid);
    }

    public void setBalance(UUID uuid, BigDecimal amount) {
        storage.setBalance(uuid, amount);
    }

    public boolean addBalance(UUID uuid, BigDecimal amount) {
        int retries = 10;
        while (retries > 0) {
            // Reload account data to get latest version
            AccountData data = getAccountData(uuid);
            BigDecimal current = data != null ? data.balance : config.defaultBalance;
            long version = data != null ? data.version : 0;
            
            if (storage.setBalance(uuid, current.add(amount), version)) {
                return true;
            }
            retries--;
            try {
                Thread.sleep(10 + (long)(Math.random() * 10)); // Small random backoff
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return false; // Failed after retries
    }

    public boolean removeBalance(UUID uuid, BigDecimal amount) {
        int retries = 10;
        while (retries > 0) {
            // Reload account data to get latest version
            AccountData data = getAccountData(uuid);
            BigDecimal current = data != null ? data.balance : config.defaultBalance;
            long version = data != null ? data.version : 0;
            
            if (current.compareTo(amount) >= 0) {
                if (storage.setBalance(uuid, current.subtract(amount), version)) {
                    return true;
                }
            } else {
                return false; // Insufficient funds
            }
            retries--;
            try {
                Thread.sleep(10 + (long)(Math.random() * 10)); // Small random backoff
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return false; // Failed after retries
    }
    
    private AccountData getAccountData(UUID uuid) {
        // Helper to get raw account data including version
        // This requires exposing a way to get AccountData from storage or reloading
        // For now, we can just use getBalance but we need the version.
        // Let's add a getAccount method to storage interface or just rely on implementation details
        // Actually, we need to add getAccount to EconomyStorage interface to do this properly.
        // For now, let's implement a workaround or update interface.
        // Updating interface is better.
        return storage.getAccount(uuid);
    }

    public boolean hasAccount(UUID uuid) {
        return storage.hasAccount(uuid);
    }

    public void createAccount(UUID uuid, String name) {
        storage.createAccount(uuid, name);
    }

    public void resetBalance(UUID uuid) {
        setBalance(uuid, config.defaultBalance);
    }

    public UUID getUUID(String name) {
        return storage.getUUID(name);
    }

    public java.util.Collection<String> getOfflinePlayerNames() {
        return storage.getOfflinePlayerNames();
    }

    public String format(BigDecimal amount) {
        if (config.symbolBeforeAmount) {
            return config.currencySymbol + amount.toString();
        } else {
            return amount.toString() + config.currencySymbol;
        }
    }

    // Leaderboard support
    public java.util.List<AccountData> getTopAccounts(int limit) {
        return storage.getTopAccounts(limit);
    }

    // Sell system support
    private WorthConfig worthConfig;

    public boolean isSellEnabled() {
        return config != null && config.enableSellCommands;
    }

    public BigDecimal getItemPrice(String itemId) {
        if (worthConfig == null) {
            loadWorthConfig();
        }
        return worthConfig.itemPrices.getOrDefault(itemId, BigDecimal.ZERO);
    }

    public Map<String, BigDecimal> getAllItemPrices() {
        if (worthConfig == null) {
            loadWorthConfig();
        }
        return new HashMap<>(worthConfig.itemPrices);
    }

    private void loadWorthConfig() {
        Path worthPath = FabricLoader.getInstance().getConfigDir().resolve("savs-common-economy").resolve("worth.json");
        File worthFile = worthPath.toFile();

        if (!worthFile.exists()) {
            this.worthConfig = new WorthConfig();
            try (FileWriter writer = new FileWriter(worthFile)) {
                gson.toJson(this.worthConfig, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try (FileReader reader = new FileReader(worthFile)) {
                this.worthConfig = gson.fromJson(reader, WorthConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
                this.worthConfig = new WorthConfig();
            }
        }
    }

    public static class AccountData {
        public String name;
        public BigDecimal balance;
        public long version;

        public AccountData(String name, BigDecimal balance) {
            this(name, balance, 0);
        }

        public AccountData(String name, BigDecimal balance, long version) {
            this.name = name;
            this.balance = balance;
            this.version = version;
        }
    }
}
