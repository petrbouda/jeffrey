package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import cafe.jeffrey.jfrparser.api.type.JfrStackFrameImpl;
import cafe.jeffrey.jfrparser.api.type.JfrThreadImpl;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.List;

public abstract class FlamegraphMapperUtils {

    public static List<JfrStackFrameImpl> getStackFrames(ResultSet rs) throws SQLException {
        Array framesArray = rs.getArray("frames");
        List<JfrStackFrameImpl> frames = null;
        if (framesArray != null) {
            Object[] objects = (Object[]) framesArray.getArray();
            frames = new ArrayList<>(objects.length);

            for (Object obj : objects) {
                Struct struct = (Struct) obj;
                Object[] attrs = struct.getAttributes();
                frames.add(new JfrStackFrameImpl(
                        (String) attrs[0],  // class_name
                        (String) attrs[1],  // method_name
                        (String) attrs[2],  // type
                        (Integer) attrs[3], // line
                        (Integer) attrs[4]  // bci
                ));
            }
        }
        return frames;
    }

    /**
     * Converts DuckDB array to primitive long array.
     * DuckDB JDBC may return different array types depending on context.
     */
    public static long[] toFrameHashArray(Array frameHashesArray) throws SQLException {
        Object arrayObj = frameHashesArray.getArray();
        return switch (arrayObj) {
            case long[] primitiveArray -> primitiveArray;
            case Long[] longArray -> {
                long[] result = new long[longArray.length];
                for (int i = 0; i < longArray.length; i++) {
                    result[i] = longArray[i];
                }
                yield result;
            }
            case Object[] objectArray -> {
                // DuckDB may return Object[] with Long/Number elements
                long[] result = new long[objectArray.length];
                for (int i = 0; i < objectArray.length; i++) {
                    result[i] = ((Number) objectArray[i]).longValue();
                }
                yield result;
            }
            default -> throw new SQLException("Unexpected array type: " + arrayObj.getClass());
        };
    }

    public static JfrThreadImpl getThread(ResultSet rs) throws SQLException {
        Struct threadStruct = (Struct) rs.getObject("thread");
        if (threadStruct != null) {
            Object[] attrs = threadStruct.getAttributes();
            if (attrs != null && attrs.length >= 4) {
                long osId = toLong(attrs[0]);
                long javaId = toLong(attrs[1]);
                String name = (String) attrs[2];
                boolean isVirtual = toBoolean(attrs[3]);

                return new JfrThreadImpl(osId, javaId, name, isVirtual);
            }
        }
        return null;
    }

    private static long toLong(Object value) {
        if (value == null) {
            return -1L;
        }
        return ((Number) value).longValue();
    }

    private static boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        return (Boolean) value;
    }
}
