package io.github.benjaminl11au.fallingtimber;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class FallingTimberPlugin extends JavaPlugin {
    private static final LegacyComponentSerializer LEGACY_COLORS =
            LegacyComponentSerializer.legacyAmpersand();

    private TimberSettings settings;
    private TimberListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        listener = new TimberListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        PluginCommand command = Objects.requireNonNull(
                getCommand("fallingtimber"),
                "fallingtimber command is missing from plugin.yml"
        );
        command.setExecutor(this);
        command.setTabCompleter(this);

        getLogger().info("FallingTimber 1.0.0 enabled for Paper 26.2.");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            listener.shutdown();
        }
    }

    TimberSettings settings() {
        return settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        switch (subcommand) {
            case "toggle" -> toggle(sender);
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            case "help" -> help(sender, label);
            default -> {
                message(sender, "&cUnknown option. Use /" + label + " help.");
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> choices = new ArrayList<>(List.of("toggle", "status", "help"));
        if (sender.hasPermission("fallingtimber.reload")) {
            choices.add("reload");
        }
        return choices.stream().filter(choice -> choice.startsWith(input)).toList();
    }

    private void toggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cOnly a player can toggle Timber for themselves.");
            return;
        }

        boolean enabled = listener.toggle(player);
        message(sender, enabled
                ? "Tree felling is now &aenabled&7 for you."
                : "Tree felling is now &cdisabled&7 for you.");
    }

    private void status(CommandSender sender) {
        if (sender instanceof Player player) {
            String enabled = listener.isEnabledFor(player) ? "&aenabled" : "&cdisabled";
            String active = listener.hasActiveTask(player) ? " &8(&efelling a tree&8)" : "";
            message(sender, "Tree felling is " + enabled + "&7 for you." + active);
        } else {
            message(sender, settings.enabled()
                    ? "Tree felling is globally &aenabled&7."
                    : "Tree felling is globally &cdisabled&7.");
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("fallingtimber.reload")) {
            message(sender, "&cYou do not have permission to reload FallingTimber.");
            return;
        }

        reloadConfig();
        loadSettings();
        message(sender, "Configuration reloaded.");
    }

    private void help(CommandSender sender, String label) {
        message(sender, "&f/" + label + " toggle &8- &7enable or disable Timber for yourself");
        message(sender, "&f/" + label + " status &8- &7show your current status");
        if (sender.hasPermission("fallingtimber.reload")) {
            message(sender, "&f/" + label + " reload &8- &7reload config.yml");
        }
        if (settings.sneakToBypass()) {
            message(sender, "Hold &fSneak &7while chopping to break only one log.");
        }
    }

    private void loadSettings() {
        settings = TimberSettings.from(getConfig());
    }

    private void message(CommandSender sender, String text) {
        String prefix = getConfig().getString("messages.prefix", "&8[&2FallingTimber&8] &7");
        Component message = LEGACY_COLORS.deserialize(prefix + text);
        sender.sendMessage(message);
    }
}
