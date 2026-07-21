package exposed.thunder.thunderLBs.leaderboard;

import exposed.thunder.thunderLBs.config.PluginConfig;
import org.bukkit.configuration.ConfigurationSection;

public final class LeaderboardSettings {
    private int positions;
    private long pageDurationTicks;
    private long intervalTicks;
    private long rowDelayTicks;
    private long typingIntervalTicks;
    private boolean showRelativePosition;
    private double boardScale;
    private double rowYOffset;

    public LeaderboardSettings(int positions,
            long pageDurationTicks,
            long intervalTicks,
            long rowDelayTicks,
            long typingIntervalTicks,
            boolean showRelativePosition,
            double boardScale,
            double rowYOffset) {
        this.positions = positions;
        this.pageDurationTicks = pageDurationTicks;
        this.intervalTicks = intervalTicks;
        this.rowDelayTicks = rowDelayTicks;
        this.typingIntervalTicks = typingIntervalTicks;
        this.showRelativePosition = showRelativePosition;
        this.boardScale = boardScale;
        this.rowYOffset = rowYOffset;
    }

    public static LeaderboardSettings from(ConfigurationSection section, PluginConfig config) {
        PluginConfig.Defaults defaults = config.defaults();
        if (section == null) {
            return new LeaderboardSettings(
                    defaults.positions(),
                    defaults.pageDurationTicks(),
                    defaults.intervalTicks(),
                    defaults.rowDelayTicks(),
                    defaults.typingIntervalTicks(),
                    true,
                    1.0D,
                    defaults.rowStartOffset());
        }
        return new LeaderboardSettings(
                section.getInt("positions", defaults.positions()),
                section.getLong("page-duration-ticks", defaults.pageDurationTicks()),
                section.getLong("interval-ticks", defaults.intervalTicks()),
                section.getLong("row-delay-ticks", defaults.rowDelayTicks()),
                section.getLong("typing-interval-ticks", defaults.typingIntervalTicks()),
                section.getBoolean("show-relative-position", true),
                section.getDouble("board-scale", 1.0D),
                section.getDouble("row-y-offset", defaults.rowStartOffset()));
    }

    public void serialize(ConfigurationSection section) {
        section.set("positions", positions);
        section.set("page-duration-ticks", pageDurationTicks);
        section.set("interval-ticks", intervalTicks);
        section.set("row-delay-ticks", rowDelayTicks);
        section.set("typing-interval-ticks", typingIntervalTicks);
        section.set("show-relative-position", showRelativePosition);
        section.set("board-scale", boardScale);
        section.set("row-y-offset", rowYOffset);
    }

    public int positions() {
        return positions;
    }

    public void setPositions(int positions) {
        this.positions = positions;
    }

    public long pageDurationTicks() {
        return pageDurationTicks;
    }

    public void setPageDurationTicks(long pageDurationTicks) {
        this.pageDurationTicks = Math.max(1L, pageDurationTicks);
    }

    public long intervalTicks() {
        return intervalTicks;
    }

    public void setIntervalTicks(long intervalTicks) {
        this.intervalTicks = Math.max(1L, intervalTicks);
    }

    public long rowDelayTicks() {
        return rowDelayTicks;
    }

    public void setRowDelayTicks(long rowDelayTicks) {
        this.rowDelayTicks = Math.max(0L, rowDelayTicks);
    }

    public long typingIntervalTicks() {
        return typingIntervalTicks;
    }

    public void setTypingIntervalTicks(long typingIntervalTicks) {
        this.typingIntervalTicks = Math.max(0L, typingIntervalTicks);
    }

    public boolean showRelativePosition() {
        return showRelativePosition;
    }

    public void setShowRelativePosition(boolean showRelativePosition) {
        this.showRelativePosition = showRelativePosition;
    }

    public double boardScale() {
        return boardScale;
    }

    public void setBoardScale(double boardScale) {
        this.boardScale = boardScale;
    }

    public double rowYOffset() {
        return rowYOffset;
    }

    public void setRowYOffset(double rowYOffset) {
        this.rowYOffset = rowYOffset;
    }
}
