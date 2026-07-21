package exposed.thunder.thunderLBs.leaderboard;

import exposed.thunder.thunderLBs.animation.EasingType;
import exposed.thunder.thunderLBs.config.PluginConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Objects;

public final class LeaderboardAnimations {
    private final Title title;
    private final Row row;
    private final Bar bar;

    private LeaderboardAnimations(Title title, Row row, Bar bar) {
        this.title = title;
        this.row = row;
        this.bar = bar;
    }

    public static LeaderboardAnimations defaults(PluginConfig.Animation defaults) {
        Objects.requireNonNull(defaults, "defaults");
        return new LeaderboardAnimations(
                Title.from(null, defaults.title()),
                Row.from(null, defaults.row()),
                Bar.from(null, defaults.bar())
        );
    }

    public static LeaderboardAnimations from(ConfigurationSection section, PluginConfig.Animation defaults) {
        Objects.requireNonNull(defaults, "defaults");
        if (section == null) {
            return defaults(defaults);
        }
        Title title = Title.from(section.getConfigurationSection("title"), defaults.title());
        Row row = Row.from(section.getConfigurationSection("rows"), defaults.row());
        Bar bar = Bar.from(section.getConfigurationSection("progress"), defaults.bar());
        return new LeaderboardAnimations(title, row, bar);
    }

    public LeaderboardAnimations copy() {
        return new LeaderboardAnimations(title.copy(), row.copy(), bar.copy());
    }

    public Title title() {
        return title;
    }

    public Row row() {
        return row;
    }

    public Bar bar() {
        return bar;
    }

    public void serialize(ConfigurationSection section) {
        Title.serialize(section.createSection("title"), title);
        Row.serialize(section.createSection("rows"), row);
        Bar.serialize(section.createSection("progress"), bar);
    }

    public static final class Title {
        private boolean inEnabled;
        private boolean outEnabled;
        private int inFrames;
        private int outFrames;
        private EasingType inCurve;
        private EasingType outCurve;
        private double scale;
        private double overshoot;

        private Title(boolean inEnabled,
                      boolean outEnabled,
                      int inFrames,
                      int outFrames,
                      EasingType inCurve,
                      EasingType outCurve,
                      double scale,
                      double overshoot) {
            this.inEnabled = inEnabled;
            this.outEnabled = outEnabled;
            this.inFrames = Math.max(1, inFrames);
            this.outFrames = Math.max(1, outFrames);
            this.inCurve = Objects.requireNonNullElse(inCurve, EasingType.LINEAR);
            this.outCurve = Objects.requireNonNullElse(outCurve, EasingType.LINEAR);
            this.scale = scale;
            this.overshoot = overshoot;
        }

        public static Title from(ConfigurationSection section, PluginConfig.Animation.Title defaults) {
            boolean enabled = section == null || section.getBoolean("enabled", true);
            boolean inEnabled = section == null || section.getBoolean("in-enabled", enabled);
            boolean outEnabled = section == null || section.getBoolean("out-enabled", enabled);
            int inFrames = defaults.inFrames();
            int outFrames = defaults.outFrames();
            EasingType defaultIn = defaults.inEasing();
            EasingType defaultOut = defaults.outEasing();
            double scale = defaults.scale();
            double overshoot = defaults.overshoot();
            if (section != null) {
                inFrames = section.getInt("frames-in", inFrames);
                outFrames = section.getInt("frames-out", outFrames);
                scale = section.getDouble("scale", scale);
                overshoot = section.getDouble("overshoot", overshoot);
                String global = section.getString("type");
                String inCurve = section.getString("in-curve", global);
                String outCurve = section.getString("out-curve", global);
                defaultIn = EasingType.fromFriendly(inCurve, defaultIn);
                defaultOut = EasingType.fromFriendly(outCurve, defaultOut);
                if (global != null && global.equalsIgnoreCase("none")) {
                    inEnabled = false;
                    outEnabled = false;
                }
            }
            return new Title(inEnabled, outEnabled, inFrames, outFrames, defaultIn, defaultOut, scale, overshoot);
        }

        public Title copy() {
            return new Title(inEnabled, outEnabled, inFrames, outFrames, inCurve, outCurve, scale, overshoot);
        }

        public boolean enabled() {
            return inEnabled || outEnabled;
        }

        public void setEnabled(boolean enabled) {
            this.inEnabled = enabled;
            this.outEnabled = enabled;
        }

