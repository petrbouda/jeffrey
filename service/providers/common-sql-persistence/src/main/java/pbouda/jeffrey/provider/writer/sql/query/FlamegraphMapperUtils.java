package pbouda.jeffrey.provider.writer.sql.query;

import pbouda.jeffrey.jfrparser.db.type.DbJfrStackFrame;
import pbouda.jeffrey.jfrparser.db.type.DbJfrThread;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.List;

public abstract class FlamegraphMapperUtils {

    public static List<DbJfrStackFrame> getStackFrames(ResultSet rs) throws SQLException {
        Array framesArray = rs.getArray("frames");
        List<DbJfrStackFrame> frames = null;
        if (framesArray != null) {
            Object[] objects = (Object[]) framesArray.getArray();
            frames = new ArrayList<>(objects.length);

            for (Object obj : objects) {
                Struct struct = (Struct) obj;
                Object[] attrs = struct.getAttributes();
                frames.add(new DbJfrStackFrame(
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

    public static DbJfrThread getThread(ResultSet rs) throws SQLException {
        Struct threadStruct = (Struct) rs.getObject("thread");
        if (threadStruct != null) {
            Object[] attrs = threadStruct.getAttributes();
            return new DbJfrThread(
                    (Long) attrs[0],   // os_id
                    (Long) attrs[1],   // java_id
                    (String) attrs[2], // name
                    (Boolean) attrs[3] // is_virtual
            );
        }
        return null;
    }
}
