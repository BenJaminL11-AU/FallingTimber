package io.github.benjaminl11au.fallingtimber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class ConfigMigrator {
    private static final int CURRENT_VERSION = 4;
    private static final DateTimeFormatter BACKUP_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ConfigMigrator() {
    }

    static void migrate(FallingTimberPlugin plugin) {
        Path config = plugin.getDataFolder().toPath().resolve("config.yml");
        int existingVersion = plugin.getConfig().getInt("config-version", 1);

        if (Files.exists(config) && existingVersion < CURRENT_VERSION) {
            Path backupDirectory = plugin.getDataFolder().toPath().resolve("config-backups");
            Path backup = backupDirectory.resolve("config-v" + existingVersion + "-"
                    + LocalDateTime.now().format(BACKUP_TIME) + ".yml");
            try {
                Files.createDirectories(backupDirectory);
                Files.copy(config, backup, StandardCopyOption.COPY_ATTRIBUTES);
                plugin.getLogger().info("Backed up the previous configuration to "
                        + backup.getFileName() + ".");
            } catch (IOException error) {
                plugin.getLogger().warning("Could not back up config.yml: " + error.getMessage());
            }
        }

        // v1.1 defaults were too small for some giant vanilla trees. Preserve
        // customised limits, but safely upgrade untouched legacy defaults.
        if (existingVersion < 2) {
            if (plugin.getConfig().getInt("detection.maximum-logs", 256) == 256) {
                plugin.getConfig().set("detection.maximum-logs", 512);
            }
            if (plugin.getConfig().getInt("detection.maximum-horizontal-radius", 12) == 12) {
                plugin.getConfig().set("detection.maximum-horizontal-radius", 20);
            }
            if (plugin.getConfig().getInt("detection.maximum-vertical-distance", 64) == 64) {
                plugin.getConfig().set("detection.maximum-vertical-distance", 128);
            }
        }

        // v1.3 promotes the previously opt-in decay feature to a normal part
        // of tree felling. It remains fully configurable and can be disabled.
        if (existingVersion < 3) {
            plugin.getConfig().set("leaf-decay.enabled", true);
        }

        // Restore the original join experience: show version information to
        // everyone even when the installed release is current. Administrators
        // can opt back into permission/update-only filtering in config.yml.
        if (existingVersion < 4) {
            plugin.getConfig().set("updates.notify-permission-required", false);
            plugin.getConfig().set("updates.only-when-update-available", false);
        }

        plugin.getConfig().options().copyDefaults(true);
        plugin.getConfig().set("config-version", CURRENT_VERSION);
        plugin.saveConfig();
        plugin.reloadConfig();
    }
}
