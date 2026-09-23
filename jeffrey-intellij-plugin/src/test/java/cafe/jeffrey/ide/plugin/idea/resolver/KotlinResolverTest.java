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

/**
 * Tests {@link KotlinResolver}'s FQCN heuristics and the filename fallback. Uses only platform +
 * Java-PSI APIs (no Kotlin plugin), matching the resolver itself.
 */
public class KotlinResolverTest extends BasePlatformTestCase {

    public void testDetectsTopLevelFacade() {
        assertTrue(KotlinResolver.isObviouslyKotlin("com.acme.UtilsKt"));
    }

    public void testDetectsCompanionAndSynthetics() {
        assertTrue(KotlinResolver.isObviouslyKotlin("com.acme.Foo$Companion"));
        assertTrue(KotlinResolver.isObviouslyKotlin("com.acme.Foo$DefaultImpls"));
        assertTrue(KotlinResolver.isObviouslyKotlin("com.acme.Foo$bar$1"));
    }

    public void testPlainJavaIsNotKotlin() {
        assertFalse(KotlinResolver.isObviouslyKotlin("com.acme.Foo"));
        assertFalse(KotlinResolver.isObviouslyKotlin("com.acme.Foo$Inner"));
    }

    public void testFilenameFallbackForFacade() {
        myFixture.configureByText("Utils.kt", "package com.acme\nfun greet() = \"hi\"\n");
        Navigation result = KotlinResolver.resolve(getProject(),
                new NavigateRequest(null, "com.acme.UtilsKt", null, 2, null));
        assertTrue(result instanceof Navigation.Found);
        Navigation.Found found = (Navigation.Found) result;
        assertEquals(Navigation.Kind.KOTLIN_FALLBACK, found.kind());
        assertEquals("JFR line 2 maps to 0-based 1", 1, found.line());
    }

    public void testNotFoundWhenNothingMatches() {
        Navigation result = KotlinResolver.resolve(getProject(),
                new NavigateRequest(null, "com.acme.GhostKt", null, 1, null));
        assertTrue(result instanceof Navigation.NotFound);
    }
}
