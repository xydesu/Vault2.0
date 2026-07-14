package com.example.vault.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.text.DecimalFormat;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.YamlConfiguration;
import com.example.vault.storage.Database;

public class SimpleEconomy implements Economy {
    private final Plugin plugin;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyBalances = ConcurrentHashMap.newKeySet();
    private Database database;
    private final DecimalFormat formatter = new DecimalFormat("#,##0.00");

    public SimpleEconomy(Plugin plugin) {
        this.plugin = plugin;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public void bulkSetBalances(Map<UUID, Double> loaded) {
        this.balances.putAll(loaded);
    }

    public void save() throws IOException {
        if (database != null) {
            if (!dirtyBalances.isEmpty()) {
                Map<UUID, Double> batch = new HashMap<>();
                for (UUID uuid : dirtyBalances) {
                    batch.put(uuid, balances.getOrDefault(uuid, 0.0));
                }
                dirtyBalances.clear();
                try {
                    database.saveBalancesBatch(batch);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to save balances batch: " + e.getMessage());
                    // Re-add to dirty set if save fails
                    dirtyBalances.addAll(batch.keySet());
                }
            }
        } else {
            // Local file save fallback
            File file = new File(plugin.getDataFolder(), "balances.yml");
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
                config.set(entry.getKey().toString(), entry.getValue());
            }
            config.save(file);
        }
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "balances.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), config.getDouble(key));
            } catch (Exception ignored) {}
        }
    }

    public void close() {
        if (database != null) {
            database.close();
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "VaultEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public java.util.List<String> getBanks() {
        return java.util.Collections.emptyList();
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return formatter.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return "dollars";
    }

    @Override
    public String currencyNameSingular() {
        return "dollar";
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return balances.containsKey(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (balances.putIfAbsent(player.getUniqueId(), 0.0) == null) {
            dirtyBalances.add(player.getUniqueId());
        }
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return balances.getOrDefault(player.getUniqueId(), 0.0);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        double bal = getBalance(player);
        if (bal < amount) {
            return new EconomyResponse(0.0, bal, ResponseType.FAILURE, "Insufficient funds");
        }
        balances.put(player.getUniqueId(), bal - amount);
        dirtyBalances.add(player.getUniqueId());
        return new EconomyResponse(amount, bal - amount, ResponseType.SUCCESS, "");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        double bal = getBalance(player);
        balances.put(player.getUniqueId(), bal + amount);
        dirtyBalances.add(player.getUniqueId());
        return new EconomyResponse(amount, bal + amount, ResponseType.SUCCESS, "");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public double getBalance(OfflinePlayer player, String worldName) {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    // -- Unused legacy methods (string-based variants) --
    @Override
    public boolean hasAccount(String playerName) { return false; }
    @Override
    public boolean createPlayerAccount(String playerName) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        if (p == null) return false;
        return createPlayerAccount(p);
    }
    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }
    @Override
    public double getBalance(String playerName) { return 0; }
    @Override
    public boolean has(String playerName, double amount) { return false; }
    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }

    // world-aware string variants (delegate to OfflinePlayer-based implementations)
    @Override
    public boolean hasAccount(String playerName, String worldName) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        return p != null && hasAccount(p);
    }
    @Override
    public double getBalance(String playerName, String worldName) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        return p != null ? getBalance(p) : 0.0;
    }
    @Override
    public boolean has(String playerName, String worldName, double amount) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        return p != null && has(p, amount);
    }
    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        if (p == null) return new EconomyResponse(0,0,ResponseType.FAILURE,"Player not found");
        return withdrawPlayer(p, amount);
    }
    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        OfflinePlayer p = com.example.vault.util.PlayerResolver.resolveByNameWithOfflineFallback(plugin, playerName);
        if (p == null) return new EconomyResponse(0,0,ResponseType.FAILURE,"Player not found");
        return depositPlayer(p, amount);
    }
    @Override
    public EconomyResponse bankBalance(String bank) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public EconomyResponse bankHas(String bank, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public EconomyResponse bankWithdraw(String bank, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public EconomyResponse bankDeposit(String bank, double amount) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public EconomyResponse isBankOwner(String bank, String playerName) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public EconomyResponse isBankMember(String bank, String playerName) { return new EconomyResponse(0,0,ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    @Override
    public net.milkbowl.vault.economy.EconomyResponse createBank(String bank, String player) {
        return new net.milkbowl.vault.economy.EconomyResponse(0, 0, net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }
    @Override
    public net.milkbowl.vault.economy.EconomyResponse createBank(String bank, org.bukkit.OfflinePlayer player) {
        return new net.milkbowl.vault.economy.EconomyResponse(0, 0, net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }
    @Override
    public net.milkbowl.vault.economy.EconomyResponse deleteBank(String bank) {
        return new net.milkbowl.vault.economy.EconomyResponse(0, 0, net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not implemented");
    }
    public EconomyResponse bankWithdraw(String bank, org.bukkit.OfflinePlayer player, double amount) { return new net.milkbowl.vault.economy.EconomyResponse(0,0,net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    public EconomyResponse bankDeposit(String bank, org.bukkit.OfflinePlayer player, double amount) { return new net.milkbowl.vault.economy.EconomyResponse(0,0,net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    public EconomyResponse isBankOwner(String bank, org.bukkit.OfflinePlayer player) { return new net.milkbowl.vault.economy.EconomyResponse(0,0,net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
    public EconomyResponse isBankMember(String bank, org.bukkit.OfflinePlayer player) { return new net.milkbowl.vault.economy.EconomyResponse(0,0,net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Not implemented"); }
}