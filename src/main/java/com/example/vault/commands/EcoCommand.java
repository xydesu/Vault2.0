package com.example.vault.commands;

import com.example.vault.VaultPlugin;
import com.example.vault.i18n.Messages;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class EcoCommand implements CommandExecutor {
    private final VaultPlugin plugin;
    private final Economy economy;
    private final Messages messages;

    public EcoCommand(VaultPlugin plugin, Economy economy, Messages messages) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vault.admin")) {
            sender.sendMessage(messages.get("error.no_permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /eco <give|take|set> <player> <amount>");
            return true;
        }

        String action = args[0].toLowerCase();
        String playerName = args[1];
        double amount;

        try {
            amount = Double.parseDouble(args[2]);
            if (amount < 0 && !action.equals("set")) {
                sender.sendMessage("§cAmount cannot be negative.");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount.");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        switch (action) {
            case "give":
                economy.depositPlayer(target, amount);
                sender.sendMessage("§aGave " + economy.format(amount) + " to " + target.getName() + ".");
                break;
            case "take":
                economy.withdrawPlayer(target, amount);
                sender.sendMessage("§aTook " + economy.format(amount) + " from " + target.getName() + ".");
                break;
            case "set":
                double current = economy.getBalance(target);
                if (amount > current) {
                    economy.depositPlayer(target, amount - current);
                } else if (amount < current) {
                    economy.withdrawPlayer(target, current - amount);
                }
                sender.sendMessage("§aSet " + target.getName() + "'s balance to " + economy.format(amount) + ".");
                break;
            default:
                sender.sendMessage("§cUsage: /eco <give|take|set> <player> <amount>");
                break;
        }

        return true;
    }
}
