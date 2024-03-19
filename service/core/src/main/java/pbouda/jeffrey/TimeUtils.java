package pbouda.jeffrey;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public abstract class TimeUtils {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public static String currentDateTime() {
        return FORMATTER.format(LocalDateTime.now());
    }
}
