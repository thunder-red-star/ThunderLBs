package exposed.thunder.thunderLBs.animation;

import exposed.thunder.thunderLBs.leaderboard.LeaderboardAnimations;

public final class AnimationCache {
    private final float[] titleInScale;
    private final float[] titleOutScale;
    private final float[] rowInOffsets;
    private final float[] rowOutOffsets;
    private final float[] barScales;
    private final float[] barTranslations;

    public AnimationCache(LeaderboardAnimations animations) {
        LeaderboardAnimations.Title title = animations.title();
        LeaderboardAnimations.Row row = animations.row();
        LeaderboardAnimations.Bar bar = animations.bar();

        this.titleInScale = computeScaleFrames(
                title.inEnabled(),
                title.inFrames(),
                title.inCurve(),
                title.scale(),
                title.overshoot(),
                true
        );
        this.titleOutScale = computeScaleFrames(
                title.outEnabled(),
                title.outFrames(),
                title.outCurve(),
                title.scale(),
                title.overshoot(),
                false
        );
        this.rowInOffsets = computeRowFrames(
                row.inEnabled(),
                row.inFrames(),
                row.inCurve(),
                row.startOffset(),
                row.distance(),
                true
        );
        this.rowOutOffsets = computeRowFrames(
                row.outEnabled(),
                row.outFrames(),
                row.outCurve(),
                0.0D,
                row.distance(),
                false
        );
        this.barScales = computeBarScales(bar);
        this.barTranslations = computeBarTranslations(bar);
    }

    private float[] computeScaleFrames(boolean enabled, int frames, EasingType easing, double multiplier,
            double overshoot, boolean fadeIn) {
        if (!enabled || frames <= 1) {
            return new float[] {fadeIn ? (float) multiplier : 0.0F};
        }
        float[] values = new float[frames];
        int denominator = Math.max(frames - 1, 1);
        for (int i = 0; i < frames; i++) {
            double progress = (double) i / (double) denominator;
            double eased = easing.apply(progress, overshoot);
            if (fadeIn) {
                values[i] = (float) (eased * multiplier);
            } else {
                double scale = (1.0D - eased) * multiplier;
                values[i] = (float) Math.max(0.0D, Math.min(multiplier, scale));
            }
        }
        if (!fadeIn) {
            values[0] = (float) multiplier;
            values[values.length - 1] = 0.0F;
        }
        return values;
    }

    private float[] computeRowFrames(boolean enabled, int frames, EasingType easing, double origin, double distance, boolean forward) {
        if (!enabled || frames <= 1) {
            double result = forward ? origin + distance : origin;
            return new float[] {(float) result};
        }
        float[] values = new float[frames];
        int denominator = Math.max(frames - 1, 1);
        for (int i = 0; i < frames; i++) {
            double progress = (double) i / (double) denominator;
            double eased = easing.apply(progress, 0.0D);
            values[i] = (float) (origin + eased * distance);
        }
        return values;
    }

    private float[] computeBarScales(LeaderboardAnimations.Bar bar) {
        int frames = bar.frames();
        if (!bar.enabled() || frames <= 1) {
            return new float[] {1.0F};
        }
        EasingType easing = bar.curve();
        float[] values = new float[frames];
        int denominator = Math.max(frames - 1, 1);
        for (int i = 0; i < frames; i++) {
            double progress = (double) i / (double) denominator;
            double eased = easing.apply(progress, 0.0D);
            values[i] = (float) eased;
        }
        return values;
    }

    private float[] computeBarTranslations(LeaderboardAnimations.Bar bar) {
        int frames = bar.frames();
        if (!bar.enabled() || frames <= 1) {
            return new float[] {0.0F};
        }
        EasingType easing = bar.curve();
        double start = bar.translationStart();
        double end = bar.translationEnd();
        float[] values = new float[frames];
        int denominator = Math.max(frames - 1, 1);
        for (int i = 0; i < frames; i++) {
            double progress = (double) i / (double) denominator;
            double eased = easing.apply(progress, 0.0D);
            values[i] = (float) (start + (end - start) * eased);
        }
        return values;
    }

    public float[] titleInScale() {
        return titleInScale;
    }

    public float[] titleOutScale() {
        return titleOutScale;
    }

    public float[] rowInOffsets() {
        return rowInOffsets;
    }

    public float[] rowOutOffsets() {
        return rowOutOffsets;
    }

    public float[] barScales() {
        return barScales;
    }

    public float[] barTranslations() {
        return barTranslations;
    }
}
