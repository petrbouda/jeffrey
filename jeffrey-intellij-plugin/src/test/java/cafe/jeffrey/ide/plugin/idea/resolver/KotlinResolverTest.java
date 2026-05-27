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
