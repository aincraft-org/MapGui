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

    @Test
    void videoMaxFramesIsConfigured() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("walls.video-max-frames", 5);
        assertEquals(5, MapGuiConfig.from(config).wallVideoMaxFrames());
    }

    @Test
    void videoMaxDurationIsStoredAsMilliseconds() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("walls.video-max-duration", 30);
        assertEquals(30_000L, MapGuiConfig.from(config).wallVideoMaxDurationMs());
    }

    @Test
    void videoMaxBytesIsConfigured() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("walls.video-max-bytes", 10_000_000L);
        assertEquals(10_000_000L, MapGuiConfig.from(config).wallVideoMaxBytes());
    }

    @Test
    void cameraOperationalCeilingsPreserveDefaultsAndClampValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("camera.max-distance", 10_000);
        config.set("camera.max-entity-distance", 10_000);
        config.set("camera.limits.max-entities", 10_000);
        config.set("camera.limits.max-tile-entities", 10_000);
        config.set("camera.limits.tile-entity-distance", 10_000);

        var camera = MapGuiConfig.from(config).cameraTuning();
        assertEquals(512, camera.maxDistance());
        assertEquals(256, camera.maxEntityDistance());
        assertEquals(4096, camera.limits().mobs());
        assertEquals(4096, camera.limits().blockEntities());
        assertEquals(256, camera.limits().blockEntityDistance());
    }

    @Test
    void cameraOperationalCeilingsFloorNegativeValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("camera.max-distance", -10);
        config.set("camera.max-entity-distance", -10);
        config.set("camera.limits.max-entities", -10);
        config.set("camera.limits.max-tile-entities", -10);
        config.set("camera.limits.tile-entity-distance", -10);

        var camera = MapGuiConfig.from(config).cameraTuning();
        assertEquals(1, camera.maxDistance());
        assertEquals(1, camera.maxEntityDistance());
        assertEquals(0, camera.limits().mobs());
        assertEquals(0, camera.limits().blockEntities());
        assertEquals(0, camera.limits().blockEntityDistance());
    }
}
