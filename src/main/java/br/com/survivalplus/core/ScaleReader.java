package br.com.survivalplus.core;

import java.lang.reflect.Method;
import org.bukkit.World;

/** Public API only. Missing/unusable API is a blocking condition, never a guessed scale. */
final class ScaleReader {
    private final Method getter;

    ScaleReader() {
        Method found;
        try {
            found = World.class.getMethod("getCoordinateScale");
        } catch (NoSuchMethodException e) {
            found = null;
        }
        getter = found;
    }

    double read(World world) throws ReflectiveOperationException {
        if (getter == null) throw new NoSuchMethodException("World#getCoordinateScale indisponível na API pública.");
        Object value = getter.invoke(world);
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() <= 0) {
            throw new IllegalStateException("API retornou coordinate_scale inválido.");
        }
        return n.doubleValue();
    }
}
