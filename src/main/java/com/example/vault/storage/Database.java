package com.example.vault.storage;

import com.example.vault.VaultPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Database {
    private final VaultPlugin plugin;
    private HikariDataSource dataSource;
    private final String tablePrefix;

    public Database(VaultPlugin plugin) throws SQLException {
        this.plugin = plugin;
        this.tablePrefix = plugin.getConfig().getString("storage.mysql.table_prefix", "vault_");
        
        HikariConfig config = new HikariConfig();
        String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        String dbName = plugin.getConfig().getString("storage.mysql.database", "minecraft");
        
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&autoReconnect=true");
        config.setUsername(plugin.getConfig().getString("storage.mysql.username", "root"));
        config.setPassword(plugin.getConfig().getString("storage.mysql.password", ""));
        
        config.setMaximumPoolSize(plugin.getConfig().getInt("storage.mysql.pool_size", 10));
        config.setMinimumIdle(plugin.getConfig().getInt("storage.mysql.minimum_idle", 2));
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        // Optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");

        this.dataSource = new HikariDataSource(config);
    }

    public void ensureSchema() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + tablePrefix + "balances (" +
                             "uuid VARCHAR(36) PRIMARY KEY, " +
                             "balance DOUBLE NOT NULL" +
                             ");")) {
            ps.executeUpdate();
        }
    }

    public Map<UUID, Double> loadAllBalances() throws SQLException {
        Map<UUID, Double> balances = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT uuid, balance FROM " + tablePrefix + "balances");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    double balance = rs.getDouble("balance");
                    balances.put(uuid, balance);
                } catch (IllegalArgumentException ignored) {
                    // Invalid UUID in db
                }
            }
        }
        return balances;
    }

    public void saveBalancesBatch(Map<UUID, Double> dirtyBalances) throws SQLException {
        if (dirtyBalances.isEmpty()) return;

        String query = "INSERT INTO " + tablePrefix + "balances (uuid, balance) VALUES (?, ?) " +
                       "ON DUPLICATE KEY UPDATE balance = VALUES(balance)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            
            conn.setAutoCommit(false);
            for (Map.Entry<UUID, Double> entry : dirtyBalances.entrySet()) {
                ps.setString(1, entry.getKey().toString());
                ps.setDouble(2, entry.getValue());
                ps.addBatch();
            }
            
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
