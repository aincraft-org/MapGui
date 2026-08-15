package de.flog99.mapgui.plugin.camera;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobTexturesTest {
    @Test
    void repeatedVariantLookupKeepsTheSameTextureAndCachesAccessor() {
        Entity entity = (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[]{VariantEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getVariant")) return SampleVariant.BLUE;
                    return defaultValue(method.getReturnType());
                });

        assertEquals("blue", MobTextures.variantOf(entity, "cat"));
        assertEquals("blue", MobTextures.variantOf(entity, "cat"));
        assertEquals(1, MobTextures.cachedAccessorCount(entity, "getVariant"));
    }

    private interface VariantEntity extends Entity {
        SampleVariant getVariant();
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private enum SampleVariant {
        BLUE
    }
}
