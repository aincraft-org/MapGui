package de.flog99.mapgui.plugin;

import de.flog99.mapgui.MapSurface;
import de.flog99.mapgui.WallLayout;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapGuiConfigTest {

    @Test
    void videoSizeIsClampedToMaxWallEdge() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("walls.video-size", 4096);

        assertEquals(WallLayout.MAX_SIDE * MapSurface.TILE, MapGuiConfig.from(config).wallVideoSize());
    }

    @Test
    void videoSizeHonoursFloor() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("walls.video-size", 64);

        assertEquals(128, MapGuiConfig.from(config).wallVideoSize());
    }

    @Test
    void videoSizeUsesDefault() {
        assertEquals(256, MapGuiConfig.from(new YamlConfiguration()).wallVideoSize());
    }
}
