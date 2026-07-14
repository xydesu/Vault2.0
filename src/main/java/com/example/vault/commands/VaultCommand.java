package com.example.vault.commands;

import com.example.vault.VaultPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VaultCommand implements CommandExecutor {
    private final VaultPlugin plugin;
    public VaultCommand(VaultPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§8[§6Vault§8] §rUsage: /vault reload");
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            if (!sender.hasPermission("vault.admin")) {
                sender.sendMessage("§8[§6Vault§8] §cYou do not have permission.");
                return true;
            }
            plugin.reloadPluginState();
            sender.sendMessage("§8[§6Vault§8] §aPlugin configuration reloaded.");
            return true;
        }
        sender.sendMessage("§8[§6Vault§8] §rUsage: /vault reload");
        return true;
    }
}