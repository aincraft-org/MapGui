package de.flog99.mapgui.plugin.camera;

import de.flog99.mapgui.plugin.MapGuiConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CameraServiceTest {

    @Test
    void closeIsIdempotent() {
        File data = new File("build/camera-service-test");
        Plugin plugin = (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDataFolder" -> data;
                    case "getLogger" -> Logger.getAnonymousLogger();
                    default -> defaultValue(method.getReturnType());
                });

        CameraAssetStore assets = new CameraAssetStore(plugin, List.of(), false, false);
        CameraService service = new CameraService(
                plugin, assets, null, null, null, null,
                MapGuiConfig.from(new YamlConfiguration()).cameraTuning());

        assertDoesNotThrow(service::close);
        assertDoesNotThrow(service::close);
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
}
