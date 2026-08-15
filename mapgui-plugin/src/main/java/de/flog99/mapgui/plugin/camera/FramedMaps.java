package de.flog99.mapgui.plugin.camera;

import de.flog99.mapgui.map.SavedMapPixels;
import de.flog99.mapgui.render.TextureAtlas;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The picture on a map somebody has hung in an item frame.
 *
 * <p>A map's pixels live in the world's own saved data - one byte of palette index per pixel, the same array the
 * ordinary map renderer paints - so they are read from there and handed to {@link MapPicture}, which is where a map
 * of any kind becomes a texture.
 */
final class FramedMaps {

    /** Kept apart from the asset names, since this is a picture no pack could supply. */
    private static final String NAME = "mapgui/framed_map/";

    /** Limits retained pixel snapshots so a world with many map ids cannot grow this cache without bound. */
    static final int CACHE_LIMIT = 64;

    private final SavedMapPixels saved;
    private final LinkedHashMap<Integer, byte[]> pixels = new LinkedHashMap<>(16, 0.75f, true);

    FramedMaps(SavedMapPixels saved) {
        this.saved = saved;
    }

    /** Visible for focused package tests; the production cache remains bounded by {@link #CACHE_LIMIT}. */
    int cachedMapCount() {
        return pixels.size();
    }

    /**
     * Publishes one map's pixels into the atlas and hands back the name to draw them under.
     *
     * <p>Null when this server will not give them up, or for a map id nothing has ever drawn. The caller draws the
     * frame and leaves the picture out, which is what a map in a frame looked like before any of this.
     */
    synchronized String textureOf(int mapId, TextureAtlas atlas) {
        byte[] current = saved == null ? null : saved.read(mapId);
        if (current == null || current.length != MapPicture.SIZE * MapPicture.SIZE) return null;

        byte[] prior = pixels.get(mapId);
        if (!Arrays.equals(prior, current)) {
            MapPicture.publish(NAME + mapId, current, atlas);
            pixels.put(mapId, current.clone());
            trim();
        }
        return NAME + mapId;
    }

    private void trim() {
        while (pixels.size() > CACHE_LIMIT) {
            Iterator<Map.Entry<Integer, byte[]>> entries = pixels.entrySet().iterator();
            entries.next();
            entries.remove();
        }
    }
}
