package io.github.benjaminl11au.fallingtimber;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class TimberListener implements Listener {
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    private final FallingTimberPlugin plugin;
    private final PlayerDataStore playerData;
    private final Set<UUID> syntheticBreakPlayers = new HashSet<>();
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
    private final Set<String> lockedLogs = new HashSet<>();
    private final Map<UUID, List<String>> playerLocks = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Deque<Long>> recentTrees = new HashMap<>();
    private final Set<BukkitRunnable> leafDecayTasks = new HashSet<>();

    TimberListener(FallingTimberPlugin plugin, PlayerDataStore playerData) {
        this.plugin = plugin;
        this.playerData = playerData;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        TimberSettings settings = plugin.settings();
        ItemStack axe = player.getInventory().getItemInMainHand();

        if (syntheticBreakPlayers.contains(playerId) || activeTasks.containsKey(playerId)
                || !settings.enabled() || !playerData.isEnabled(player)
                || !player.hasPermission("fallingtimber.use")
                || !TreeScanner.isLog(event.getBlock().getType())) {
            return;
        }
        if (!settings.worldAllowed(player.getWorld().getName())) {
            reject(player, "FallingTimber is disabled in this world.");
            return;
        }
        if (!settings.allowedAxes().contains(axe.getType()) || !matchesToolRequirements(axe, settings)) {
            reject(player, "This axe does not meet the configured tool requirements.");
            return;
        }
        if (settings.sneakToBypass() && player.isSneaking()) {
            return;
        }
        if (!settings.allowCreative() && player.getGameMode() == GameMode.CREATIVE) {
            reject(player, "FallingTimber is disabled in Creative mode.");
            return;
        }
        if (settings.minimumTps() > 0 && Bukkit.getTPS()[0] < settings.minimumTps()) {
            reject(player, "The server TPS is too low to fell a whole tree safely.");
            return;
        }
        if (isCoolingDown(playerId, settings.cooldownSeconds())) {
            reject(player, "FallingTimber is cooling down.");
            return;
        }
        if (rateLimitReached(playerId, settings.maximumTreesPerMinute())) {
            reject(player, "You have reached the tree-felling rate limit.");
            return;
        }

        TreeScanner.ScanResult result = TreeScanner.scan(event.getBlock(), settings);
        if (!result.accepted()) {
            reject(player, rejectionMessage(result.rejectionReason()));
            return;
        }
        if (result.logs().stream().anyMatch(block -> lockedLogs.contains(blockKey(block)))) {
            reject(player, "Another player is already felling this tree.");
            return;
        }
        if (settings.preventAxeBreaking() && !axeCanSurvive(axe, result.logs().size(), settings)) {
            reject(player, "Your axe does not have enough durability for this tree.");
            return;
        }

        List<Block> additionalLogs = new ArrayList<>(result.logs());
        additionalLogs.removeIf(block -> sameBlock(block, event.getBlock()));
        if (additionalLogs.isEmpty()) {
            return;
        }

        List<String> locks = result.logs().stream().map(TimberListener::blockKey).toList();
        lockedLogs.addAll(locks);
        playerLocks.put(playerId, locks);
        rememberTree(playerId, settings);
        startFelling(player, event.getBlock(), result, additionalLogs, settings);
    }

    boolean toggle(Player player) {
        boolean enabled = playerData.toggleEnabled(player);
        if (!enabled) {
            cancelTask(player.getUniqueId());
        }
        return enabled;
    }

    boolean toggleDebug(Player player) {
        return playerData.toggleDebug(player);
    }

    boolean isDebugEnabled(Player player) {
        return playerData.isDebugEnabled(player);
    }

    boolean isEnabledFor(Player player) {
        return plugin.settings().enabled() && playerData.isEnabled(player);
    }

    boolean isPersonallyEnabled(Player player) {
        return playerData.isEnabled(player);
    }

    boolean isLeafDecayEnabledFor(Player player) {
        return plugin.settings().leafDecayEnabled() && playerData.isLeafDecayEnabled(player);
    }

    boolean isLeafDecayPersonallyEnabled(Player player) {
        return playerData.isLeafDecayEnabled(player);
    }

    boolean setLeafDecayEnabled(Player player, boolean enabled) {
        return playerData.setLeafDecayEnabled(player, enabled);
    }

    boolean toggleLeafDecay(Player player) {
        return playerData.toggleLeafDecay(player);
    }

    boolean hasActiveTask(Player player) {
        return activeTasks.containsKey(player.getUniqueId());
    }

    TreeScanner.ScanResult inspect(Block block) {
        return TreeScanner.scan(block, plugin.settings());
    }

    void shutdown() {
        cancelAllActiveTasks();
        cancelLeafDecayTasks();
        lockedLogs.clear();
        playerLocks.clear();
        syntheticBreakPlayers.clear();
        playerData.saveIfDirty();
    }

    void cancelAllActiveTasks() {
        for (UUID playerId : List.copyOf(activeTasks.keySet())) {
            cancelTask(playerId);
        }
    }

    void cancelLeafDecayTasks() {
        for (BukkitRunnable task : List.copyOf(leafDecayTasks)) {
            task.cancel();
        }
        leafDecayTasks.clear();
    }

    private void startFelling(
            Player player,
            Block originalBlock,
            TreeScanner.ScanResult tree,
            List<Block> logs,
            TimberSettings settings
    ) {
        UUID playerId = player.getUniqueId();
        int heldSlot = player.getInventory().getHeldItemSlot();
        Material axeType = player.getInventory().getItemInMainHand().getType();
        Material logType = originalBlock.getType();
        Optional<Material> sapling = TreeScanner.saplingFor(logType);
        int totalLogs = logs.size() + 1;

        BukkitTask task = new BukkitRunnable() {
            private int index;
            private boolean firstTick = true;
            private boolean fullyFelled = true;

            @Override
            public void run() {
                if (!player.isOnline() || !player.getWorld().equals(originalBlock.getWorld())
                        || tooFarAway(player, originalBlock, settings.cancelDistance())) {
                    finish(false);
                    return;
                }
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

                int processed = 0;
                while (index < logs.size() && processed++ < settings.blocksPerTick()) {
                    Block block = logs.get(index++);
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

                    BlockData brokenData = block.getBlockData();
                    ItemStack currentAxe = player.getInventory().getItemInMainHand();
                    if (!block.breakNaturally(currentAxe, true)) {
                        fullyFelled = false;
                        continue;
                    }
                    if (settings.damageAxe()) {
                        player.damageItemStack(EquipmentSlot.HAND, settings.damagePerExtraLog());
                    }
                    playEffects(block, brokenData, settings);
                }

                if (settings.showProgress()) {
                    int completed = Math.min(totalLogs, index + 1);
                    player.sendActionBar(Component.text("Felling " + TreeScanner.treeName(logType)
                            + " tree… " + completed + "/" + totalLogs, NamedTextColor.GREEN));
                }
                if (index >= logs.size()) {
                    finish(fullyFelled);
                }
            }

            private void finish(boolean completed) {
                cancel();
                activeTasks.remove(playerId);
                releaseLocks(playerId);
                if (!completed) {
                    return;
                }

                if (settings.statisticsEnabled()) {
                    playerData.recordTree(player,
                            new PlayerDataStore.MaterialSummary(TreeScanner.treeName(logType)), totalLogs);
                }
                if (settings.replantEnabled() && sapling.isPresent()) {
                    scheduleReplant(tree.roots(), sapling.get(), settings);
                }
                TimberSettings currentSettings = plugin.settings();
                if (currentSettings.leafDecayEnabled() && playerData.isLeafDecayEnabled(player)) {
                    scheduleLeafDecay(tree.leaves(), currentSettings);
                }
                if (settings.showProgress()) {
                    player.sendActionBar(Component.text("Tree felled! " + totalLogs + " logs",
                            NamedTextColor.GREEN));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        activeTasks.put(playerId, task);
    }

    private void scheduleLeafDecay(List<Block> leaves, TimberSettings settings) {
        BukkitRunnable decayTask = new BukkitRunnable() {
            private int index;
            private int pass = 1;
            private int retryDelay;

            @Override
            public void run() {
                if (retryDelay > 0) {
                    retryDelay--;
                    return;
                }

                int processed = 0;
                while (index < leaves.size() && processed++ < settings.leafDecayBlocksPerTick()) {
                    Block leaf = leaves.get(index++);
                    if (!leaf.getWorld().isChunkLoaded(leaf.getX() >> 4, leaf.getZ() >> 4)) {
                        continue;
                    }
                    if (leaf.getBlockData() instanceof Leaves leafData
                            && !leafData.isPersistent()
                            && leafData.getDistance() >= leafData.getMaximumDistance()) {
                        leaf.breakNaturally();
                    }
                }
                if (index >= leaves.size()) {
                    if (pass >= settings.leafDecayMaximumPasses()
                            || leaves.stream().noneMatch(TimberListener::isLoadedLeaf)) {
                        cancel();
                        leafDecayTasks.remove(this);
                        return;
                    }
                    pass++;
                    index = 0;
                    retryDelay = settings.leafDecayRetryDelayTicks();
                }
            }
        };
        leafDecayTasks.add(decayTask);
        decayTask.runTaskTimer(plugin, settings.leafDecayDelayTicks(), 1L);
    }

    private static boolean isLoadedLeaf(Block block) {
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                && block.getBlockData() instanceof Leaves;
    }

    private void scheduleReplant(List<Block> roots, Material sapling, TimberSettings settings) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<Block> targets = roots;
            if (!settings.replantTwoByTwo() || !isTwoByTwo(roots)) {
                targets = roots.isEmpty() ? List.of() : List.of(roots.getFirst());
            }
            BlockData saplingData = sapling.createBlockData();
            for (Block target : targets) {
                if (target.getType().isAir() && target.canPlace(saplingData)) {
                    target.setBlockData(saplingData.clone(), true);
                }
            }
        }, settings.replantDelayTicks());
    }

    private boolean mayBreak(Block block, Player player) {
        BlockBreakEvent check = new BlockBreakEvent(block, player);
        UUID playerId = player.getUniqueId();
        syntheticBreakPlayers.add(playerId);
        try {
            Bukkit.getPluginManager().callEvent(check);
        } finally {
            syntheticBreakPlayers.remove(playerId);
        }
        return !check.isCancelled();
    }

    private void reject(Player player, String reason) {
        if (plugin.settings().rejectionFeedback() || playerData.isDebugEnabled(player)) {
            player.sendActionBar(Component.text(reason, NamedTextColor.YELLOW));
        }
    }

    @SuppressWarnings("deprecation") // Bukkit's registry bridge remains the compatible lookup API.
    private static boolean matchesToolRequirements(ItemStack axe, TimberSettings settings) {
        if (!settings.requiredEnchantment().isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(settings.requiredEnchantment());
            Enchantment enchantment = key == null ? null : Registry.ENCHANTMENT.get(key);
            if (enchantment == null || !axe.containsEnchantment(enchantment)) {
                return false;
            }
        }
        if (!settings.requiredCustomName().isBlank()) {
            ItemMeta meta = axe.getItemMeta();
            if (meta == null || meta.customName() == null
                    || !PLAIN.serialize(meta.customName()).equalsIgnoreCase(settings.requiredCustomName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean axeCanSurvive(ItemStack axe, int logs, TimberSettings settings) {
        ItemMeta meta = axe.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return true;
        }
        int remaining = axe.getType().getMaxDurability() - damageable.getDamage();
        int estimatedDamage = 1 + (settings.damageAxe()
                ? Math.max(0, logs - 1) * settings.damagePerExtraLog() : 0);
        return remaining > estimatedDamage;
    }

    private static void playEffects(Block block, BlockData data, TimberSettings settings) {
        if (settings.particlesEnabled()) {
            block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5),
                    5, 0.25, 0.25, 0.25, data);
        }
        if (settings.soundsEnabled()) {
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.35f, 0.9f);
        }
    }

    private boolean isCoolingDown(UUID playerId, int seconds) {
        if (seconds <= 0) {
            return false;
        }
        return cooldowns.getOrDefault(playerId, 0L) > System.currentTimeMillis();
    }

    private boolean rateLimitReached(UUID playerId, int maximum) {
        if (maximum <= 0) {
            return false;
        }
        Deque<Long> times = recentTrees.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        long cutoff = System.currentTimeMillis() - 60_000L;
        while (!times.isEmpty() && times.peekFirst() < cutoff) {
            times.removeFirst();
        }
        return times.size() >= maximum;
    }

    private void rememberTree(UUID playerId, TimberSettings settings) {
        long now = System.currentTimeMillis();
        cooldowns.put(playerId, now + settings.cooldownSeconds() * 1000L);
        recentTrees.computeIfAbsent(playerId, ignored -> new ArrayDeque<>()).addLast(now);
    }

    private void cancelTask(UUID playerId) {
        BukkitTask task = activeTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        releaseLocks(playerId);
    }

    private void releaseLocks(UUID playerId) {
        List<String> locks = playerLocks.remove(playerId);
        if (locks != null) {
            lockedLogs.removeAll(locks);
        }
    }

    private static boolean tooFarAway(Player player, Block origin, int maximumDistance) {
        return maximumDistance > 0
                && player.getLocation().distanceSquared(origin.getLocation().add(0.5, 0.5, 0.5))
                > maximumDistance * maximumDistance;
    }

    private static boolean isTwoByTwo(List<Block> roots) {
        if (roots.size() != 4) {
            return false;
        }
        int minX = roots.stream().mapToInt(Block::getX).min().orElse(0);
        int maxX = roots.stream().mapToInt(Block::getX).max().orElse(0);
        int minZ = roots.stream().mapToInt(Block::getZ).min().orElse(0);
        int maxZ = roots.stream().mapToInt(Block::getZ).max().orElse(0);
        return maxX - minX == 1 && maxZ - minZ == 1;
    }

    private static String rejectionMessage(String reason) {
        return switch (reason) {
            case "too-few-logs" -> "Not enough connected logs to be a tree.";
            case "too-many-logs" -> "This tree exceeds the configured log limit.";
            case "not-rooted" -> "The logs are not rooted on tree-growing ground.";
            case "too-few-natural-leaves" -> "Not enough natural leaves were detected.";
            case "flat-log-formation" -> "This looks like a built log structure.";
            case "touching-building-blocks" -> "The logs are touching building blocks.";
            case "tree-outside-limits" -> "This tree extends beyond the configured scan limits.";
            default -> "This block was not recognised as a safe tree.";
        };
    }

    private static String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private static boolean sameBlock(Block first, Block second) {
        return first.getWorld().equals(second.getWorld())
                && first.getX() == second.getX() && first.getY() == second.getY()
                && first.getZ() == second.getZ();
    }
}
