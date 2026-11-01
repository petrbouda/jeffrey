/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.mcp.McpToolOutput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedOperationTest {

    /**
     * Both tools that run through here declare no large result, so a client holds their error text to
     * its default limit too; a long failure and a long snapshot together stay under it, cut visibly.
     */
    @Test
    void aFailedOperationsTextStaysWithinTheUndeclaredLimit() {
        String text = BoundedOperation.failureText(OperationKind.JVM_AUTO_ANALYSIS,
                "x".repeat(McpToolOutput.MAX_CHARS), "{\"state\":\"FAILED\",\"detail\":\"" + "y".repeat(McpToolOutput.MAX_CHARS) + "\"}");

        assertTrue(text.length() <= McpToolOutput.UNDECLARED_MAX_CHARS, "length " + text.length());
        assertTrue(text.contains("TRUNCATED"), "the cut is announced");
        assertTrue(text.startsWith("The JVM_AUTO_ANALYSIS operation failed: "), text.substring(0, 80));
    }

    @Test
    void aShortFailureIsCarriedWhole() {
        String text = BoundedOperation.failureText(OperationKind.JVM_AUTO_ANALYSIS, "the rules could not read the file",
                "{\"state\":\"FAILED\"}");

        assertTrue(text.contains("the rules could not read the file") && text.endsWith("{\"state\":\"FAILED\"}"), text);
    }
}
