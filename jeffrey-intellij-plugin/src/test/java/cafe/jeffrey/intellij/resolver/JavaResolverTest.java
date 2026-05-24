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

package cafe.jeffrey.intellij.resolver;

import cafe.jeffrey.intellij.dto.NavigateRequest;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** PSI resolution tests for {@link JavaResolver}, backed by a light in-process IDE fixture. */
public class JavaResolverTest extends BasePlatformTestCase {

    private static final String FOO_JAVA = """
            package com.acme;
            public class Foo {
                void bar() {
                    System.out.println("hi");
                }
            }
            """;

    public void testResolvesByJfrLine() {
        myFixture.configureByText("Foo.java", FOO_JAVA);
        Navigation result = JavaResolver.resolve(getProject(),
                new NavigateRequest(null, "com.acme.Foo", "bar", 4, null));
        assertTrue(result instanceof Navigation.Found);
        Navigation.Found found = (Navigation.Found) result;
        assertEquals("JFR line 4 maps to 0-based 3", 3, found.line());
        assertEquals(Navigation.Kind.JAVA_LINE, found.kind());
        assertFalse(found.imprecise());
    }

    public void testFallsBackToMethodWhenNoLine() {
        myFixture.configureByText("Foo.java", FOO_JAVA);
        Navigation result = JavaResolver.resolve(getProject(),
                new NavigateRequest(null, "com.acme.Foo", "bar", -1, null));
        assertTrue(result instanceof Navigation.Found);
        assertEquals(Navigation.Kind.JAVA_PRECISE, ((Navigation.Found) result).kind());
    }

    public void testFallsBackToClassWhenMethodMissing() {
        myFixture.configureByText("Foo.java", FOO_JAVA);
        Navigation result = JavaResolver.resolve(getProject(),
                new NavigateRequest(null, "com.acme.Foo", "noSuchMethod", -1, null));
        assertTrue(result instanceof Navigation.Found);
        assertTrue("class-declaration fallback is imprecise", ((Navigation.Found) result).imprecise());
    }

    public void testNotFoundForMissingClass() {
        Navigation result = JavaResolver.resolve(getProject(),
                new NavigateRequest(null, "com.acme.DoesNotExist", null, 1, null));
        assertTrue(result instanceof Navigation.NotFound);
    }

    public void testExists() {
        myFixture.configureByText("Foo.java", FOO_JAVA);
        assertTrue(JavaResolver.exists(getProject(), "com.acme.Foo"));
        assertFalse(JavaResolver.exists(getProject(), "com.acme.Nope"));
    }
}
