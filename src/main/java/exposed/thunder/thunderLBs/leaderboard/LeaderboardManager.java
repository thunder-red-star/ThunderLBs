package exposed.thunder.thunderLBs.leaderboard;

import exposed.thunder.thunderLBs.ThunderLBs;
import exposed.thunder.thunderLBs.config.PluginConfig;
import exposed.thunder.thunderLBs.placeholder.PlaceholderBridge;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Display;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LeaderboardManager {
    private final ThunderLBs plugin;
    private final PluginConfig config;
    private final PlaceholderBridge placeholderBridge;
    private final Map<String, Leaderboard> active;
    private final File folder;
    private final File backupFolder;
    private final File trashFolder;
    private final LeaderboardValidator validator;

    public record OperationResult(boolean success, String message, List<ValidationIssue> issues) {
        public static OperationResult success(String message) {
            return new OperationResult(true, message, List.of());
        }

        public static OperationResult failure(String message) {
            return new OperationResult(false, message, List.of());
        }

        public static OperationResult failure(String message, List<ValidationIssue> issues) {
            return new OperationResult(false, message, List.copyOf(issues));
        }
    }

    public record LoadSummary(int loaded, int retained, int failed, List<String> failures) {
        public boolean successful() {
            return failed == 0;
        }
    }

    public LeaderboardManager(ThunderLBs plugin,
            PluginConfig config,
            PlaceholderBridge placeholderBridge) {
        this.plugin = plugin;
        this.config = config;
        this.placeholderBridge = placeholderBridge;
        this.active = new ConcurrentHashMap<>();
        this.folder = new File(plugin.getDataFolder(), "leaderboards");
        this.backupFolder = new File(plugin.getDataFolder(), "backups");
        this.trashFolder = new File(plugin.getDataFolder(), "trash");
        this.validator = new LeaderboardValidator();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Failed to create leaderboards directory at " + folder.getAbsolutePath());
        }
    }

    public void reloadAnimationCache() {
        active.values().forEach(Leaderboard::rebuildAnimationCache);
    }

    public LoadSummary loadAll() {
        if (!folder.exists()) {
            return new LoadSummary(0, 0, 0, List.of());
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return new LoadSummary(0, 0, 0, List.of());
        }
        int loaded = 0;
        int retained = 0;
        int failed = 0;
        List<String> failures = new java.util.ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (File file : files) {
            String name = file.getName().substring(0, file.getName().length() - 4);
            if (!LeaderboardFiles.isValidId(name)) {
                plugin.getLogger().severe("Skipping leaderboard file '" + file.getName()
                        + "': file name is not a valid leaderboard ID.");
                failed++;
                failures.add(file.getName() + ": invalid leaderboard ID");
                continue;
            }
            String normalized = LeaderboardFiles.normalizeId(name);
            seen.add(normalized);
            boolean hadPrevious = active.containsKey(normalized);
            OperationResult result = reloadDetailed(normalized);
            if (!result.success()) {
                failed++;
                if (hadPrevious) {
                    retained++;
                }
                failures.add(file.getName() + ": " + result.message());
                plugin.getLogger().severe("Failed to load leaderboard '" + name + "': " + result.message());
                logIssues(normalized, result.issues());
            } else {
                loaded++;
            }
        }
        for (String removed : new HashSet<>(active.keySet())) {
            if (!seen.contains(removed)) {
                Leaderboard leaderboard = active.remove(removed);
                if (leaderboard != null) {
                    leaderboard.stop();
                }
            }
        }
        return new LoadSummary(loaded, retained, failed, List.copyOf(failures));
    }

    public Optional<Leaderboard> load(LeaderboardDefinition definition) {
        List<ValidationIssue> issues = validator.validate(definition);
        if (validator.hasErrors(issues)) {
            logIssues(definition.id(), issues);
            return Optional.empty();
        }
        if (definition.enabled() && definition.origin().getWorld() == null) {
            plugin.getLogger().warning("World for leaderboard '" + definition.id() + "' is not loaded. Skipping.");
            return Optional.empty();
        }
        Leaderboard leaderboard = new Leaderboard(plugin, config, placeholderBridge, definition);
        active.put(definition.id().toLowerCase(Locale.ROOT), leaderboard);
        if (definition.enabled()) {
            leaderboard.start();
        }
        return Optional.of(leaderboard);
    }

    public Optional<Leaderboard> create(String id, Location origin, String initialHolder, LeaderboardType type) {
        if (!LeaderboardFiles.isValidId(id)) {
            return Optional.empty();
        }
        String normalizedId = LeaderboardFiles.normalizeId(id);
        if (active.containsKey(normalizedId)) {
            return Optional.empty();
        }
        File file = LeaderboardFiles.configFile(folder, normalizedId);
        if (file.exists()) {
            return Optional.empty();
        }
        if (origin.getWorld() == null) {
            plugin.getLogger().warning("Cannot create leaderboard without a valid world.");
            return Optional.empty();
        }
        LeaderboardSettings settings = new LeaderboardSettings(
                config.defaults().positions(),
                config.defaults().pageDurationTicks(),
                config.defaults().intervalTicks(),
                config.defaults().rowDelayTicks(),
                config.defaults().typingIntervalTicks(),
                true,
                1.0D,
                config.defaults().rowStartOffset());
        LeaderboardFormatting formatting = new LeaderboardFormatting(
                config.formatting().title(),
                config.formatting().row(),
                config.formatting().relative(),
                config.bar().background(),
                config.bar().foreground());
        LeaderboardPage page = new LeaderboardPage(
                initialHolder,
                initialHolder.toUpperCase(Locale.ROOT),
                "#FFFFFF",
                "*",
                ValueFormat.SHORT_NUMBER,
                "",
                "");
        Location baseLocation = origin.clone();
        baseLocation.setYaw(0.0F);
        baseLocation.setPitch(0.0F);
        String worldName = baseLocation.getWorld().getName();
        LeaderboardAnimations animations = LeaderboardAnimations.defaults(config.animation());
        LeaderboardDefinition definition = new LeaderboardDefinition(
                normalizedId,
                type,
                file,
                baseLocation,
                worldName,
                settings,
                formatting,
                new java.util.ArrayList<>(java.util.List.of(page)),
                Display.Billboard.FIXED,
                true,
                config.bar().mode(),
                config.bar().useHolderColor(),
                config.viewDistance(),
                animations,
                true);
        List<ValidationIssue> issues = validator.validate(definition);
        if (validator.hasErrors(issues)) {
            plugin.getLogger().severe("Cannot create leaderboard '" + id + "' because its defaults are invalid.");
            logIssues(normalizedId, issues);
            return Optional.empty();
        }
        try {
            definition.save();
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save leaderboard '" + id + "': " + ex.getMessage());
            return Optional.empty();
        }
        return load(definition);
    }

    public boolean delete(String id) {
        return trash(id).success();
    }

    public boolean reload(String id) {
        return reloadDetailed(id).success();
    }

    public OperationResult reloadDetailed(String id) {
        if (!LeaderboardFiles.isValidId(id)) {
            return OperationResult.failure("Invalid leaderboard ID.");
        }
        String normalized = LeaderboardFiles.normalizeId(id);
        File file = LeaderboardFiles.configFile(folder, normalized);
        if (!file.exists()) {
            return OperationResult.failure("Configuration file does not exist.");
        }
        LeaderboardDefinition definition;
        try {
            definition = LeaderboardDefinition.load(normalized, file, config);
        } catch (IOException | InvalidConfigurationException ex) {
            return OperationResult.failure("Could not parse " + file.getName() + ": " + ex.getMessage());
        }
        List<ValidationIssue> issues = validator.validate(definition);
        if (validator.hasErrors(issues)) {
            return OperationResult.failure("Validation failed; the previous board is still running.", issues);
        }
        if (definition.legacyFormat()) {
            try {
                File backup = LeaderboardFiles.timestampedFile(backupFolder, normalized);
                LeaderboardFiles.copy(file, backup);
                definition.save();
                plugin.getLogger().info("Upgraded leaderboard file '" + file.getName()
                        + "'; backup saved as '" + backup.getName() + "'.");
            } catch (IOException ex) {
                return OperationResult.failure("Could not back up and upgrade legacy configuration: "
                        + ex.getMessage());
            }
        }

        Leaderboard replacement = new Leaderboard(plugin, config, placeholderBridge, definition);
        Leaderboard previous = active.get(normalized);
        if (previous != null) {
            previous.stop();
        }
        try {
            active.put(normalized, replacement);
            if (definition.enabled()) {
                replacement.start();
            }
            return new OperationResult(true, "Reloaded " + normalized + ".", issues);
        } catch (RuntimeException ex) {
            replacement.stop();
            if (previous != null) {
                active.put(normalized, previous);
                if (previous.definition().enabled()) {
                    previous.start();
                }
            } else {
                active.remove(normalized);
            }
            return OperationResult.failure("Could not activate replacement; restored previous board: "
                    + ex.getMessage());
        }
    }

    public OperationResult saveAndReload(LeaderboardDefinition definition) {
        List<ValidationIssue> issues = validator.validate(definition);
        if (validator.hasErrors(issues)) {
            reloadDetailed(definition.id());
            return OperationResult.failure("Validation failed; changes were not saved.", issues);
        }
        File current = LeaderboardFiles.configFile(folder, definition.id());
        Path rollback = null;
        try {
            if (current.exists()) {
                rollback = Files.createTempFile(folder.toPath(), definition.id() + ".rollback.", ".yml");
                Files.copy(current.toPath(), rollback, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            definition.save();
        } catch (IOException ex) {
            deleteQuietly(rollback);
            return OperationResult.failure("Could not save configuration: " + ex.getMessage());
        }
        OperationResult result = reloadDetailed(definition.id());
        if (!result.success() && rollback != null) {
            try {
                LeaderboardFiles.move(rollback.toFile(), current);
                reloadDetailed(definition.id());
            } catch (IOException ex) {
                plugin.getLogger().severe("Could not restore the previous configuration after a failed save: "
                        + ex.getMessage());
            }
        }
        deleteQuietly(rollback);
        return result;
    }

    public OperationResult setEnabled(String id, boolean enabled) {
        Leaderboard board = get(id);
        if (board == null) {
            return OperationResult.failure("Leaderboard does not exist.");
        }
        LeaderboardDefinition definition = board.definition();
        boolean previous = definition.enabled();
        if (previous == enabled) {
            return OperationResult.success("Leaderboard is already " + (enabled ? "enabled" : "disabled") + ".");
        }
        definition.setEnabled(enabled);
        OperationResult result = saveAndReload(definition);
        if (!result.success()) {
            definition.setEnabled(previous);
        }
        return result;
    }

    public OperationResult trash(String id) {
        if (!LeaderboardFiles.isValidId(id)) {
            return OperationResult.failure("Invalid leaderboard ID.");
        }
        String normalized = LeaderboardFiles.normalizeId(id);
        File source = LeaderboardFiles.configFile(folder, normalized);
        if (!source.exists()) {
            return OperationResult.failure("Configuration file does not exist.");
        }
        File destination = LeaderboardFiles.timestampedFile(trashFolder, normalized);
        try {
            LeaderboardFiles.move(source, destination);
        } catch (IOException ex) {
            return OperationResult.failure("Could not move configuration to trash: " + ex.getMessage());
        }
        Leaderboard board = active.remove(normalized);
        if (board != null) {
            board.stop();
        }
        return OperationResult.success("Moved " + source.getName() + " to trash as " + destination.getName() + ".");
    }

    public OperationResult restore(String id) {
        if (!LeaderboardFiles.isValidId(id)) {
            return OperationResult.failure("Invalid leaderboard ID.");
        }
        String normalized = LeaderboardFiles.normalizeId(id);
        File destination = LeaderboardFiles.configFile(folder, normalized);
        if (destination.exists() || active.containsKey(normalized)) {
            return OperationResult.failure("A leaderboard with that ID already exists.");
        }
        File source = newestTrashFile(normalized);
        if (source == null) {
            return OperationResult.failure("No deleted configuration was found for that ID.");
        }
        try {
            LeaderboardFiles.move(source, destination);
        } catch (IOException ex) {
            return OperationResult.failure("Could not restore configuration: " + ex.getMessage());
        }
        OperationResult result = reloadDetailed(normalized);
        if (!result.success()) {
            try {
                LeaderboardFiles.move(destination, source);
            } catch (IOException rollback) {
                plugin.getLogger().severe("Failed to return invalid restored file to trash: " + rollback.getMessage());
            }
        }
        return result.success() ? OperationResult.success("Restored leaderboard " + normalized + ".") : result;
    }

    public List<String> trashedIds() {
        if (!trashFolder.isDirectory()) {
            return List.of();
        }
        File[] files = trashFolder.listFiles((dir, name) -> name.endsWith(".yml") && name.contains("--"));
        if (files == null) {
            return List.of();
        }
        Set<String> ids = new HashSet<>();
        for (File file : files) {
            String name = file.getName();
            String id = name.substring(0, name.indexOf("--"));
            if (LeaderboardFiles.isValidId(id)) {
                ids.add(id);
            }
        }
        return ids.stream().sorted().toList();
    }

    public List<ValidationIssue> validate(LeaderboardDefinition definition) {
        return validator.validate(definition);
    }

    public OperationResult validateFile(String id) {
        if (!LeaderboardFiles.isValidId(id)) {
            return OperationResult.failure("Invalid leaderboard ID.");
        }
        String normalized = LeaderboardFiles.normalizeId(id);
        File file = LeaderboardFiles.configFile(folder, normalized);
        if (!file.exists()) {
            return OperationResult.failure("Configuration file does not exist.");
        }
        try {
            LeaderboardDefinition definition = LeaderboardDefinition.load(normalized, file, config);
            List<ValidationIssue> issues = validator.validate(definition);
            if (validator.hasErrors(issues)) {
                return OperationResult.failure("Validation failed.", issues);
            }
            return new OperationResult(true, "Configuration is valid.", issues);
        } catch (IOException | InvalidConfigurationException ex) {
            return OperationResult.failure("Could not parse " + file.getName() + ": " + ex.getMessage());
        }
    }

    public List<String> configuredIds() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return List.of();
        }
        return java.util.Arrays.stream(files)
                .map(File::getName)
                .map(name -> name.substring(0, name.length() - 4))
                .filter(LeaderboardFiles::isValidId)
                .map(LeaderboardFiles::normalizeId)
                .sorted()
                .toList();
    }

    public boolean hasValidationErrors(List<ValidationIssue> issues) {
        return validator.hasErrors(issues);
    }

    private File newestTrashFile(String id) {
        File[] files = trashFolder.listFiles((dir, name) -> name.startsWith(id + "--") && name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            return null;
        }
        return java.util.Arrays.stream(files).max(Comparator.comparingLong(File::lastModified)).orElse(null);
    }

    private void logIssues(String id, List<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) {
            plugin.getLogger().warning("[" + id + "] " + issue.severity() + " " + issue.path() + ": "
                    + issue.message());
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not remove temporary rollback file '" + path.getFileName() + "'.");
        }
    }

    public Leaderboard get(String id) {
        return active.get(LeaderboardFiles.normalizeId(id));
    }

    public Collection<Leaderboard> all() {
        return Collections.unmodifiableCollection(active.values());
    }

    public int totalHolders() {
        return active.values().stream()
                .mapToInt(leaderboard -> leaderboard.definition().pages().size())
                .sum();
    }

    public File folder() {
        return folder;
    }

    public void shutdown() {
        for (Leaderboard leaderboard : active.values()) {
            leaderboard.stop();
        }
        active.clear();
    }
}
