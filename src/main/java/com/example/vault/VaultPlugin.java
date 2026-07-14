package com.example.vault;

import com.example.vault.commands.VaultCommand;
import com.example.vault.economy.SimpleEconomy;
import com.example.vault.i18n.Messages;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

public class VaultPlugin extends JavaPlugin implements Listener {
    private Economy economy;
    private Messages messages;
    private org.bukkit.scheduler.BukkitTask autosaveTask;
    private org.bukkit.scheduler.BukkitTask updateCheckTask;
    private volatile boolean updateAvailable = false;
    private volatile String remoteVersion = null;
    private volatile String lastAnnouncedVersion = null;
    private volatile long lastOnJoinUpdateCheckMs = 0L;
    private static final long ON_JOIN_CHECK_COOLDOWN_MS = 5 * 60 * 1000L; // 5 minutos
    private static final String UPDATE_LINK = "https://www.spigotmc.org/resources/vault-2-0-economy-plugins-%E2%9D%97updated-to-latest-versions%E2%9D%97.129605/";
    private static final String SPIGOT_UPDATE_URL = "https://api.spigotmc.org/legacy/update.php?resource=129605";

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

        // Load messages based on config language
        String lang = getConfig().getString("language", "en");
        messages = new Messages(this, lang);

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

        // Importar saldos de Essentials según config (una sola vez)
        if (getConfig().getBoolean("import.essentials.enabled", false)) {
            boolean replace = getConfig().getBoolean("import.essentials.replace", false);
            int mode = replace ? 1 : 0; // 0=merge, 1=replace
            new com.example.vault.importer.EssentialsImportService(this, provider).runOnce(mode);
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
            getCommand("vault").setExecutor(new VaultCommand(this, messages));
        }

        // Register listener for OP join notifications
        getServer().getPluginManager().registerEvents(this, this);

