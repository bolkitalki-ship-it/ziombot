package pl.ziom.ziombot;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZiomBotPlugin extends JavaPlugin implements CommandExecutor {

    private BotClient botClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("ziom").setExecutor(this);

        botClient = new BotClient(this);

        if (getConfig().getBoolean("active", false)) {
            getLogger().info("Automatyczne uruchamianie ZiomBota zgodnie z konfiguracją...");
            botClient.start();
        }

        getLogger().info("ZiomBot 2.0.0 został pomyślnie uruchomiony!");
    }

    @Override
    public void onDisable() {
        if (botClient != null) {
            botClient.shutdown();
        }
        getLogger().info("ZiomBot został wyłączony.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ziombot.admin")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do tej komendy.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Użycie: /ziom <spawn|kill|reload>");
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "spawn":
                if (botClient.isRunning()) {
                    sender.sendMessage(ChatColor.YELLOW + "ZiomBot jest już uruchomiony!");
                } else {
                    botClient.start();
                    getConfig().set("active", true);
                    saveConfig();
                    sender.sendMessage(ChatColor.GREEN + "ZiomBot został uruchomiony.");
                }
                break;
            case "kill":
                if (!botClient.isRunning()) {
                    sender.sendMessage(ChatColor.YELLOW + "ZiomBot nie jest uruchomiony.");
                } else {
                    botClient.shutdown();
                    getConfig().set("active", false);
                    saveConfig();
                    sender.sendMessage(ChatColor.GREEN + "ZiomBot został zatrzymany.");
                }
                break;
            case "reload":
                reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Konfiguracja ZiomBota została przeładowana.");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Nieznana podkomenda. Użyj: /ziom <spawn|kill|reload>");
                break;
        }
        return true;
    }
}
