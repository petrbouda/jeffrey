package jeffrey.testapp.server.leak;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeHolder {

    public static final Unsafe INSTANCE;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            INSTANCE = (Unsafe) f.get(null);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
