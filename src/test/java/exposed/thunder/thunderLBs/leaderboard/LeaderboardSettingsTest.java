package exposed.thunder.thunderLBs.leaderboard;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaderboardSettingsTest {
    @Test
    void boardScaleAndRowYOffsetAreSerialized() {
        LeaderboardSettings settings = new LeaderboardSettings(
                10, 160L, 200L, 2L, 1L, true, 1.75D, -1.2D);
        YamlConfiguration yaml = new YamlConfiguration();

        settings.serialize(yaml);

        assertEquals(1.75D, yaml.getDouble("board-scale"));
        assertEquals(-1.2D, yaml.getDouble("row-y-offset"));
    }
}
