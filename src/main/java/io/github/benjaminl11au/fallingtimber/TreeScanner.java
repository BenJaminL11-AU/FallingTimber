package io.github.benjaminl11au.fallingtimber;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

final class TreeScanner {
    private TreeScanner() {
    }

    static ScanResult scan(Block origin, TimberSettings settings) {
        if (!isLog(origin.getType())) {
            return ScanResult.rejected("not-a-log");
        }

        World world = origin.getWorld();
        Material originType = origin.getType();
        BlockPosition originPosition = BlockPosition.of(origin);
        Queue<BlockPosition> pending = new ArrayDeque<>();
        Set<BlockPosition> found = new HashSet<>();

        pending.add(originPosition);
        found.add(originPosition);

        while (!pending.isEmpty()) {
            BlockPosition current = pending.remove();

            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }

                        BlockPosition next = current.offset(dx, dy, dz);
                        if (found.contains(next) || !withinLimits(next, originPosition, settings)) {
                            continue;
                        }
                        if (!world.isChunkLoaded(next.x() >> 4, next.z() >> 4)) {
                            continue;
                        }

                        Material nextType = world.getBlockAt(next.x(), next.y(), next.z()).getType();
                        if (!isLog(nextType)) {
                            continue;
                        }
                        if (settings.sameLogTypeOnly() && nextType != originType) {
                            continue;
                        }

                        found.add(next);
                        if (found.size() > settings.maximumLogs()) {
                            return ScanResult.rejected("too-many-logs");
                        }
                        pending.add(next);
                    }
                }
            }
        }

        if (found.size() < settings.minimumLogs()) {
            return ScanResult.rejected("too-few-logs");
        }

        if (!hasEnoughLeaves(world, found, settings)) {
            return ScanResult.rejected("too-few-leaves");
        }

        List<Block> logs = new ArrayList<>(found.size());
        for (BlockPosition position : found) {
            logs.add(world.getBlockAt(position.x(), position.y(), position.z()));
        }

        logs.sort(Comparator
                .comparingInt(Block::getY).reversed()
                .thenComparingInt(block -> horizontalDistanceSquared(block, origin)));

        Block base = logs.stream()
                .min(Comparator
                        .comparingInt(Block::getY)
                        .thenComparingInt(block -> horizontalDistanceSquared(block, origin)))
                .orElse(origin);

        return ScanResult.accepted(logs, base);
    }

    static boolean isLog(Material material) {
        return Tag.LOGS.isTagged(material);
    }

    static Optional<Material> saplingFor(Material logMaterial) {
        String name = logMaterial.name();
        if (name.startsWith("STRIPPED_")) {
            name = name.substring("STRIPPED_".length());
        }

        String woodName;
        if (name.endsWith("_LOG")) {
            woodName = name.substring(0, name.length() - "_LOG".length());
        } else if (name.endsWith("_WOOD")) {
            woodName = name.substring(0, name.length() - "_WOOD".length());
        } else {
            return Optional.empty();
        }

        String plantName = woodName.equals("MANGROVE")
                ? "MANGROVE_PROPAGULE"
                : woodName + "_SAPLING";
        Material plant = Material.matchMaterial(plantName);
        return plant == null ? Optional.empty() : Optional.of(plant);
    }

    private static boolean withinLimits(
            BlockPosition candidate,
            BlockPosition origin,
            TimberSettings settings
    ) {
        int dx = Math.abs(candidate.x() - origin.x());
        int dz = Math.abs(candidate.z() - origin.z());
        int dy = Math.abs(candidate.y() - origin.y());
        return dx <= settings.maximumHorizontalRadius()
                && dz <= settings.maximumHorizontalRadius()
                && dy <= settings.maximumVerticalDistance();
    }

    private static boolean hasEnoughLeaves(
            World world,
            Set<BlockPosition> logs,
            TimberSettings settings
    ) {
        if (settings.minimumLeaves() == 0) {
            return true;
        }

        int radius = settings.leafSearchRadius();
        Set<BlockPosition> leaves = new HashSet<>();

        for (BlockPosition log : logs) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPosition position = log.offset(dx, dy, dz);
                        if (leaves.contains(position)
                                || !world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
                            continue;
                        }

                        if (isLeafLike(world.getBlockAt(position.x(), position.y(), position.z()).getType())) {
                            leaves.add(position);
                            if (leaves.size() >= settings.minimumLeaves()) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean isLeafLike(Material material) {
        if (Tag.LEAVES.isTagged(material)) {
            return true;
        }

        // Nether fungi use wart blocks instead of ordinary leaves.
        String name = material.name();
        return name.equals("NETHER_WART_BLOCK") || name.equals("WARPED_WART_BLOCK");
    }

    private static int horizontalDistanceSquared(Block first, Block second) {
        int dx = first.getX() - second.getX();
        int dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    record ScanResult(boolean accepted, List<Block> logs, Block base, String rejectionReason) {
        static ScanResult accepted(List<Block> logs, Block base) {
            return new ScanResult(true, List.copyOf(logs), base, "");
        }

        static ScanResult rejected(String reason) {
            return new ScanResult(false, List.of(), null, reason);
        }
    }

    private record BlockPosition(int x, int y, int z) {
        static BlockPosition of(Block block) {
            return new BlockPosition(block.getX(), block.getY(), block.getZ());
        }

        BlockPosition offset(int dx, int dy, int dz) {
            return new BlockPosition(x + dx, y + dy, z + dz);
        }
    }
}
