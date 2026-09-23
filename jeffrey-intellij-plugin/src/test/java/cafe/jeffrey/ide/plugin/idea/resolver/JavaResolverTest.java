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

package cafe.jeffrey.ide.plugin.idea.resolver;

import cafe.jeffrey.ide.plugin.idea.dto.NavigateRequest;
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

    public void testHasMethodFindsDeclaredMethod() {
        myFixture.configureByText("Foo.java", FOO_JAVA);
        assertTrue(JavaResolver.hasMethod(getProject(), "com.acme.Foo", "bar"));
    }

    public void testHasMethodFalseForMissingMethodOrClass() {
        myFixture.configureByText("Foo.java", FOO_JAVA);
        assertFalse("method not declared", JavaResolver.hasMethod(getProject(), "com.acme.Foo", "noSuchMethod"));
        assertFalse("class not found", JavaResolver.hasMethod(getProject(), "com.acme.Nope", "bar"));
    }
}
