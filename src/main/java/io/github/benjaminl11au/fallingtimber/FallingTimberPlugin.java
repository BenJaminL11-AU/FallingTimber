package io.github.benjaminl11au.fallingtimber;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class FallingTimberPlugin extends JavaPlugin {
    private static final LegacyComponentSerializer LEGACY_COLORS =
            LegacyComponentSerializer.legacyAmpersand();

    private TimberSettings settings;
    private TimberListener listener;
    private UpdateChecker updateChecker;
    private PlayerDataStore playerData;
    private BukkitTask dataSaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigMigrator.migrate(this);
        loadSettings();

        playerData = new PlayerDataStore(this);
        listener = new TimberListener(this, playerData);
        getServer().getPluginManager().registerEvents(listener, this);

        updateChecker = new UpdateChecker(this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        updateChecker.start();

        PluginCommand command = Objects.requireNonNull(
                getCommand("fallingtimber"),
                "fallingtimber command is missing from plugin.yml"
        );
        command.setExecutor(this);
        command.setTabCompleter(this);

        dataSaveTask = getServer().getScheduler().runTaskTimer(
                this, playerData::saveIfDirty, 600L, 600L);

        getLogger().info("FallingTimber " + getPluginMeta().getVersion()
                + " enabled for Paper 26.3.");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            listener.shutdown();
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
        if (dataSaveTask != null) {
            dataSaveTask.cancel();
        }
        if (playerData != null) {
            playerData.saveIfDirty();
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
            case "leaves" -> leaves(sender, args);
            case "global" -> global(sender, args);
            case "status" -> status(sender);
            case "debug" -> debug(sender);
            case "inspect" -> inspect(sender);
            case "stats" -> stats(sender, args);
            case "top" -> top(sender);
            case "version" -> updateChecker.sendStatus(sender);
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
        if (args.length == 2 && args[0].equalsIgnoreCase("stats")
                && sender.hasPermission("fallingtimber.stats.others")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("global")
                && sender.hasPermission("fallingtimber.global")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return List.of("on", "off", "toggle", "status", "timber", "leaves").stream()
                    .filter(choice -> choice.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("leaves")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return List.of("on", "off", "toggle", "status").stream()
                    .filter(choice -> choice.startsWith(input)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("global")
                && sender.hasPermission("fallingtimber.global")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            if (args[1].equalsIgnoreCase("leaves")) {
                return List.of("on", "off", "toggle", "status", "speed").stream()
                        .filter(choice -> choice.startsWith(input)).toList();
            }
            if (args[1].equalsIgnoreCase("timber")) {
                return List.of("on", "off", "toggle", "status").stream()
                        .filter(choice -> choice.startsWith(input)).toList();
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("global")
                && args[1].equalsIgnoreCase("leaves")
                && args[2].equalsIgnoreCase("speed")
                && sender.hasPermission("fallingtimber.global")) {
            String input = args[3].toLowerCase(Locale.ROOT);
            return List.of("slow", "normal", "fast", "very-fast", "instant").stream()
                    .filter(choice -> choice.startsWith(input)).toList();
        }
        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> choices = new ArrayList<>(List.of(
                "toggle", "leaves", "status", "debug", "stats", "top", "version", "help"));
        if (sender.hasPermission("fallingtimber.inspect")) {
            choices.add("inspect");
        }
        if (sender.hasPermission("fallingtimber.reload")) {
            choices.add("reload");
        }
        if (sender.hasPermission("fallingtimber.global")) {
            choices.add("global");
        }
        return choices.stream().filter(choice -> choice.startsWith(input)).toList();
    }

    private void toggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cOnly a player can toggle Timber for themselves.");
            return;
        }

        boolean enabled = listener.toggle(player);
        if (enabled && !settings.enabled()) {
            message(sender, "Your personal preference is now &aenabled&7, but "
                    + "FallingTimber is currently &cdisabled globally&7.");
        } else {
            message(sender, enabled
                    ? "Tree felling is now &aenabled&7 for you."
                    : "Tree felling is now &cdisabled&7 for you.");
        }
    }

    private void status(CommandSender sender) {
        if (sender instanceof Player player) {
            String global = settings.enabled() ? "&aenabled" : "&cdisabled";
            String personal = listener.isPersonallyEnabled(player) ? "&aenabled" : "&cdisabled";
            String effective = listener.isEnabledFor(player) ? "&aenabled" : "&cdisabled";
            String globalLeaves = settings.leafDecayEnabled() ? "&aenabled" : "&cdisabled";
            String personalLeaves = listener.isLeafDecayPersonallyEnabled(player)
                    ? "&aenabled" : "&cdisabled";
            String effectiveLeaves = listener.isLeafDecayEnabledFor(player)
                    ? "&aenabled" : "&cdisabled";
            String active = listener.hasActiveTask(player) ? " &8(&efelling a tree&8)" : "";
            message(sender, "Global: " + global + " &8| &7Personal: " + personal);
            message(sender, "Tree felling is " + effective + "&7 for you." + active);
            message(sender, "Fast leaves — Global: " + globalLeaves
                    + " &8| &7Personal: " + personalLeaves
                    + " &8| &7Effective: " + effectiveLeaves);
        } else {
            message(sender, settings.enabled()
                    ? "Tree felling is globally &aenabled&7."
                    : "Tree felling is globally &cdisabled&7.");
            message(sender, settings.leafDecayEnabled()
                    ? "Fast leaf decay is globally &aenabled&7 at &f" + currentLeafSpeed() + "&7 speed."
                    : "Fast leaf decay is globally &cdisabled&7.");
        }
    }

    private void leaves(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cUse /timber global leaves from the console.");
            return;
        }
        String option = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        boolean enabled;
        switch (option) {
            case "on" -> enabled = listener.setLeafDecayEnabled(player, true);
            case "off" -> enabled = listener.setLeafDecayEnabled(player, false);
            case "toggle" -> enabled = listener.toggleLeafDecay(player);
            case "status" -> {
                message(sender, listener.isLeafDecayEnabledFor(player)
                        ? "Fast leaf decay is &aenabled&7 for your trees."
                        : "Fast leaf decay is &cdisabled&7 for your trees.");
                return;
            }
            default -> {
                message(sender, "&cUse /timber leaves <on|off|toggle|status>.");
                return;
            }
        }
        if (enabled && !settings.leafDecayEnabled()) {
            message(sender, "Your fast-leaf preference is &aenabled&7, but leaf decay is "
                    + "currently &cdisabled globally&7.");
        } else {
            message(sender, enabled
                    ? "Fast leaf decay is now &aenabled&7 for trees you chop."
                    : "Fast leaf decay is now &cdisabled&7 for trees you chop.");
        }
    }

    private void global(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fallingtimber.global")) {
            message(sender, "&cYou do not have permission to change the global state.");
            return;
        }

        if (args.length > 1 && args[1].equalsIgnoreCase("leaves")) {
            globalLeaves(sender, args);
            return;
        }
        int optionIndex = args.length > 1 && args[1].equalsIgnoreCase("timber") ? 2 : 1;
        String option = args.length > optionIndex
                ? args[optionIndex].toLowerCase(Locale.ROOT) : "status";
        boolean enabled;
        switch (option) {
            case "on" -> enabled = true;
            case "off" -> enabled = false;
            case "toggle" -> enabled = !settings.enabled();
            case "status" -> {
                message(sender, settings.enabled()
                        ? "FallingTimber is globally &aenabled&7."
                        : "FallingTimber is globally &cdisabled&7.");
                return;
            }
            default -> {
                message(sender, "&cUse /timber global <on|off|toggle|status>.");
                return;
            }
        }

        getConfig().set("enabled", enabled);
        saveConfig();
        loadSettings();
        if (!enabled) {
            listener.cancelAllActiveTasks();
        }
        message(sender, enabled
                ? "FallingTimber is now globally &aenabled&7."
                : "FallingTimber is now globally &cdisabled&7.");
    }

    private void globalLeaves(CommandSender sender, String[] args) {
        String option = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";
        if (option.equals("speed")) {
            if (args.length < 4) {
                message(sender, "&cUse /timber global leaves speed "
                        + "<slow|normal|fast|very-fast|instant>.");
                return;
            }
            setLeafDecaySpeed(sender, args[3].toLowerCase(Locale.ROOT));
            return;
        }

        boolean enabled;
        switch (option) {
            case "on" -> enabled = true;
            case "off" -> enabled = false;
            case "toggle" -> enabled = !settings.leafDecayEnabled();
            case "status" -> {
                message(sender, settings.leafDecayEnabled()
                        ? "Fast leaf decay is globally &aenabled&7."
                        : "Fast leaf decay is globally &cdisabled&7.");
                message(sender, "Speed: &f" + currentLeafSpeed());
                return;
            }
            default -> {
                message(sender, "&cUse /timber global leaves "
                        + "<on|off|toggle|status|speed>.");
                return;
            }
        }
        getConfig().set("leaf-decay.enabled", enabled);
        saveAndReloadSettings();
        if (!enabled) {
            listener.cancelLeafDecayTasks();
        }
        message(sender, enabled
                ? "Fast leaf decay is now globally &aenabled&7."
                : "Fast leaf decay is now globally &cdisabled&7.");
    }

    private void setLeafDecaySpeed(CommandSender sender, String speed) {
        int delay;
        int blocks;
        int retry;
        switch (speed) {
            case "slow" -> { delay = 60; blocks = 4; retry = 20; }
            case "normal" -> { delay = 40; blocks = 12; retry = 10; }
            case "fast" -> { delay = 20; blocks = 24; retry = 5; }
            case "very-fast" -> { delay = 5; blocks = 48; retry = 2; }
            case "instant" -> { delay = 1; blocks = 64; retry = 1; }
            default -> {
                message(sender, "&cChoose slow, normal, fast, very-fast or instant.");
                return;
            }
        }
        getConfig().set("leaf-decay.delay-ticks", delay);
        getConfig().set("leaf-decay.blocks-per-tick", blocks);
        getConfig().set("leaf-decay.retry-delay-ticks", retry);
        saveAndReloadSettings();
        message(sender, "Fast leaf decay speed is now &a" + speed + "&7.");
    }

    private String currentLeafSpeed() {
        int blocks = settings.leafDecayBlocksPerTick();
        if (blocks <= 4) return "slow";
        if (blocks <= 12) return "normal";
        if (blocks <= 24) return "fast";
        if (blocks <= 48) return "very-fast";
        return "instant";
    }

    private void saveAndReloadSettings() {
        saveConfig();
        loadSettings();
    }

    private void debug(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cOnly a player can toggle diagnostic messages.");
            return;
        }
        boolean enabled = listener.toggleDebug(player);
        message(sender, enabled
                ? "Tree detection diagnostics are now &aenabled&7."
                : "Tree detection diagnostics are now &cdisabled&7.");
    }

    private void inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cOnly a player can inspect a tree.");
            return;
        }
        if (!sender.hasPermission("fallingtimber.inspect")) {
            message(sender, "&cYou do not have permission to inspect trees.");
            return;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null || !TreeScanner.isLog(target.getType())) {
            message(sender, "&eLook directly at a log within six blocks and try again.");
            return;
        }

        TreeScanner.ScanResult result = listener.inspect(target);
        message(sender, "&fTree inspection");
        message(sender, result.accepted() ? "Result: &aValid tree" : "Result: &cRejected");
        if (!result.accepted()) {
            message(sender, "Reason: &e" + result.rejectionReason());
        }
        message(sender, "Logs: &f" + result.detectedLogs()
                + " &8| &7Natural leaves: &f" + result.detectedLeaves());
        message(sender, "Log type: &f" + TreeScanner.treeName(target.getType()));
        message(sender, "Root ground: &f" + (result.rootMaterial() == null
                ? "Unknown" : friendlyName(result.rootMaterial().name())));
    }

    private void stats(CommandSender sender, String[] args) {
        if (!settings.statisticsEnabled()) {
            message(sender, "&eStatistics are disabled in config.yml.");
            return;
        }
        PlayerDataStore.Stats stats;
        if (args.length > 1) {
            if (!sender.hasPermission("fallingtimber.stats.others")) {
                message(sender, "&cYou do not have permission to view other players' statistics.");
                return;
            }
            stats = playerData.statsByName(args[1]);
        } else if (sender instanceof Player player) {
            stats = playerData.stats(player);
        } else {
            message(sender, "&eUse /timber stats <player> from the console.");
            return;
        }
        if (stats == null) {
            message(sender, "&cNo saved statistics were found for that player.");
            return;
        }
        message(sender, "&f" + stats.name() + "'s Timber statistics");
        message(sender, "Trees felled: &a" + stats.trees()
                + " &8| &7Logs chopped: &a" + stats.logs());
        message(sender, "Largest tree: &a" + stats.largest()
                + " logs &8| &7Favourite: &a" + stats.favouriteTree());
    }

    private void top(CommandSender sender) {
        if (!settings.statisticsEnabled()) {
            message(sender, "&eStatistics are disabled in config.yml.");
            return;
        }
        message(sender, "&fTop tree fellers");
        List<PlayerDataStore.Stats> leaders = playerData.leaderboard(5);
        if (leaders.isEmpty()) {
            message(sender, "No trees have been recorded yet.");
            return;
        }
        for (int index = 0; index < leaders.size(); index++) {
            PlayerDataStore.Stats entry = leaders.get(index);
            message(sender, "&e" + (index + 1) + ". &f" + entry.name()
                    + " &8- &a" + entry.trees() + " trees &7(" + entry.logs() + " logs)");
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("fallingtimber.reload")) {
            message(sender, "&cYou do not have permission to reload FallingTimber.");
            return;
        }

        reloadConfig();
        ConfigMigrator.migrate(this);
        loadSettings();
        updateChecker.start();
        message(sender, "Configuration reloaded.");
    }

    private void help(CommandSender sender, String label) {
        message(sender, "&f/" + label + " toggle &8- &7enable or disable Timber for yourself");
        message(sender, "&f/" + label
                + " leaves <on|off|toggle|status> &8- &7control fast leaves for your trees");
        message(sender, "&f/" + label + " status &8- &7show your current status");
        message(sender, "&f/" + label + " debug &8- &7toggle tree rejection diagnostics");
        message(sender, "&f/" + label + " stats &8- &7show your tree statistics");
        message(sender, "&f/" + label + " top &8- &7show the top tree fellers");
        message(sender, "&f/" + label + " version &8- &7show installed and latest versions");
        if (sender.hasPermission("fallingtimber.global")) {
            message(sender, "&f/" + label
                    + " global <on|off|toggle|status> &8- &7control the global state");
            message(sender, "&f/" + label
                    + " global leaves <on|off|toggle|status> &8- &7control global fast leaves");
            message(sender, "&f/" + label
                    + " global leaves speed <preset> &8- &7change the global decay speed");
        }
        if (sender.hasPermission("fallingtimber.inspect")) {
            message(sender, "&f/" + label + " inspect &8- &7inspect the targeted tree");
        }
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

    private static String friendlyName(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
