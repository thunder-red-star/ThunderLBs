package exposed.thunder.thunderLBs.leaderboard;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LeaderboardValidator {
    private static final int MAX_POSITIONS = 100;
    private static final long MAX_TIMING_TICKS = 20L * 60L * 60L;

    private final MiniMessage miniMessage;

    public LeaderboardValidator() {
        this.miniMessage = MiniMessage.miniMessage();
    }

    public List<ValidationIssue> validate(LeaderboardDefinition definition) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (!LeaderboardFiles.isValidId(definition.id())) {
            error(issues, "id", "must contain only a-z, 0-9, _ or - and be at most 64 characters");
        }
        if (definition.origin().getWorld() == null) {
            add(issues, definition.enabled() ? ValidationIssue.Severity.ERROR : ValidationIssue.Severity.WARNING,
                    "world", "world is not currently loaded");
        }
        if (definition.viewDistance() < 4 || definition.viewDistance() > 256) {
            error(issues, "view-distance", "must be between 4 and 256 blocks");
        }

        LeaderboardSettings settings = definition.settings();
        if (settings.positions() < 1 || settings.positions() > MAX_POSITIONS) {
            error(issues, "settings.positions", "must be between 1 and " + MAX_POSITIONS);
        }
        if (!Double.isFinite(settings.boardScale())
                || settings.boardScale() < 0.1D || settings.boardScale() > 5.0D) {
            error(issues, "settings.board-scale", "must be between 0.1 and 5.0");
        }
        if (!Double.isFinite(settings.rowYOffset())
                || settings.rowYOffset() < -5.0D || settings.rowYOffset() > 5.0D) {
            error(issues, "settings.row-y-offset", "must be between -5.0 and 5.0");
        }
        validatePositiveTiming(issues, "settings.page-duration-ticks", settings.pageDurationTicks());
        validatePositiveTiming(issues, "settings.interval-ticks", settings.intervalTicks());
        validateNonNegativeTiming(issues, "settings.row-delay-ticks", settings.rowDelayTicks());
        validateNonNegativeTiming(issues, "settings.typing-interval-ticks", settings.typingIntervalTicks());

        if (definition.pages().isEmpty()) {
            error(issues, "pages", "at least one page is required");
        }
        Set<String> holders = new HashSet<>();
        for (int index = 0; index < definition.pages().size(); index++) {
            LeaderboardPage page = definition.pages().get(index);
            String path = "pages.page-" + (index + 1);
            if (page.holderId() == null || page.holderId().isBlank()) {
                error(issues, path + ".holder", "must not be empty");
            } else if (!holders.add(page.holderId().toLowerCase(Locale.ROOT) + "|" + page.interval())) {
                warning(issues, path + ".holder", "duplicates an earlier holder with the same interval");
            }
            if (page.title() == null || page.title().isBlank()) {
                error(issues, path + ".title", "must not be empty");
            }
            if (!isColor(page.color())) {
                error(issues, path + ".color", "must be a named MiniMessage color or a hex color");
            }
        }

        LeaderboardFormatting formatting = definition.formatting();
        validatePattern(issues, "formatting.title", formatting.titlePattern());
        validatePattern(issues, "formatting.row", formatting.rowPattern());
        validatePattern(issues, "formatting.relative", formatting.relativePattern());
        validatePattern(issues, "formatting.bar.background", formatting.barBackground());
        validatePattern(issues, "formatting.bar.foreground", formatting.barForeground());

        long lastRowStart = settings.rowDelayTicks() * Math.max(0L, settings.positions() - 1L);
        if (lastRowStart >= settings.pageDurationTicks()) {
            warning(issues, "settings.page-duration-ticks",
                    "the final row starts after the page begins fading; increase page duration or reduce row delay");
        }
        return List.copyOf(issues);
    }

    public boolean hasErrors(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR);
    }

    private void validatePattern(List<ValidationIssue> issues, String path, String pattern) {
        String rendered = pattern == null ? "" : pattern
                .replace("%color%", "#ffffff")
                .replace("%icon%", "*")
                .replace("%title%", "Title")
                .replace("%position%", "1")
                .replace("%player%", "Player")
                .replace("%value%", "1")
                .replace("%rank%", "1");
        validateMiniMessage(issues, path, rendered);
    }

    private void validateMiniMessage(List<ValidationIssue> issues, String path, String value) {
        try {
            miniMessage.deserialize(value == null ? "" : value);
        } catch (RuntimeException ex) {
            error(issues, path, "invalid MiniMessage: " + firstLine(ex.getMessage()));
        }
    }

    private boolean isColor(String color) {
        if (color == null || color.isBlank()) {
            return false;
        }
        String normalized = color.trim().toLowerCase(Locale.ROOT);
        return TextColor.fromHexString(normalized) != null || NamedTextColor.NAMES.value(normalized) != null;
    }

    private void validatePositiveTiming(List<ValidationIssue> issues, String path, long value) {
        if (value < 1 || value > MAX_TIMING_TICKS) {
            error(issues, path, "must be between 1 and " + MAX_TIMING_TICKS + " ticks");
        }
    }

    private void validateNonNegativeTiming(List<ValidationIssue> issues, String path, long value) {
        if (value < 0 || value > MAX_TIMING_TICKS) {
            error(issues, path, "must be between 0 and " + MAX_TIMING_TICKS + " ticks");
        }
    }

    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "unknown parsing error";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static void error(List<ValidationIssue> issues, String path, String message) {
        add(issues, ValidationIssue.Severity.ERROR, path, message);
    }

    private static void warning(List<ValidationIssue> issues, String path, String message) {
        add(issues, ValidationIssue.Severity.WARNING, path, message);
    }

    private static void add(List<ValidationIssue> issues, ValidationIssue.Severity severity, String path,
            String message) {
        issues.add(new ValidationIssue(severity, path, message));
    }
}
