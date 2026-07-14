package com.example.vault;

import com.example.vault.commands.VaultCommand;
import com.example.vault.economy.SimpleEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
public class VaultPlugin extends JavaPlugin {
    private Economy economy;
    private org.bukkit.scheduler.BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        // Ensure data folder
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        // Save default config if not exists
        saveDefaultConfig();
        // Clean obsolete sections after defaults are written
        migrateConfig();

        // Language config is no longer used
        boolean useMySQL = getConfig().getBoolean("storage.use_mysql", false);
        com.example.vault.storage.Database db = null;

        // Create our internal Economy provider and register it in ServicesManager
        SimpleEconomy provider = new SimpleEconomy(this);
        if (useMySQL) {
            try {
                db = new com.example.vault.storage.Database(this);
                db.ensureSchema();
                java.util.Map<java.util.UUID, Double> loaded = db.loadAllBalances();
                provider.bulkSetBalances(loaded);
                provider.setDatabase(db);
                getLogger().info("Loaded " + loaded.size() + " balances from MySQL.");
            } catch (java.sql.SQLException ex) {
                getLogger().severe("Failed to initialize MySQL storage: " + ex.getMessage());
                // Fallback a archivo si falla
                provider.load();
            }
        } else {
            // Load persisted balances (file)
            provider.load();
        }

        this.economy = provider;
        getServer().getServicesManager().register(Economy.class, provider, this, ServicePriority.Highest);

        // Schedule autosave
        scheduleAutosave(provider);

        // Register PlaceholderAPI expansion if plugin is present
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.example.vault.placeholder.VaultPlaceholderExpansion(this, economy).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        if (getCommand("vault") != null) {
            getCommand("vault").setExecutor(new VaultCommand(this));
        }

        getLogger().info("Plugin enabled!");
    }

    public void reloadPluginState() {
        // Reload config
        reloadConfig();
        // Clean obsolete sections after reload
        migrateConfig();
        // Reschedule autosave with new config
        if (economy instanceof SimpleEconomy) {
            scheduleAutosave((SimpleEconomy) economy);
        }
    }

    private void migrateConfig() {
        org.bukkit.configuration.file.FileConfiguration cfg = getConfig();
        boolean changed = false;
        if (cfg.isConfigurationSection("permissions")) {
            cfg.set("permissions", null);
            changed = true;
        }
        // Ensure config reflects current plugin version
        String currentVersion = getDescription().getVersion();
        if (!currentVersion.equals(cfg.getString("plugin_version"))) {
            cfg.set("plugin_version", currentVersion);
            changed = true;
        }
        if (changed) {
            saveConfig();
            getLogger().info("Removed obsolete 'permissions' section from config.yml");
            getLogger().info("Synchronized 'plugin_version' in config.yml to " + currentVersion);
        }
    }

    @Override
    public void onDisable() {
        // Unregister our Economy service
        getServer().getServicesManager().unregister(Economy.class, economy);
        // Cancel autosave
        if (autosaveTask != null) {
            try { autosaveTask.cancel(); } catch (Exception ignored) {}
            autosaveTask = null;
        }
        // Persist balances on shutdown
        if (economy instanceof SimpleEconomy) {
            try {
                ((SimpleEconomy) economy).save();
            } catch (java.io.IOException ex) {
                getLogger().warning("Failed to save balances: " + ex.getMessage());
            }
            // Close SQL connection if used
            ((SimpleEconomy) economy).close();
        }
        getLogger().info("Plugin disabled!");
    }

    private void scheduleAutosave(SimpleEconomy provider) {
        // Cancel previous task if any
        if (autosaveTask != null) {
            try { autosaveTask.cancel(); } catch (Exception ignored) {}
            autosaveTask = null;
        }
        int seconds = getConfig().getInt("storage.autosave_seconds", 60);
        if (seconds <= 0) {
            return; // disabled
        }
        long ticks = 20L * seconds;
        autosaveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override public void run() {
                try {
                    provider.save();
                } catch (java.io.IOException ex) {
                    getLogger().warning("Autosave failed: " + ex.getMessage());
                }
            }
        }, ticks, ticks);
    }

}
