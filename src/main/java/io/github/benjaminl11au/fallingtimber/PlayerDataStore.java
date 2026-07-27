package io.github.benjaminl11au.fallingtimber;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PlayerDataStore {
    private final FallingTimberPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerRecord> players = new HashMap<>();
    private boolean dirty;

    PlayerDataStore(FallingTimberPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-data.yml");
        load();
    }

    boolean isEnabled(Player player) {
        return !record(player).disabled;
    }

    boolean toggleEnabled(Player player) {
        PlayerRecord record = record(player);
        record.disabled = !record.disabled;
        dirty = true;
        return !record.disabled;
    }

    boolean isLeafDecayEnabled(Player player) {
        return !record(player).leafDecayDisabled;
    }

    boolean setLeafDecayEnabled(Player player, boolean enabled) {
        PlayerRecord record = record(player);
        record.leafDecayDisabled = !enabled;
        dirty = true;
        return enabled;
    }

    boolean toggleLeafDecay(Player player) {
        return setLeafDecayEnabled(player, !isLeafDecayEnabled(player));
    }

    boolean isDebugEnabled(Player player) {
        return record(player).debug;
    }

    boolean toggleDebug(Player player) {
        PlayerRecord record = record(player);
        record.debug = !record.debug;
        dirty = true;
        return record.debug;
    }

    void recordTree(Player player, MaterialSummary tree, int logs) {
        PlayerRecord record = record(player);
        record.trees++;
        record.logs += logs;
        record.largest = Math.max(record.largest, logs);
        record.treeTypes.merge(tree.displayName(), 1L, Long::sum);
        dirty = true;
    }

    Stats stats(Player player) {
        return record(player).stats();
    }

    Stats statsByName(String name) {
        return players.values().stream()
                .filter(record -> record.name.equalsIgnoreCase(name))
                .findFirst()
                .map(PlayerRecord::stats)
                .orElse(null);
    }

    List<Stats> leaderboard(int limit) {
        return players.values().stream()
                .map(PlayerRecord::stats)
                .filter(stats -> stats.trees() > 0)
                .sorted(Comparator.comparingLong(Stats::trees).reversed()
                        .thenComparing(Stats::name, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .toList();
    }

    void saveIfDirty() {
        if (!dirty) {
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerRecord> entry : players.entrySet()) {
            String path = "players." + entry.getKey();
            PlayerRecord record = entry.getValue();
            yaml.set(path + ".name", record.name);
            yaml.set(path + ".disabled", record.disabled);
            yaml.set(path + ".debug", record.debug);
            yaml.set(path + ".leaf-decay-disabled", record.leafDecayDisabled);
            yaml.set(path + ".statistics.trees", record.trees);
            yaml.set(path + ".statistics.logs", record.logs);
            yaml.set(path + ".statistics.largest-tree", record.largest);
            yaml.set(path + ".statistics.tree-types", record.treeTypes);
        }

        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException error) {
            plugin.getLogger().warning("Could not save player-data.yml: " + error.getMessage());
        }
    }

    private PlayerRecord record(Player player) {
        PlayerRecord record = players.computeIfAbsent(player.getUniqueId(),
                ignored -> new PlayerRecord(player.getName()));
        if (!record.name.equals(player.getName())) {
            record.name = player.getName();
            dirty = true;
        }
        return record;
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "players." + key;
                PlayerRecord record = new PlayerRecord(yaml.getString(path + ".name", key));
                record.disabled = yaml.getBoolean(path + ".disabled", false);
                record.debug = yaml.getBoolean(path + ".debug", false);
                record.leafDecayDisabled = yaml.getBoolean(path + ".leaf-decay-disabled", false);
                record.trees = yaml.getLong(path + ".statistics.trees", 0L);
                record.logs = yaml.getLong(path + ".statistics.logs", 0L);
                record.largest = yaml.getInt(path + ".statistics.largest-tree", 0);
                ConfigurationSection types = yaml.getConfigurationSection(
                        path + ".statistics.tree-types");
                if (types != null) {
                    for (String type : types.getKeys(false)) {
                        record.treeTypes.put(type, types.getLong(type));
                    }
                }
                players.put(uuid, record);
            } catch (IllegalArgumentException error) {
                plugin.getLogger().warning("Ignored invalid player UUID in player-data.yml: " + key);
            }
        }
    }

    record Stats(String name, long trees, long logs, int largest, String favouriteTree) {
    }

    record MaterialSummary(String displayName) {
    }

    private static final class PlayerRecord {
        private String name;
        private boolean disabled;
        private boolean debug;
        private boolean leafDecayDisabled;
        private long trees;
        private long logs;
        private int largest;
        private final Map<String, Long> treeTypes = new HashMap<>();

        private PlayerRecord(String name) {
            this.name = name;
        }

        private Stats stats() {
            String favourite = treeTypes.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("None yet");
            return new Stats(name, trees, logs, largest, favourite);
        }
    }
}
