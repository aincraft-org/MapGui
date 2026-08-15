package de.flog99.mapgui.plugin.camera;

import de.flog99.mapgui.map.SavedMapPixels;
import de.flog99.mapgui.render.Texture;
import de.flog99.mapgui.render.TextureAtlas;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FramedMapsTest {

    private static final int SIZE = 128 * 128;

    @Test
    void unchangedPixelsReuseThePublishedTexture() throws Exception {
        FakeSavedMapPixels saved = new FakeSavedMapPixels();
        saved.put(7, pixels((byte) 4));
        TextureAtlas atlas = atlas();
        FramedMaps maps = new FramedMaps(saved);

        String first = maps.textureOf(7, atlas);
        Texture published = atlas.get(first);
        String second = maps.textureOf(7, atlas);

        assertEquals(first, second);
        assertSame(published, atlas.get(second));
        assertEquals(2, saved.reads);
    }

    @Test
    void changedPixelsRegenerateUnderTheSamePublishedName() throws Exception {
        FakeSavedMapPixels saved = new FakeSavedMapPixels();
        saved.put(7, pixels((byte) 4));
        TextureAtlas atlas = atlas();
        FramedMaps maps = new FramedMaps(saved);

        String name = maps.textureOf(7, atlas);
        Texture before = atlas.get(name);
        saved.put(7, pixels((byte) 5));

        assertEquals(name, maps.textureOf(7, atlas));
        Texture after = atlas.get(name);
        assertNotSame(before, after);
        assertTrue(after.argb()[0] != before.argb()[0]);
    }

    @Test
    void cacheRetainsOnlyBoundedMapIds() throws Exception {
        FakeSavedMapPixels saved = new FakeSavedMapPixels();
        TextureAtlas atlas = atlas();
        FramedMaps maps = new FramedMaps(saved);

        for (int mapId = 0; mapId < FramedMaps.CACHE_LIMIT + 2; mapId++) {
            saved.put(mapId, pixels((byte) (4 + mapId)));
            maps.textureOf(mapId, atlas);
        }

        assertEquals(FramedMaps.CACHE_LIMIT, maps.cachedMapCount());
    }

    private static TextureAtlas atlas() throws Exception {
        Constructor<?> constructor = TextureAtlas.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (TextureAtlas) constructor.newInstance(new Object[]{null});
    }

    private static byte[] pixels(byte value) {
        byte[] pixels = new byte[SIZE];
        java.util.Arrays.fill(pixels, value);
        return pixels;
    }

    private static final class FakeSavedMapPixels implements SavedMapPixels {
        private final Map<Integer, byte[]> maps = new HashMap<>();
        private int reads;

        void put(int mapId, byte[] pixels) {
            maps.put(mapId, pixels);
        }

        @Override
        public boolean write(int mapId, byte[] pixels) {
            maps.put(mapId, pixels.clone());
            return true;
        }

        @Override
        public byte[] read(int mapId) {
            reads++;
            byte[] pixels = maps.get(mapId);
            return pixels == null ? null : pixels.clone();
        }
    }
}