        public boolean inEnabled() {
            return inEnabled;
        }

        public void setInEnabled(boolean inEnabled) {
            this.inEnabled = inEnabled;
        }

        public boolean outEnabled() {
            return outEnabled;
        }

        public void setOutEnabled(boolean outEnabled) {
            this.outEnabled = outEnabled;
        }

        public int inFrames() {
            return inFrames;
        }

        public void setInFrames(int inFrames) {
            this.inFrames = Math.max(1, inFrames);
        }

        public int outFrames() {
            return outFrames;
        }

        public void setOutFrames(int outFrames) {
            this.outFrames = Math.max(1, outFrames);
        }

        public EasingType inCurve() {
            return inCurve;
        }

        public void setInCurve(EasingType inCurve) {
            this.inCurve = Objects.requireNonNullElse(inCurve, EasingType.LINEAR);
        }

        public EasingType outCurve() {
            return outCurve;
        }

        public void setOutCurve(EasingType outCurve) {
            this.outCurve = Objects.requireNonNullElse(outCurve, EasingType.LINEAR);
        }

        public double scale() {
            return scale;
        }

        public void setScale(double scale) {
            this.scale = scale;
        }

        public double overshoot() {
            return overshoot;
        }

        public void setOvershoot(double overshoot) {
            this.overshoot = overshoot;
        }

        private static void serialize(ConfigurationSection section, Title title) {
            section.set("enabled", title.enabled());
            section.set("in-enabled", title.inEnabled);
            section.set("out-enabled", title.outEnabled);
            section.set("frames-in", title.inFrames);
            section.set("frames-out", title.outFrames);
            section.set("in-curve", title.inCurve.name().toLowerCase(Locale.ROOT));
            section.set("out-curve", title.outCurve.name().toLowerCase(Locale.ROOT));
            section.set("scale", title.scale);
            section.set("overshoot", title.overshoot);
        }
    }

    public static final class Row {
        private boolean inEnabled;
        private boolean outEnabled;
        private int inFrames;
        private int outFrames;
        private EasingType inCurve;
        private EasingType outCurve;
        private double startOffset;
        private double distance;

        private Row(boolean inEnabled,
                    boolean outEnabled,
                    int inFrames,
                    int outFrames,
                    EasingType inCurve,
                    EasingType outCurve,
                    double startOffset,
                    double distance) {
            this.inEnabled = inEnabled;
            this.outEnabled = outEnabled;
            this.inFrames = Math.max(1, inFrames);
            this.outFrames = Math.max(1, outFrames);
            this.inCurve = Objects.requireNonNullElse(inCurve, EasingType.LINEAR);
            this.outCurve = Objects.requireNonNullElse(outCurve, EasingType.LINEAR);
            this.startOffset = startOffset;
            this.distance = distance;
        }

        public static Row from(ConfigurationSection section, PluginConfig.Animation.Row defaults) {
            boolean enabled = section == null || section.getBoolean("enabled", true);
            boolean inEnabled = section == null || section.getBoolean("in-enabled", enabled);
            boolean outEnabled = section == null || section.getBoolean("out-enabled", enabled);
            int inFrames = defaults.inFrames();
            int outFrames = defaults.outFrames();
            EasingType inCurve = defaults.inEasing();
            EasingType outCurve = defaults.outEasing();
            double startOffset = defaults.startOffset();
            double distance = defaults.distance();
            if (section != null) {
                inFrames = section.getInt("frames-in", inFrames);
                outFrames = section.getInt("frames-out", outFrames);
                startOffset = section.getDouble("start-offset", startOffset);
                distance = section.getDouble("distance", distance);
                String global = section.getString("type");
                inCurve = EasingType.fromFriendly(section.getString("in-curve", global), inCurve);
                outCurve = EasingType.fromFriendly(section.getString("out-curve", global), outCurve);
                if (global != null && global.equalsIgnoreCase("none")) {
                    inEnabled = false;
                    outEnabled = false;
                }
            }
            return new Row(inEnabled, outEnabled, inFrames, outFrames, inCurve, outCurve, startOffset, distance);
        }

        public Row copy() {
            return new Row(inEnabled, outEnabled, inFrames, outFrames, inCurve, outCurve, startOffset, distance);
        }

        public boolean enabled() {
            return inEnabled || outEnabled;
        }

