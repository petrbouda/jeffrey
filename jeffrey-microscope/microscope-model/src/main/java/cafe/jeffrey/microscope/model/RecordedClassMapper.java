/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.model;

public abstract class RecordedClassMapper {
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
