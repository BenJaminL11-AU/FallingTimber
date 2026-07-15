package io.github.benjaminl11au.fallingtimber;

import org.bukkit.configuration.file.FileConfiguration;

public record TimberSettings(
        boolean enabled,
        boolean sneakToBypass,
        boolean allowCreative,
        boolean sameLogTypeOnly,
        boolean respectOtherPlugins,
        int minimumLogs,
        int minimumLeaves,
        int leafSearchRadius,
        int maximumLogs,
        int maximumHorizontalRadius,
        int maximumVerticalDistance,
        int blocksPerTick,
        boolean damageAxe,
        int damagePerExtraLog,
        boolean replantEnabled,
        int replantDelayTicks
) {
    public static TimberSettings from(FileConfiguration config) {
        return new TimberSettings(
                config.getBoolean("enabled", true),
                config.getBoolean("sneak-to-bypass", true),
                config.getBoolean("allow-creative", false),
                config.getBoolean("same-log-type-only", true),
                config.getBoolean("respect-other-plugins", true),
                clamp(config.getInt("detection.minimum-logs", 3), 1, 64),
                clamp(config.getInt("detection.minimum-leaves", 4), 0, 256),
                clamp(config.getInt("detection.leaf-search-radius", 2), 1, 4),
                clamp(config.getInt("detection.maximum-logs", 256), 1, 2048),
                clamp(config.getInt("detection.maximum-horizontal-radius", 12), 2, 64),
                clamp(config.getInt("detection.maximum-vertical-distance", 64), 4, 256),
                clamp(config.getInt("felling.blocks-per-tick", 8), 1, 64),
                config.getBoolean("felling.damage-axe", true),
                clamp(config.getInt("felling.damage-per-extra-log", 1), 1, 16),
                config.getBoolean("replant.enabled", false),
                clamp(config.getInt("replant.delay-ticks", 20), 1, 1200)
        );
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
