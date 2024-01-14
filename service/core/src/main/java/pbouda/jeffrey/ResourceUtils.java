package pbouda.jeffrey;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class ResourceUtils {

    public static String readTextFile(String path) {
        InputStream instr = ResourceUtils.class.getResourceAsStream(path);
        try (var reader = new BufferedInputStream(instr)) {
            return new String(reader.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
