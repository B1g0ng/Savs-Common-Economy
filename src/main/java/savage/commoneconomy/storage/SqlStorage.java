package savage.commoneconomy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import savage.commoneconomy.EconomyManager.AccountData;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public abstract class SqlStorage implements EconomyStorage {
    protected HikariDataSource dataSource;
    protected final String tablePrefix;

    public SqlStorage(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    protected abstract void setupDataSource();

    protected void createTables() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + tablePrefix + "accounts (" +
                             "uuid VARCHAR(36) PRIMARY KEY, " +
                             "name VARCHAR(16) NOT NULL, " +
                             "balance DECIMAL(20, 2) NOT NULL" +
                             ")")) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void load() {
        setupDataSource();
        createTables();
    }

    @Override
    public void save() {
        // SQL databases save instantly, so this is a no-op or could be used for cleanup
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public BigDecimal getBalance(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT balance FROM " + tablePrefix + "accounts WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO; // Default balance should be handled by manager if account doesn't exist, but here we return 0
    }

    @Override
    public void setBalance(UUID uuid, BigDecimal amount) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE " + tablePrefix + "accounts SET balance = ? WHERE uuid = ?")) {
            stmt.setBigDecimal(1, amount);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean hasAccount(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM " + tablePrefix + "accounts WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void createAccount(UUID uuid, String name) {
        if (hasAccount(uuid)) {
            // Update name
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE " + tablePrefix + "accounts SET name = ? WHERE uuid = ?")) {
                stmt.setString(1, name);
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            // Insert new
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO " + tablePrefix + "accounts (uuid, name, balance) VALUES (?, ?, ?)")) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, name);
                stmt.setBigDecimal(3, BigDecimal.valueOf(1000)); // Default balance, should be passed from manager really
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public UUID getUUID(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM " + tablePrefix + "accounts WHERE LOWER(name) = LOWER(?)")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Collection<String> getOfflinePlayerNames() {
        List<String> names = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT name FROM " + tablePrefix + "accounts");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return names;
    }

    @Override
    public List<AccountData> getTopAccounts(int limit) {
        List<AccountData> accounts = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT name, balance FROM " + tablePrefix + "accounts ORDER BY balance DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    accounts.add(new AccountData(
                            rs.getString("name"),
                            rs.getBigDecimal("balance")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return accounts;
    }
}
