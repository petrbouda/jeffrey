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

package cafe.jeffrey.ide.plugin.idea.recording;

import cafe.jeffrey.ide.plugin.idea.AnalyzeInMicroscopeAction;
import cafe.jeffrey.ide.plugin.idea.settings.JeffreySettings;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * The address "Analyze in Microscope" opens: the configured base, the {@code /quick-open?path=}
 * template, and the absolute path encoded into one query parameter.
 *
 * <p>The action builds it inline in {@code actionPerformed}, which needs a selected
 * {@code VirtualFile} and ends in a real browser, so what is driven here is the two halves it is
 * made of. The template is read off the action rather than copied, so a change to the endpoint is a
 * failure here rather than a link that silently stops resolving.
 */
public class AnalyzeInMicroscopeActionTest {

    private static final String QUICK_OPEN_FIELD = "QUICK_OPEN_PATH";
    private static final String EXPECTED_TEMPLATE = "/quick-open?path=";

    private static final String CONFIGURED_URL = "http://192.168.1.10:9000";
    private static final String PLAIN_PATH = "/home/petr/recordings/run.jfr";
    private static final String SPACED_PATH = "/home/petr/My Recordings/run.jfr";
    private static final String PLUS_PATH = "/home/petr/run+1.jfr";
    private static final String ACCENTED_PATH = "/home/petr/nahr\u00e1vky/b\u011bh.jfr";
    private static final String WINDOWS_PATH = "C:\\Users\\petr\\run.jfr";

    @Test
    public void opensQuickOpenUnderTheConfiguredAddress() {
        assertEquals(
                CONFIGURED_URL + EXPECTED_TEMPLATE + "%2Fhome%2Fpetr%2Frecordings%2Frun.jfr",
                quickOpenUrl(CONFIGURED_URL, PLAIN_PATH));
    }

    /** The template the action holds privately; the whole link hangs off it. */
    @Test
    public void pinsTheQuickOpenTemplate() {
        assertEquals(EXPECTED_TEMPLATE, quickOpenTemplate());
    }

    /**
     * The path travels as one query parameter, so every separator in it is encoded. A raw path would
     * end the parameter at the first {@code &} or {@code #} in a directory name.
     */
    @Test
    public void encodesTheWholePathIntoOneParameter() {
        assertEquals(
                EXPECTED_TEMPLATE + "%2Fhome%2Fpetr%2FMy+Recordings%2Frun.jfr",
                quickOpenSuffix(SPACED_PATH));
        assertEquals(
                EXPECTED_TEMPLATE + "%2Fhome%2Fpetr%2Frun%2B1.jfr",
                quickOpenSuffix(PLUS_PATH));
    }

    /** Non-ASCII goes over as UTF-8 percent-escapes, which is what Microscope decodes it back from. */
    @Test
    public void encodesNonAsciiAsUtf8() {
        assertEquals(
                EXPECTED_TEMPLATE + "%2Fhome%2Fpetr%2Fnahr%C3%A1vky%2Fb%C4%9Bh.jfr",
                quickOpenSuffix(ACCENTED_PATH));
    }

    /**
     * A Windows path survives too. IntelliJ hands out {@code C:/Users/...} for a local file, so the
     * backslash form arrives only from a caller that built the string itself — it still has to
     * encode rather than escape anything.
     */
    @Test
    public void encodesAWindowsStylePath() {
        assertEquals(
                EXPECTED_TEMPLATE + "C%3A%5CUsers%5Cpetr%5Crun.jfr",
                quickOpenSuffix(WINDOWS_PATH));
    }

    @Test
    public void trimsATrailingSlashFromTheConfiguredAddress() {
        assertEquals("http://localhost:9000", microscopeUrl("http://localhost:9000/"));
    }

    /** A base with a path keeps it — only the slash that would double up in the link comes off. */
    @Test
    public void keepsAPathPrefixOnTheConfiguredAddress() {
        assertEquals("https://jeffrey.internal/microscope", microscopeUrl("https://jeffrey.internal/microscope/"));
    }

    @Test
    public void trimsSurroundingWhitespace() {
        assertEquals("http://localhost:9000", microscopeUrl("  http://localhost:9000/  "));
    }

    /**
     * A cleared field sends the developer to the usual address rather than building a link that
     * cannot resolve.
     */
    @Test
    public void fallsBackToTheDefaultForAnEmptyAddress() {
        assertEquals(JeffreySettings.DEFAULT_MICROSCOPE_URL, microscopeUrl(null));
        assertEquals(JeffreySettings.DEFAULT_MICROSCOPE_URL, microscopeUrl(""));
        assertEquals(JeffreySettings.DEFAULT_MICROSCOPE_URL, microscopeUrl("   "));
    }

    private static String quickOpenUrl(String configuredUrl, String path) {
        return microscopeUrl(configuredUrl) + quickOpenSuffix(path);
    }

    private static String quickOpenSuffix(String path) {
        return quickOpenTemplate() + URLEncoder.encode(path, StandardCharsets.UTF_8);
    }

    private static String microscopeUrl(String configuredUrl) {
        JeffreySettings settings = new JeffreySettings();
        settings.setMicroscopeUrl(configuredUrl);
        return settings.microscopeUrl();
    }

    private static String quickOpenTemplate() {
        try {
            Field field = AnalyzeInMicroscopeAction.class.getDeclaredField(QUICK_OPEN_FIELD);
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (ReflectiveOperationException e) {
            fail("AnalyzeInMicroscopeAction no longer declares " + QUICK_OPEN_FIELD + ": " + e);
            return null;
        }
    }
}
