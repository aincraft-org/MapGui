package de.flog99.mapgui.plugin.camera;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkinCacheTest {

    @Test
    void failedDownloadIsNotRetriedUntilBackoffExpires() throws Exception {
        URL skin = new URL("http://example.invalid/skin.png");
        PlayerProfile profile = newProfile(skin);
        AtomicInteger fetches = new AtomicInteger();
        SkinCache.Clock clock = new SkinCache.Clock() {
            private long now;
            public long millis() { return now; }
            public void advance(long millis) { now += millis; }
        };
        SkinCache cache = new SkinCache(clock, (url) -> {
            fetches.incrementAndGet();
            throw new IllegalStateException("offline");
        });
        assertNull(cache.nameFor(profile));
        awaitFetches(fetches, 1);
        assertNull(cache.nameFor(profile));
        assertEquals(1, fetches.get());

        clock.advance(SkinCache.RETRY_DELAYS_MILLIS[0]);
        assertNull(cache.nameFor(profile));
        awaitFetches(fetches, 2);
        assertEquals(2, fetches.get());
    }

    private static PlayerProfile newProfile(URL skin) {
        PlayerProfile profile = (PlayerProfile) java.lang.reflect.Proxy.newProxyInstance(
                PlayerProfile.class.getClassLoader(), new Class<?>[]{PlayerProfile.class},
                (proxy, method, args) -> method.getName().equals("getTextures") ? textures(skin) : null);
        return profile;
    }

    private static Object textures(URL skin) {
        return java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.profile.PlayerTextures.class.getClassLoader(),
                new Class<?>[]{org.bukkit.profile.PlayerTextures.class},
                (proxy, method, args) -> method.getName().equals("getSkin") ? skin : null);
    }

    private static void awaitFetches(AtomicInteger fetches, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (fetches.get() < expected && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals(expected, fetches.get());
    }
}
