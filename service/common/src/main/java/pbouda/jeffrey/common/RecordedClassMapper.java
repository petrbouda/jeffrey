package pbouda.jeffrey.common;

import jdk.jfr.consumer.RecordedClass;

public abstract class RecordedClassMapper {

    // https://docs.oracle.com/en/java/javase/21/docs/specs/jni/types.html
    public static String map(RecordedClass clazz) {
        return map(clazz.getName());
    }

    public static String map(String typeName) {
        return switch (removeLeadingBrackets(typeName)) {
            case "[Z" -> "boolean[]";
            case "[B" -> "byte[]";
            case "[C" -> "char[]";
            case "[S" -> "short[]";
            case "[I" -> "int[]";
            case "[J" -> "long[]";
            case "[F" -> "float[]";
            case "[D" -> "double[]";
            case String s when s.startsWith("[L") -> typeName.substring(2, typeName.length() - 1) + "[]";
            default -> typeName;
        };
    }

    private static String removeLeadingBrackets(String value) {
        if (value.startsWith("[[")) {
            int index;
            for (index = 0; index < value.length() - 1; index++) {
                if (value.charAt(index) != '[') {
                    break;
                }
            }
            return "[" + value.substring(index);
        } else {
            return value;
        }
    }
}
