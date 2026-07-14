package com.example.vault.importer;

import com.example.vault.VaultPlugin;
import com.example.vault.economy.SimpleEconomy;
import org.bukkit.Bukkit;

public class EssentialsImportService {
    private final VaultPlugin plugin;
    private final SimpleEconomy provider;

    public EssentialsImportService(VaultPlugin plugin, SimpleEconomy provider) {
        this.plugin = plugin;
        this.provider = provider;
    }

    public void runOnce(int mode) {
        if (!plugin.getConfig().getBoolean("import.essentials.enabled", false)) {
            return;
        }

        plugin.getLogger().info("Essentials import started (mode: " + mode + ")...");
        // For now, this is a stub. A full implementation would hook into Essentials' user files.
        // Once completed, disable the flag so it doesn't run again.
        
        plugin.getConfig().set("import.essentials.enabled", false);
        plugin.saveConfig();
        plugin.getLogger().info("Essentials import finished. The flag in config has been disabled.");
    }
}
