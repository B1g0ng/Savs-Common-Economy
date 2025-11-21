package savage.commoneconomy.storage;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import savage.commoneconomy.EconomyManager.AccountData;

public interface EconomyStorage {
    void load();
    void save();
    
    BigDecimal getBalance(UUID uuid);
    void setBalance(UUID uuid, BigDecimal amount);
    
    boolean hasAccount(UUID uuid);
    void createAccount(UUID uuid, String name);
    
    UUID getUUID(String name);
    Collection<String> getOfflinePlayerNames();
    List<AccountData> getTopAccounts(int limit);
}
