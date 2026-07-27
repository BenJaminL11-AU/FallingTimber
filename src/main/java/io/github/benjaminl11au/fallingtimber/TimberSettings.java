package io.github.benjaminl11au.fallingtimber;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public record TimberSettings(
        boolean enabled,
        boolean sneakToBypass,
        boolean allowCreative,
        boolean sameLogTypeOnly,
        boolean respectOtherPlugins,
        String worldMode,
        Set<String> worlds,
        int minimumLogs,
        int minimumLeaves,
        int leafSearchRadius,
        boolean requireNaturalLeaves,
        boolean requireRootedTree,
        boolean rejectFlatFormations,
        boolean rejectNearbyBuildingBlocks,
        int buildingBlockSearchRadius,
        int maximumLogs,
        int maximumHorizontalRadius,
        int maximumVerticalDistance,
        int blocksPerTick,
        boolean damageAxe,
        int damagePerExtraLog,
        double minimumTps,
        int cooldownSeconds,
        int maximumTreesPerMinute,
        int cancelDistance,
        boolean showProgress,
        boolean rejectionFeedback,
        boolean soundsEnabled,
        boolean particlesEnabled,
        Set<Material> allowedAxes,
        boolean preventAxeBreaking,
        String requiredEnchantment,
        String requiredCustomName,
        boolean replantEnabled,
        int replantDelayTicks,
        boolean replantTwoByTwo,
        boolean leafDecayEnabled,
        int leafDecayDelayTicks,
        int leafDecayBlocksPerTick,
        int leafDecayMaximumPasses,
        int leafDecayRetryDelayTicks,
        boolean statisticsEnabled,
        boolean updateChecksEnabled,
        boolean notifyUpdatesOnJoin,
        boolean updateNotifyPermissionRequired,
        boolean notifyOnlyWhenUpdateAvailable,
        int updateNotifyDelayTicks,
        int updateCheckIntervalHours,
        String githubRepository,
        String releasesUrl
) {
    public static TimberSettings from(FileConfiguration config) {
        return new TimberSettings(
                config.getBoolean("enabled", true),
                config.getBoolean("sneak-to-bypass", true),
                config.getBoolean("allow-creative", false),
                config.getBoolean("same-log-type-only", true),
                config.getBoolean("respect-other-plugins", true),
                config.getString("worlds.mode", "blacklist").toLowerCase(Locale.ROOT),
                lowerCaseSet(config.getStringList("worlds.list")),
                clamp(config.getInt("detection.minimum-logs", 3), 1, 64),
                clamp(config.getInt("detection.minimum-leaves", 4), 0, 256),
                clamp(config.getInt("detection.leaf-search-radius", 2), 1, 5),
                config.getBoolean("detection.require-natural-leaves", true),
                config.getBoolean("detection.require-rooted-tree", true),
                config.getBoolean("detection.reject-flat-formations", true),
                config.getBoolean("detection.reject-nearby-building-blocks", false),
                clamp(config.getInt("detection.building-block-search-radius", 1), 1, 3),
                clamp(config.getInt("detection.maximum-logs", 512), 1, 2048),
                clamp(config.getInt("detection.maximum-horizontal-radius", 20), 2, 64),
                clamp(config.getInt("detection.maximum-vertical-distance", 128), 4, 256),
                clamp(config.getInt("felling.blocks-per-tick", 8), 1, 64),
                config.getBoolean("felling.damage-axe", true),
                clamp(config.getInt("felling.damage-per-extra-log", 1), 1, 16),
                clamp(config.getDouble("safety.minimum-tps", 16.0), 0.0, 20.0),
                clamp(config.getInt("safety.cooldown-seconds", 1), 0, 3600),
                clamp(config.getInt("safety.maximum-trees-per-minute", 20), 0, 600),
                clamp(config.getInt("safety.cancel-distance", 32), 0, 256),
                config.getBoolean("effects.show-progress", true),
                config.getBoolean("feedback.show-rejection-reasons", false),
                config.getBoolean("effects.sounds", true),
                config.getBoolean("effects.particles", true),
                materialSet(config),
                config.getBoolean("tools.prevent-axe-breaking", true),
                config.getString("tools.required-enchantment", "").strip(),
                config.getString("tools.required-custom-name", "").strip(),
                config.getBoolean("replant.enabled", false),
                clamp(config.getInt("replant.delay-ticks", 40), 1, 1200),
                config.getBoolean("replant.support-two-by-two", true),
                config.getBoolean("leaf-decay.enabled", true),
                clamp(config.getInt("leaf-decay.delay-ticks", 40), 1, 1200),
                clamp(config.getInt("leaf-decay.blocks-per-tick", 12), 1, 64),
                clamp(config.getInt("leaf-decay.maximum-passes", 6), 1, 20),
                clamp(config.getInt("leaf-decay.retry-delay-ticks", 10), 1, 200),
                config.getBoolean("statistics.enabled", true),
                config.getBoolean("updates.enabled", true),
                config.getBoolean("updates.notify-on-join", true),
                config.getBoolean("updates.notify-permission-required", false),
                config.getBoolean("updates.only-when-update-available", false),
                clamp(config.getInt("updates.notify-delay-ticks", 60), 0, 1200),
                clamp(config.getInt("updates.check-interval-hours", 6), 1, 168),
                config.getString("updates.github-repository", "BenJaminL11-AU/FallingTimber"),
                config.getString("updates.releases-url",
                        "https://github.com/BenJaminL11-AU/FallingTimber/releases")
        );
    }

    boolean worldAllowed(String worldName) {
        boolean listed = worlds.contains(worldName.toLowerCase(Locale.ROOT));
        return worldMode.equals("whitelist") ? listed : !listed;
    }

    private static Set<String> lowerCaseSet(Iterable<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(result);
    }

    private static Set<Material> materialSet(FileConfiguration config) {
        Set<Material> result = new HashSet<>();
        for (String name : config.getStringList("tools.allowed-axes")) {
            Material material = Material.matchMaterial(name);
            if (material != null && material.name().endsWith("_AXE")) {
                result.add(material);
            }
        }
        if (result.isEmpty()) {
            for (Material material : Material.values()) {
                if (material.name().endsWith("_AXE")) {
                    result.add(material);
                }
            }
        }
        return Set.copyOf(result);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
