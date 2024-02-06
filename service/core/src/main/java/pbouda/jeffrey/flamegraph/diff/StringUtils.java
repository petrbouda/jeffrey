package pbouda.jeffrey.flamegraph.diff;

public abstract class StringUtils {

    public static String escape(String s) {
        if (s.indexOf('\\') >= 0) s = s.replace("\\", "\\\\");
        if (s.indexOf('\'') >= 0) s = s.replace("'", "\\'");
        return s;
    }
}