        // Schedule update check and run one immediately
        scheduleUpdateCheck();
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override public void run() { checkForUpdate(); }
        });

        getLogger().info(messages.get("plugin.enabled"));
    }

    public void reloadPluginState() {
        // Reload config and messages using current language
        reloadConfig();
        // Clean obsolete sections after reload
        migrateConfig();
        String lang = getConfig().getString("language", "en");
        messages.reload(lang);
        // Reschedule autosave with new config
        if (economy instanceof SimpleEconomy) {
            scheduleAutosave((SimpleEconomy) economy);
        }
        // Reschedule update check
        scheduleUpdateCheck();
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
        // Cancel update check
        if (updateCheckTask != null) {
            try { updateCheckTask.cancel(); } catch (Exception ignored) {}
            updateCheckTask = null;
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
        getLogger().info(messages.get("plugin.disabled"));
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

    private void scheduleUpdateCheck() {
        // Cancel previous if any
        if (updateCheckTask != null) {
            try { updateCheckTask.cancel(); } catch (Exception ignored) {}
            updateCheckTask = null;
        }
        long periodTicks = 20L * 60L * 30L; // cada 30 minutos
        updateCheckTask = getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override public void run() { checkForUpdate(); }
        }, periodTicks, periodTicks);
    }

    public void runUpdateCheckAndAnnounce(CommandSender requester) {
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override public void run() {
                String current = getDescription().getVersion();
                String remote = null;
                try {
                    java.net.URL url = new java.net.URL(SPIGOT_UPDATE_URL);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestProperty("User-Agent", "Vault2-UpdateChecker/1.0");
                    try (java.io.InputStream is = conn.getInputStream(); java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line).append('\n');
                        remote = sb.toString().trim();
                    }
                } catch (Exception ex) {
                    remote = null;
                }
                final String r = remote;
                final String cur = current;
                final boolean isUpdate = (r != null) && !equalsVersion(r, cur);
                updateAvailable = isUpdate;
                remoteVersion = r;
                if (isUpdate) {
                    lastAnnouncedVersion = r;
                }
                getServer().getScheduler().runTask(VaultPlugin.this, new Runnable() {
                    @Override public void run() {
                        if (r == null) {
                            boolean es = "es".equalsIgnoreCase(getConfig().getString("language", "en"));
                            if (requester != null) requester.sendMessage(es ? "§cNo se pudo comprobar actualizaciones (red/API)." : "§cCould not check for updates (network/API).");
                            getLogger().warning("Update check failed: network/API error");
                            return;
                        }
                        if (isUpdate) {
                            notifyOnlineOps(r);
                            getLogger().info("Update available: remote=" + normalizeVersion(r) + " current=" + normalizeVersion(cur));
                            if (requester != null) {
                                sendClickableUpdateMessage(requester, buildUpdateMessage(r));
                            }
                        } else {
                            String norm = normalizeVersion(r);
                            String lang = getConfig().getString("language", "en");
                            String ver;
                            if (norm != null && !norm.isEmpty()) {
                                ver = "v" + norm;
                            } else {
                                ver = switch (lang.toLowerCase(java.util.Locale.ROOT)) {
                                    case "es" -> "desconocida";
                                    case "fr" -> "inconnue";
                                    case "de" -> "unbekannt";
                                    default -> "unknown";
                                };
                            }
                            String msg = switch (lang.toLowerCase(java.util.Locale.ROOT)) {
                                case "es" -> "§aEstás en la última versión (" + ver + ").";
                                case "fr" -> "§aVous utilisez la dernière version (" + ver + ").";
                                case "de" -> "§aDu verwendest die neueste Version (" + ver + ").";
                                default -> "§aYou are on the latest version (" + ver + ").";
                            };
                            if (requester != null) requester.sendMessage(msg + " §b" + UPDATE_LINK);
                            getLogger().info("No update: remote=" + normalizeVersion(r) + " current=" + normalizeVersion(cur));
                        }
                    }
                });
            }
        });
    }

    private void checkForUpdate() {
        String current = getDescription().getVersion();
        try {
            java.net.URL url = new java.net.URL(SPIGOT_UPDATE_URL);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Vault2-UpdateChecker/1.0");
            try (java.io.InputStream is = conn.getInputStream(); java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                remoteVersion = sb.toString().trim();
                updateAvailable = !equalsVersion(remoteVersion, current);
                if (updateAvailable && (lastAnnouncedVersion == null || !lastAnnouncedVersion.equals(remoteVersion))) {
                    notifyOnlineOps(remoteVersion);
                    // También avisar en consola al iniciar, incluso si no hay operadores conectados
                    String normRemote = normalizeVersion(remoteVersion);
                    String normCurrent = normalizeVersion(current);
                    getLogger().info("Update available on startup: remote=" + normRemote + " current=" + normCurrent + " | " + UPDATE_LINK);
                    lastAnnouncedVersion = remoteVersion;
                }
            }
        } catch (Exception ex) {
            // Silently ignore network errors
        }
    }

    private String buildUpdateMessage(String version) {
        String lang = getConfig().getString("language", "en");
        String norm = (version != null ? normalizeVersion(version) : null);
        String ver;
        if (norm != null && !norm.isEmpty()) {
            ver = "v" + norm;
        } else {
            String lname = lang.toLowerCase(java.util.Locale.ROOT);
            if ("es".equals(lname)) {
                ver = "desconocida";
            } else if ("fr".equals(lname)) {
                ver = "inconnue";
            } else if ("de".equals(lname)) {
                ver = "unbekannt";
            } else {
                ver = "unknown";
            }
        }
        String lname = lang.toLowerCase(java.util.Locale.ROOT);
        if ("es".equals(lname)) {
            return "§eHay una nueva actualización de Vault 2.0 (" + ver + ") disponible. §bDescárgala: " + UPDATE_LINK;
        } else if ("fr".equals(lname)) {
            return "§eUne nouvelle mise à jour de Vault 2.0 (" + ver + ") est disponible. §bTélécharger : " + UPDATE_LINK;
        } else if ("de".equals(lname)) {
            return "§eEin neues Update für Vault 2.0 (" + ver + ") ist verfügbar. §bDownload: " + UPDATE_LINK;
        } else {
            return "§eA new Vault 2.0 update (" + ver + ") is available. §bDownload: " + UPDATE_LINK;
        }
    }

    private String normalizeVersion(String s) {
        if (s == null) return "";
        String input = s.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)v?\\d+(?:\\.\\d+)*").matcher(input);
        String candidate = null;
        while (m.find()) {
            candidate = m.group();
        }
        if (candidate == null) {
            return input.replaceFirst("(?i)^v", "");
        }
        return candidate.replaceFirst("(?i)^v", "");
    }

    private boolean equalsVersion(String a, String b) {
        String na = normalizeVersion(a);
        String nb = normalizeVersion(b);
        return !na.isEmpty() && na.equalsIgnoreCase(nb);
    }

    private void notifyOnlineOps(String newVersion) {
        String msg = buildUpdateMessage(newVersion);
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.isOp()) {
                sendClickableUpdateMessage(p, msg);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!p.isOp()) return;
        // Si ya sabemos que hay actualización, avisa de inmediato
        if (updateAvailable) {
            p.sendMessage(buildUpdateMessage(remoteVersion));
            return;
        }
        // Si no hay información remota aún, o ha pasado el cooldown, fuerza un chequeo
        long now = System.currentTimeMillis();
        if ((remoteVersion == null) || (now - lastOnJoinUpdateCheckMs >= ON_JOIN_CHECK_COOLDOWN_MS)) {
            lastOnJoinUpdateCheckMs = now;
            runUpdateCheckAndAnnounce(p);
        }
    }

    private void sendClickableUpdateMessage(org.bukkit.command.CommandSender sender, String message) {
        if (sender instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player p = (org.bukkit.entity.Player) sender;
            try {
                net.md_5.bungee.api.chat.TextComponent tc = new net.md_5.bungee.api.chat.TextComponent(message + " " + UPDATE_LINK);
                tc.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, UPDATE_LINK));
                String lang = getConfig().getString("language", "en");
                String hover = switch (lang.toLowerCase(java.util.Locale.ROOT)) {
                    case "es" -> "Abrir SpigotMC";
                    case "fr" -> "Ouvrir SpigotMC";
                    case "de" -> "SpigotMC öffnen";
                    default -> "Open SpigotMC";
                };
                tc.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder(hover).create()));
                p.spigot().sendMessage(tc);
            } catch (Throwable t) {
                sender.sendMessage(message + " " + UPDATE_LINK);
            }
        } else {
            sender.sendMessage(message + " " + UPDATE_LINK);
        }
    }
}
