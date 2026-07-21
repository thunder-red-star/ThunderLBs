package exposed.thunder.thunderLBs.leaderboard;

import exposed.thunder.thunderLBs.animation.EasingType;
import exposed.thunder.thunderLBs.config.PluginConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardAnimationsTest {
    @Test
    void directionalEnablementSurvivesSerialization() {
        PluginConfig.Animation defaults = defaults();
        LeaderboardAnimations animations = LeaderboardAnimations.defaults(defaults);
        animations.title().setInEnabled(false);
        animations.row().setOutEnabled(false);

        YamlConfiguration yaml = new YamlConfiguration();
        animations.serialize(yaml);
        LeaderboardAnimations loaded = LeaderboardAnimations.from(yaml, defaults);

        assertFalse(loaded.title().inEnabled());
        assertTrue(loaded.title().outEnabled());
        assertTrue(loaded.row().inEnabled());
        assertFalse(loaded.row().outEnabled());
    }

    @Test
    void legacyEnabledFlagStillControlsBothDirections() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.createSection("title").set("enabled", false);
        yaml.createSection("rows").set("enabled", false);

        LeaderboardAnimations loaded = LeaderboardAnimations.from(yaml, defaults());

        assertFalse(loaded.title().inEnabled());
        assertFalse(loaded.title().outEnabled());
        assertFalse(loaded.row().inEnabled());
        assertFalse(loaded.row().outEnabled());
    }

    private static PluginConfig.Animation defaults() {
        return new PluginConfig.Animation(
                new PluginConfig.Animation.Title(20, 20, EasingType.EASE_OUT_BACK,
                        EasingType.EASE_IN_SINE, 1.5D, 4.5D),
                new PluginConfig.Animation.Row(20, 20, EasingType.EASE_OUT_CUBIC,
                        EasingType.EASE_IN_CUBIC, -1.25D, 1.25D),
                new PluginConfig.Animation.Bar(10, EasingType.LINEAR, -1.0D, 0.0D)
        );
    }
}
