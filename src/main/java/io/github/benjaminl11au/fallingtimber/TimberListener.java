package io.github.benjaminl11au.fallingtimber;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class TimberListener implements Listener {
    private final FallingTimberPlugin plugin;
    private final Set<UUID> disabledPlayers = new HashSet<>();
    private final Set<UUID> syntheticBreakPlayers = new HashSet<>();
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();

    TimberListener(FallingTimberPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        TimberSettings settings = plugin.settings();

        if (syntheticBreakPlayers.contains(playerId)
                || activeTasks.containsKey(playerId)
                || disabledPlayers.contains(playerId)
                || !settings.enabled()
                || !player.hasPermission("fallingtimber.use")
                || !TreeScanner.isLog(event.getBlock().getType())
                || !isAxe(player.getInventory().getItemInMainHand().getType())) {
            return;
        }

        if (settings.sneakToBypass() && player.isSneaking()) {
            return;
        }
        if (!settings.allowCreative() && player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        TreeScanner.ScanResult result = TreeScanner.scan(event.getBlock(), settings);
        if (!result.accepted()) {
            return;
        }

        List<Block> additionalLogs = new ArrayList<>(result.logs());
        additionalLogs.removeIf(block -> sameBlock(block, event.getBlock()));
        if (additionalLogs.isEmpty()) {
            return;
        }

        startFelling(player, event.getBlock(), result.base(), additionalLogs, settings);
    }

    boolean toggle(Player player) {
        UUID playerId = player.getUniqueId();
        if (disabledPlayers.remove(playerId)) {
            return true;
        }
        disabledPlayers.add(playerId);
        cancelTask(playerId);
        return false;
    }

    boolean isEnabledFor(Player player) {
        return plugin.settings().enabled() && !disabledPlayers.contains(player.getUniqueId());
    }

    boolean hasActiveTask(Player player) {
        return activeTasks.containsKey(player.getUniqueId());
    }

    void shutdown() {
        for (BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
        disabledPlayers.clear();
        syntheticBreakPlayers.clear();
    }

    private void startFelling(
            Player player,
            Block originalBlock,
            Block baseBlock,
            List<Block> logs,
            TimberSettings settings
    ) {
        UUID playerId = player.getUniqueId();
        int heldSlot = player.getInventory().getHeldItemSlot();
        Material axeType = player.getInventory().getItemInMainHand().getType();
        Material logType = originalBlock.getType();
        Optional<Material> sapling = TreeScanner.saplingFor(logType);

        BukkitTask task = new BukkitRunnable() {
            private int index;
            private boolean firstTick = true;
            private boolean fullyFelled = true;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    finish(false);
                    return;
                }

                // If a later event listener cancelled the player's original
                // block break, leave the rest of the tree untouched as well.
                if (firstTick) {
                    firstTick = false;
                    if (TreeScanner.isLog(originalBlock.getType())) {
                        finish(false);
                        return;
                    }
                }

                if (player.getInventory().getHeldItemSlot() != heldSlot
                        || player.getInventory().getItemInMainHand().getType() != axeType) {
                    finish(false);
                    return;
                }

                int processedThisTick = 0;
                while (index < logs.size() && processedThisTick < settings.blocksPerTick()) {
                    Block block = logs.get(index++);
                    processedThisTick++;

                    if (!TreeScanner.isLog(block.getType())) {
                        continue;
                    }
                    if (settings.sameLogTypeOnly() && block.getType() != logType) {
                        fullyFelled = false;
                        continue;
                    }

                    if (settings.respectOtherPlugins() && !mayBreak(block, player)) {
                        fullyFelled = false;
                        continue;
                    }

                    ItemStack currentAxe = player.getInventory().getItemInMainHand();
                    if (!block.breakNaturally(currentAxe, true)) {
                        fullyFelled = false;
                        continue;
                    }

                    if (settings.damageAxe()) {
                        player.damageItemStack(EquipmentSlot.HAND, settings.damagePerExtraLog());
                    }

                    if (index < logs.size()
                            && player.getInventory().getItemInMainHand().getType() != axeType) {
                        finish(false);
                        return;
                    }
                }

                if (index >= logs.size()) {
                    finish(fullyFelled);
                }
            }

            private void finish(boolean completed) {
                cancel();
                activeTasks.remove(playerId);
                if (completed && settings.replantEnabled() && sapling.isPresent()) {
                    scheduleReplant(baseBlock, sapling.get(), settings.replantDelayTicks());
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        activeTasks.put(playerId, task);
    }

    private boolean mayBreak(Block block, Player player) {
        BlockBreakEvent checkEvent = new BlockBreakEvent(block, player);
        UUID playerId = player.getUniqueId();
        syntheticBreakPlayers.add(playerId);
        try {
            Bukkit.getPluginManager().callEvent(checkEvent);
        } finally {
            syntheticBreakPlayers.remove(playerId);
        }
        return !checkEvent.isCancelled();
    }

    private void scheduleReplant(Block baseBlock, Material sapling, int delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!baseBlock.getType().isAir()) {
                return;
            }

            BlockData saplingData = sapling.createBlockData();
            if (baseBlock.canPlace(saplingData)) {
                baseBlock.setBlockData(saplingData, true);
            }
        }, delayTicks);
    }

    private void cancelTask(UUID playerId) {
        BukkitTask task = activeTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private static boolean isAxe(Material material) {
        return material.name().endsWith("_AXE");
    }

    private static boolean sameBlock(Block first, Block second) {
        return first.getWorld().equals(second.getWorld())
                && first.getX() == second.getX()
                && first.getY() == second.getY()
                && first.getZ() == second.getZ();
    }
}
