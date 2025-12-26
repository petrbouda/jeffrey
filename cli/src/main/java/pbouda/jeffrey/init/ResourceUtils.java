package pbouda.jeffrey.init;

import java.io.IOException;
import java.io.InputStream;

public abstract class ResourceUtils {

    public static String readFromClasspath(String resourcePath) {
        ClassLoader classLoader = ResourceUtils.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream != null) {
                byte[] bytes = inputStream.readAllBytes();
                return new String(bytes);
            } else {
                throw new RuntimeException("Cannot find the resource=" + resourcePath);
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Cannot find the resource=" + resourcePath + " error=" + e.getMessage(), e);
        }
    }
}
