package io.github.benjaminl11au.fallingtimber;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class UpdateChecker implements Listener {
    private final FallingTimberPlugin plugin;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicBoolean checking = new AtomicBoolean();

    private volatile ReleaseStatus status = ReleaseStatus.checking();
    private BukkitTask refreshTask;

    UpdateChecker(FallingTimberPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        stop();
        if (!plugin.settings().updateChecksEnabled()) {
            status = ReleaseStatus.disabled();
            return;
        }

        checkNow();
        long interval = plugin.settings().updateCheckIntervalHours() * 60L * 60L * 20L;
        refreshTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::checkNow, interval, interval
        );
    }

    void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    void checkNow() {
        if (!plugin.settings().updateChecksEnabled() || !checking.compareAndSet(false, true)) {
            return;
        }

        String repository = plugin.settings().githubRepository();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repository + "/releases/latest"))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2026-03-10")
                .header("User-Agent", "FallingTimber/" + installedVersion())
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> acceptResponse(response.statusCode(), response.body()))
                .exceptionally(error -> {
                    status = ReleaseStatus.failed();
                    plugin.getLogger().warning("Could not check GitHub for updates: " + error.getMessage());
                    return null;
                })
                .whenComplete((ignored, error) -> checking.set(false));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.settings().updateChecksEnabled() || !plugin.settings().notifyUpdatesOnJoin()) {
            return;
        }

        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                sendStatus(player);
            }
        }, plugin.settings().updateNotifyDelayTicks());
    }

    void sendStatus(CommandSender sender) {
        ReleaseStatus snapshot = status;
        String installed = installedVersion();

        sender.sendMessage(Component.text("FallingTimber", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                .append(Component.text(" version check", NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false)));
        sender.sendMessage(Component.text("Installed: ", NamedTextColor.GRAY)
                .append(Component.text(installed, NamedTextColor.WHITE)));

        if (snapshot.state == State.READY) {
            boolean newer = compareVersions(snapshot.latestVersion, installed) > 0;
            Component result = newer
                    ? Component.text("  Update available!", NamedTextColor.GOLD)
                    : Component.text("  ✓ Up to date", NamedTextColor.GREEN);
            sender.sendMessage(Component.text("Latest: ", NamedTextColor.GRAY)
                    .append(Component.text(snapshot.latestVersion, NamedTextColor.WHITE))
                    .append(result));
        } else if (snapshot.state == State.DISABLED) {
            sender.sendMessage(Component.text("Latest: update checks disabled", NamedTextColor.DARK_GRAY));
        } else if (snapshot.state == State.FAILED) {
            sender.sendMessage(Component.text("Latest: check unavailable", NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text("Latest: checking GitHub…", NamedTextColor.YELLOW));
        }

        String url = snapshot.releaseUrl != null
                ? snapshot.releaseUrl
                : plugin.settings().releasesUrl();
        sender.sendMessage(Component.text("GitHub: ", NamedTextColor.GRAY)
                .append(Component.text("View releases", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(Component.text("Open " + url, NamedTextColor.AQUA)))));
    }

    private void acceptResponse(int statusCode, String body) {
        if (statusCode != 200) {
            status = ReleaseStatus.failed();
            plugin.getLogger().warning("GitHub update check returned HTTP " + statusCode + ".");
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String version = normalizeVersion(json.get("tag_name").getAsString());
            String url = json.has("html_url")
                    ? json.get("html_url").getAsString()
                    : plugin.settings().releasesUrl();
            status = ReleaseStatus.ready(version, url);
        } catch (RuntimeException error) {
            status = ReleaseStatus.failed();
            plugin.getLogger().warning("GitHub returned an unreadable release response.");
        }
    }

    private String installedVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    private static String normalizeVersion(String value) {
        return value.strip().replaceFirst("^[vV]", "");
    }

    private static int compareVersions(String left, String right) {
        List<Integer> leftParts = numericParts(left);
        List<Integer> rightParts = numericParts(right);
        int size = Math.max(leftParts.size(), rightParts.size());
        for (int index = 0; index < size; index++) {
            int leftPart = index < leftParts.size() ? leftParts.get(index) : 0;
            int rightPart = index < rightParts.size() ? rightParts.get(index) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static List<Integer> numericParts(String version) {
        List<Integer> parts = new ArrayList<>();
        for (String part : normalizeVersion(version).split("[.-]")) {
            try {
                parts.add(Integer.parseInt(part.replaceFirst("\\D.*$", "")));
            } catch (NumberFormatException ignored) {
                parts.add(0);
            }
        }
        return parts;
    }

    private enum State { CHECKING, READY, FAILED, DISABLED }

    private record ReleaseStatus(State state, String latestVersion, String releaseUrl) {
        private static ReleaseStatus checking() {
            return new ReleaseStatus(State.CHECKING, null, null);
        }

        private static ReleaseStatus ready(String version, String url) {
            return new ReleaseStatus(State.READY, version, url);
        }

        private static ReleaseStatus failed() {
            return new ReleaseStatus(State.FAILED, null, null);
        }

        private static ReleaseStatus disabled() {
            return new ReleaseStatus(State.DISABLED, null, null);
        }
    }
}
