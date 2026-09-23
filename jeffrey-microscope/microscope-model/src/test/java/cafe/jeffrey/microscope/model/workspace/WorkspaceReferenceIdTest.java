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

package cafe.jeffrey.microscope.model.workspace;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceReferenceIdTest {

    @Nested
    class UserReferenceIds {

        @Test
        void plainAlphanumericIsValid() {
            assertTrue(WorkspaceReferenceId.isValid("uat"));
            assertTrue(WorkspaceReferenceId.isValid("prod-2025"));
            assertTrue(WorkspaceReferenceId.isValid("MyWorkspace"));
        }

        @Test
        void emptyOrNullIsInvalid() {
            assertFalse(WorkspaceReferenceId.isValid(null));
            assertFalse(WorkspaceReferenceId.isValid(""));
            assertFalse(WorkspaceReferenceId.isValid("   "));
        }

        @Test
        void shorterThan3CharsIsInvalid() {
            assertFalse(WorkspaceReferenceId.isValid("ab"));
        }

        @Test
        void longerThan64CharsIsInvalid() {
            assertFalse(WorkspaceReferenceId.isValid("a".repeat(65)));
        }

        @Test
        void leadingOrTrailingDashIsInvalid() {
            assertFalse(WorkspaceReferenceId.isValid("-leading"));
            assertFalse(WorkspaceReferenceId.isValid("trailing-"));
        }

        @Test
        void disallowedCharsAreInvalid() {
            assertFalse(WorkspaceReferenceId.isValid("with space"));
            assertFalse(WorkspaceReferenceId.isValid("with_underscore"));
            assertFalse(WorkspaceReferenceId.isValid("with.dot"));
        }
    }

    @Nested
    class SystemReferenceIds {

        @Test
        void dollarPrefixedIsValid() {
            assertTrue(WorkspaceReferenceId.isValid("$default"));
            assertTrue(WorkspaceReferenceId.isValid("$archive"));
            assertTrue(WorkspaceReferenceId.isValid("$ws-2025"));
        }

        @Test
        void dollarOnlyOrTooShortIsInvalid() {
            assertFalse(WorkspaceReferenceId.isValid("$"));
            assertFalse(WorkspaceReferenceId.isValid("$a"));
        }

        @Test
        void dollarInMiddleIsInvalid() {
            assertFalse(WorkspaceReferenceId.isValid("foo$bar"));
            assertFalse(WorkspaceReferenceId.isValid("foo$"));
        }

        @Test
        void doubleDollarIsInvalid() {
            assertFalse(WorkspaceReferenceId.isValid("$$default"));
        }

        @Test
        void isSystemReturnsTrueForDollarPrefix() {
            assertTrue(WorkspaceReferenceId.isSystem("$default"));
            assertTrue(WorkspaceReferenceId.isSystem("$anything"));
        }

        @Test
        void isSystemReturnsFalseForUserIds() {
            assertFalse(WorkspaceReferenceId.isSystem("default"));
            assertFalse(WorkspaceReferenceId.isSystem("uat"));
            assertFalse(WorkspaceReferenceId.isSystem(null));
            assertFalse(WorkspaceReferenceId.isSystem(""));
        }
    }

    @Nested
    class Validate {

        @Test
        void validatePassesForValidIds() {
            assertDoesNotThrow(() -> WorkspaceReferenceId.validate("uat"));
            assertDoesNotThrow(() -> WorkspaceReferenceId.validate("$default"));
        }

        @Test
        void validateThrowsForBlank() {
            assertThrows(IllegalArgumentException.class, () -> WorkspaceReferenceId.validate(null));
            assertThrows(IllegalArgumentException.class, () -> WorkspaceReferenceId.validate(""));
        }

        @Test
        void validateThrowsForInvalidPattern() {
            assertThrows(IllegalArgumentException.class, () -> WorkspaceReferenceId.validate("ab"));
            assertThrows(IllegalArgumentException.class, () -> WorkspaceReferenceId.validate("foo$bar"));
        }
    }
}
