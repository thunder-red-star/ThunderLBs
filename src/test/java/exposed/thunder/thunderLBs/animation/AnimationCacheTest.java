package exposed.thunder.thunderLBs.animation;

import exposed.thunder.thunderLBs.config.PluginConfig;
import exposed.thunder.thunderLBs.leaderboard.LeaderboardAnimations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationCacheTest {
    @Test
    void titleEntranceKeepsItsOvershoot() {
        AnimationCache cache = new AnimationCache(animations(EasingType.EASE_IN_SINE));

        float[] frames = cache.titleInScale();

        assertTrue(max(frames) > 1.5F, "title entrance should keep its configured bounce");
    }

    @Test
    void titleExitScalesDownToZero() {
        AnimationCache cache = new AnimationCache(animations(EasingType.EASE_IN_SINE));

        float[] frames = cache.titleOutScale();

        assertEquals(1.5F, frames[0]);
        assertEquals(0.0F, frames[frames.length - 1]);
        for (int i = 1; i < frames.length; i++) {
            assertTrue(frames[i] <= frames[i - 1], "title exit scale must not increase");
        }
    }

    @Test
    void titleExitDoesNotBounceWhenUsingBackEasing() {
        AnimationCache cache = new AnimationCache(animations(EasingType.EASE_OUT_BACK));

        float[] frames = cache.titleOutScale();

        for (int i = 1; i < frames.length; i++) {
            assertTrue(frames[i] <= frames[i - 1], "title exit scale must not increase");
            assertTrue(frames[i] >= 0.0F, "title exit scale must not invert");
        }
    }

    @Test
    void rowExitMovesToTheRight() {
        AnimationCache cache = new AnimationCache(animations(EasingType.EASE_IN_SINE));

        float[] frames = cache.rowOutOffsets();

        assertEquals(0.0F, frames[0]);
        assertEquals(1.25F, frames[frames.length - 1]);
    }

    @Test
    void entranceAndExitCanBeDisabledIndependently() {
        LeaderboardAnimations animations = animations(EasingType.EASE_IN_SINE);
        animations.title().setInEnabled(false);
        animations.row().setOutEnabled(false);

        AnimationCache cache = new AnimationCache(animations);

        assertEquals(1, cache.titleInScale().length);
        assertEquals(20, cache.titleOutScale().length);
        assertEquals(20, cache.rowInOffsets().length);
        assertEquals(1, cache.rowOutOffsets().length);
    }

    private static float max(float[] values) {
        float max = Float.NEGATIVE_INFINITY;
        for (float value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static LeaderboardAnimations animations(EasingType outEasing) {
        PluginConfig.Animation defaults = new PluginConfig.Animation(
                new PluginConfig.Animation.Title(20, 20, EasingType.EASE_OUT_BACK, outEasing, 1.5D, 4.5D),
                new PluginConfig.Animation.Row(20, 20, EasingType.EASE_OUT_CUBIC, EasingType.EASE_IN_CUBIC,
                        -1.25D, 1.25D),
                new PluginConfig.Animation.Bar(10, EasingType.LINEAR, -1.0D, 0.0D)
        );
        return LeaderboardAnimations.defaults(defaults);
    }
}
