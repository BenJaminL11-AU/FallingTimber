package io.github.benjaminl11au.fallingtimber;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

final class TreeScanner {
    private TreeScanner() {
    }

    static ScanResult scan(Block origin, TimberSettings settings) {
        if (!isLog(origin.getType())) {
            return ScanResult.rejected("not-a-log", 0, 0, null);
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
                        if (found.contains(next)
                                || !world.isChunkLoaded(next.x() >> 4, next.z() >> 4)) {
                            continue;
                        }

                        Material nextType = world.getBlockAt(next.x(), next.y(), next.z()).getType();
                        if (!isLog(nextType)
                                || (settings.sameLogTypeOnly() && nextType != originType)) {
                            continue;
                        }
                        if (!withinLimits(next, originPosition, settings)) {
                            return ScanResult.rejected("tree-outside-limits",
                                    found.size() + 1, 0, null);
                        }

                        found.add(next);
                        if (found.size() > settings.maximumLogs()) {
                            return ScanResult.rejected("too-many-logs", found.size(), 0, null);
                        }
                        pending.add(next);
                    }
                }
            }
        }

        RootInfo roots = findRoots(world, found);
        Set<BlockPosition> leafPositions = findLeaves(world, found, settings);
        Material rootMaterial = roots.blocks.isEmpty()
                ? null
                : roots.blocks.getFirst().getRelative(0, -1, 0).getType();

        if (found.size() < settings.minimumLogs()) {
            return ScanResult.rejected("too-few-logs", found.size(), leafPositions.size(), rootMaterial);
        }
        if (settings.requireRootedTree() && !roots.validGround) {
            return ScanResult.rejected("not-rooted", found.size(), leafPositions.size(), rootMaterial);
        }
        if (leafPositions.size() < settings.minimumLeaves()) {
            return ScanResult.rejected("too-few-natural-leaves", found.size(),
                    leafPositions.size(), rootMaterial);
        }
        if (settings.rejectFlatFormations() && isSuspiciouslyFlat(found)) {
            return ScanResult.rejected("flat-log-formation", found.size(),
                    leafPositions.size(), rootMaterial);
        }
        if (settings.rejectNearbyBuildingBlocks()
                && touchesBuildingBlock(world, found, settings.buildingBlockSearchRadius())) {
            return ScanResult.rejected("touching-building-blocks", found.size(),
                    leafPositions.size(), rootMaterial);
        }

        List<Block> logs = blocks(world, found);
        logs.sort(Comparator.comparingInt(Block::getY).reversed()
                .thenComparingInt(block -> horizontalDistanceSquared(block, origin)));
        List<Block> leaves = blocks(world, leafPositions);
        leaves.sort(Comparator.comparingInt(Block::getY).reversed());
        return ScanResult.accepted(logs, leaves, roots.blocks, rootMaterial);
    }

    static boolean isLog(Material material) {
        return Tag.LOGS.isTagged(material);
    }

    static String treeName(Material logMaterial) {
        String name = logMaterial.name().replace("STRIPPED_", "")
                .replace("_LOG", "").replace("_WOOD", "")
                .replace("_STEM", "").replace("_HYPHAE", "");
        StringBuilder result = new StringBuilder();
        for (String word : name.split("_")) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }

    static Optional<Material> saplingFor(Material logMaterial) {
        String name = logMaterial.name().replaceFirst("^STRIPPED_", "");
        String woodName;
        if (name.endsWith("_LOG")) {
            woodName = name.substring(0, name.length() - 4);
        } else if (name.endsWith("_WOOD")) {
            woodName = name.substring(0, name.length() - 5);
        } else {
            return Optional.empty();
        }
        Material plant = Material.matchMaterial(woodName.equals("MANGROVE")
                ? "MANGROVE_PROPAGULE" : woodName + "_SAPLING");
        return Optional.ofNullable(plant);
    }

    private static Set<BlockPosition> findLeaves(
            World world, Set<BlockPosition> logs, TimberSettings settings
    ) {
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
                        Block block = world.getBlockAt(position.x(), position.y(), position.z());
                        if (isQualifyingLeaf(block, settings.requireNaturalLeaves())) {
                            leaves.add(position);
                        }
                    }
                }
            }
        }
        return leaves;
    }

    private static RootInfo findRoots(World world, Set<BlockPosition> logs) {
        int lowestY = logs.stream().mapToInt(BlockPosition::y).min().orElse(Integer.MIN_VALUE);
        List<Block> roots = new ArrayList<>();
        boolean valid = false;
        for (BlockPosition log : logs) {
            if (log.y() == lowestY) {
                Block block = world.getBlockAt(log.x(), log.y(), log.z());
                roots.add(block);
                valid |= isTreeGround(block.getRelative(0, -1, 0).getType());
            }
        }
        roots.sort(Comparator.comparingInt(Block::getX).thenComparingInt(Block::getZ));
        return new RootInfo(List.copyOf(roots), valid);
    }

    private static boolean isSuspiciouslyFlat(Set<BlockPosition> logs) {
        int minX = logs.stream().mapToInt(BlockPosition::x).min().orElse(0);
        int maxX = logs.stream().mapToInt(BlockPosition::x).max().orElse(0);
        int minY = logs.stream().mapToInt(BlockPosition::y).min().orElse(0);
        int maxY = logs.stream().mapToInt(BlockPosition::y).max().orElse(0);
        int minZ = logs.stream().mapToInt(BlockPosition::z).min().orElse(0);
        int maxZ = logs.stream().mapToInt(BlockPosition::z).max().orElse(0);
        int height = maxY - minY + 1;
        int width = Math.max(maxX - minX + 1, maxZ - minZ + 1);
        return height < 3 || (width >= 6 && width > height * 2);
    }

    private static boolean touchesBuildingBlock(World world, Set<BlockPosition> logs, int radius) {
        for (BlockPosition log : logs) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        Material material = world.getBlockAt(
                                log.x() + dx, log.y() + dy, log.z() + dz).getType();
                        if (isBuildingMarker(material)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isBuildingMarker(Material material) {
        String name = material.name();
        return name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR")
                || name.endsWith("_BED") || name.endsWith("_SIGN")
                || name.endsWith("_WALL_SIGN") || name.endsWith("_HANGING_SIGN")
                || name.endsWith("_HEAD") || name.endsWith("_SKULL")
                || name.equals("CHEST") || name.equals("TRAPPED_CHEST")
                || name.equals("BARREL") || name.equals("FURNACE")
                || name.equals("BLAST_FURNACE") || name.equals("SMOKER")
                || name.equals("CRAFTING_TABLE") || name.equals("LECTERN");
    }

    private static boolean withinLimits(
            BlockPosition candidate, BlockPosition origin, TimberSettings settings
    ) {
        return Math.abs(candidate.x() - origin.x()) <= settings.maximumHorizontalRadius()
                && Math.abs(candidate.z() - origin.z()) <= settings.maximumHorizontalRadius()
                && Math.abs(candidate.y() - origin.y()) <= settings.maximumVerticalDistance();
    }

    private static boolean isTreeGround(Material material) {
        String name = material.name();
        return Tag.DIRT.isTagged(material) || name.equals("MUD")
                || name.equals("MUDDY_MANGROVE_ROOTS") || name.equals("MOSS_BLOCK")
                || name.equals("PALE_MOSS_BLOCK") || name.equals("CRIMSON_NYLIUM")
                || name.equals("WARPED_NYLIUM");
    }

    private static boolean isQualifyingLeaf(Block block, boolean requireNaturalLeaves) {
        BlockData data = block.getBlockData();
        if (data instanceof Leaves leaves) {
            return !requireNaturalLeaves || !leaves.isPersistent();
        }
        String name = block.getType().name();
        return !requireNaturalLeaves
                && (name.equals("NETHER_WART_BLOCK") || name.equals("WARPED_WART_BLOCK"));
    }

    private static List<Block> blocks(World world, Set<BlockPosition> positions) {
        List<Block> result = new ArrayList<>(positions.size());
        for (BlockPosition position : positions) {
            result.add(world.getBlockAt(position.x(), position.y(), position.z()));
        }
        return result;
    }

    private static int horizontalDistanceSquared(Block first, Block second) {
        int dx = first.getX() - second.getX();
        int dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    record ScanResult(
            boolean accepted,
            List<Block> logs,
            List<Block> leaves,
            List<Block> roots,
            Material rootMaterial,
            String rejectionReason,
            int detectedLogs,
            int detectedLeaves
    ) {
        static ScanResult accepted(
                List<Block> logs, List<Block> leaves, List<Block> roots, Material rootMaterial
        ) {
            return new ScanResult(true, List.copyOf(logs), List.copyOf(leaves),
                    List.copyOf(roots), rootMaterial, "", logs.size(), leaves.size());
        }

        static ScanResult rejected(String reason, int logs, int leaves, Material rootMaterial) {
            return new ScanResult(false, List.of(), List.of(), List.of(), rootMaterial,
                    reason, logs, leaves);
        }

        Block base() {
            return roots.isEmpty() ? null : roots.getFirst();
        }
    }

    private record RootInfo(List<Block> blocks, boolean validGround) {
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
