/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.shared.common.model.workspace;

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
