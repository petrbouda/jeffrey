/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

export interface ParsedJavaMethod {
    packageName: string | null;
    className: string;
    methodName: string;
    shortForm: string;
}

const JAVA_FRAME_TYPES = new Set([
    'JIT_COMPILED',
    'INLINED',
    'INTERPRETED',
    'C1_COMPILED'
]);

export default class JavaMethodParser {

    /**
     * Checks if the frame type is a Java frame that can be parsed.
     */
    static isJavaFrame(frameType: string): boolean {
        return JAVA_FRAME_TYPES.has(frameType);
    }

    /**
     * Checks if a string starts with an uppercase letter (indicating a class name).
     */
    private static startsWithUppercase(str: string): boolean {
        if (!str || str.length === 0) return false;
        const firstChar = str[0];
        return firstChar >= 'A' && firstChar <= 'Z';
    }

    /**
     * Parses a Java method signature into its components.
     * Example: "com.example.MyClass.myMethod(String, int)" ->
     *   { packageName: "com.example", className: "MyClass", methodName: "myMethod(String, int)" }
     * Also handles # separator: "com.example.MyClass#myMethod"
     */
    static parse(title: string): ParsedJavaMethod | null {
        if (!title || title.length === 0) {
            return null;
        }

        let workingTitle = title;
        let methodName: string;
        let classAndPackage: string;

        // Check for # separator first (e.g., "org.apache.MyClass#process")
        const hashIndex = workingTitle.indexOf('#');
        if (hashIndex !== -1) {
            classAndPackage = workingTitle.substring(0, hashIndex);
            methodName = workingTitle.substring(hashIndex + 1);
        } else {
            // Find method arguments (everything from first '(' to the end)
            const argsStart = workingTitle.indexOf('(');
            let argsPart = '';

            if (argsStart !== -1) {
                argsPart = workingTitle.substring(argsStart);
                workingTitle = workingTitle.substring(0, argsStart);
            }

            // Split by dots - last part is the method name
            const parts = workingTitle.split('.');

            if (parts.length < 2) {
                return {
                    packageName: null,
                    className: '',
                    methodName: title,
                    shortForm: title
                };
            }

            methodName = parts[parts.length - 1] + argsPart;
            classAndPackage = parts.slice(0, parts.length - 1).join('.');
        }

        // Now parse classAndPackage to separate package from class
        const parts = classAndPackage.split('.');

        if (parts.length === 0) {
            return {
                packageName: null,
                className: '',
                methodName: methodName,
                shortForm: methodName
            };
        }

        if (parts.length === 1) {
            return {
                packageName: null,
                className: parts[0],
                methodName: methodName,
                shortForm: parts[0] + '.' + methodName
            };
        }

        // Find where the class starts - first part that starts with uppercase
        // Package names are lowercase, class names start with uppercase
        let classStartIndex = -1;
        for (let i = 0; i < parts.length; i++) {
            if (this.startsWithUppercase(parts[i])) {
                classStartIndex = i;
                break;
            }
        }

        // If no uppercase part found, assume last part is the class
        if (classStartIndex === -1) {
            classStartIndex = parts.length - 1;
        }

        // Class name is everything from classStartIndex to the end
        const className = parts.slice(classStartIndex).join('.');

        // Package is everything before the class
        const packageName = classStartIndex > 0 ? parts.slice(0, classStartIndex).join('.') : null;

        return {
            packageName,
            className,
            methodName,
            shortForm: className + '.' + methodName
        };
    }
}