        public void setEnabled(boolean enabled) {
            this.inEnabled = enabled;
            this.outEnabled = enabled;
        }

        public boolean inEnabled() {
            return inEnabled;
        }

        public void setInEnabled(boolean inEnabled) {
            this.inEnabled = inEnabled;
        }

        public boolean outEnabled() {
            return outEnabled;
        }

        public void setOutEnabled(boolean outEnabled) {
            this.outEnabled = outEnabled;
        }

        public int inFrames() {
            return inFrames;
        }

        public void setInFrames(int inFrames) {
            this.inFrames = Math.max(1, inFrames);
        }

        public int outFrames() {
            return outFrames;
        }

        public void setOutFrames(int outFrames) {
            this.outFrames = Math.max(1, outFrames);
        }

        public EasingType inCurve() {
            return inCurve;
        }

        public void setInCurve(EasingType inCurve) {
            this.inCurve = Objects.requireNonNullElse(inCurve, EasingType.LINEAR);
        }

        public EasingType outCurve() {
            return outCurve;
        }

        public void setOutCurve(EasingType outCurve) {
            this.outCurve = Objects.requireNonNullElse(outCurve, EasingType.LINEAR);
        }

        public double startOffset() {
            return startOffset;
        }

        public void setStartOffset(double startOffset) {
            this.startOffset = startOffset;
        }

        public double distance() {
            return distance;
        }

        public void setDistance(double distance) {
            this.distance = distance;
        }

        private static void serialize(ConfigurationSection section, Row row) {
            section.set("enabled", row.enabled());
            section.set("in-enabled", row.inEnabled);
            section.set("out-enabled", row.outEnabled);
            section.set("frames-in", row.inFrames);
            section.set("frames-out", row.outFrames);
            section.set("in-curve", row.inCurve.name().toLowerCase(Locale.ROOT));
            section.set("out-curve", row.outCurve.name().toLowerCase(Locale.ROOT));
            section.set("start-offset", row.startOffset);
            section.set("distance", row.distance);
        }
    }

    public static final class Bar {
        private boolean enabled;
        private int frames;
        private EasingType curve;
        private double translationStart;
        private double translationEnd;

        private Bar(boolean enabled,
                    int frames,
                    EasingType curve,
                    double translationStart,
                    double translationEnd) {
            this.enabled = enabled;
            this.frames = Math.max(1, frames);
            this.curve = Objects.requireNonNullElse(curve, EasingType.LINEAR);
            this.translationStart = translationStart;
            this.translationEnd = translationEnd;
        }

        public static Bar from(ConfigurationSection section, PluginConfig.Animation.Bar defaults) {
            boolean enabled = section == null || section.getBoolean("enabled", true);
            int frames = defaults.frames();
            EasingType curve = defaults.easing();
            double translationStart = defaults.translationStart();
            double translationEnd = defaults.translationEnd();
            if (section != null) {
                frames = section.getInt("frames", frames);
                translationStart = section.getDouble("translation-start", translationStart);
                translationEnd = section.getDouble("translation-end", translationEnd);
                String type = section.getString("type");
                curve = EasingType.fromFriendly(type, curve);
                if (type != null && type.equalsIgnoreCase("none")) {
                    enabled = false;
                }
            }
            return new Bar(enabled, frames, curve, translationStart, translationEnd);
        }

        public Bar copy() {
            return new Bar(enabled, frames, curve, translationStart, translationEnd);
        }

        public boolean enabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int frames() {
            return frames;
        }

        public void setFrames(int frames) {
            this.frames = Math.max(1, frames);
        }

        public EasingType curve() {
            return curve;
        }

        public void setCurve(EasingType curve) {
            this.curve = Objects.requireNonNullElse(curve, EasingType.LINEAR);
        }

        public double translationStart() {
            return translationStart;
        }

        public void setTranslationStart(double translationStart) {
            this.translationStart = translationStart;
        }

        public double translationEnd() {
            return translationEnd;
        }

        public void setTranslationEnd(double translationEnd) {
            this.translationEnd = translationEnd;
        }

        private static void serialize(ConfigurationSection section, Bar bar) {
            section.set("enabled", bar.enabled);
            section.set("frames", bar.frames);
            section.set("type", bar.curve.name().toLowerCase(Locale.ROOT));
            section.set("translation-start", bar.translationStart);
            section.set("translation-end", bar.translationEnd);
        }
    }
}
