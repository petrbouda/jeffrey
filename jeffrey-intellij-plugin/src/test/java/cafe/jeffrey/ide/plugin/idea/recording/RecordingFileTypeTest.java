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

import cafe.jeffrey.ide.plugin.idea.JeffreyIcons;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The file type that puts the recording panel in the path of a double-click.
 *
 * <p>Its {@code fieldName} and name are wire values: {@code plugin.xml} instantiates the type from
 * the field and the platform stores the name in the user's file-type settings, so both are worth
 * pinning against a rename that would look harmless.
 */
public class RecordingFileTypeTest {

    @Test
    public void isInstantiableFromTheFieldPluginXmlNames() {
        assertNotNull(RecordingFileType.INSTANCE);
        assertSame(RecordingFileType.INSTANCE, RecordingFileType.INSTANCE);
    }

    /** Matches the {@code name} attribute; the platform persists it in the user's settings. */
    @Test
    public void keepsTheNameThePlatformStores() {
        assertEquals("JVM Recording", RecordingFileType.INSTANCE.getName());
    }

    /**
     * Binary, because it is. Saying otherwise invites the platform to guess an encoding and load a
     * multi-gigabyte recording as text.
     */
    @Test
    public void declaresItselfBinary() {
        assertTrue(RecordingFileType.INSTANCE.isBinary());
    }

    /** The same flame the icon provider draws, so the two never disagree about one file. */
    @Test
    public void carriesTheSameIconTheProviderDoes() {
        assertSame(JeffreyIcons.FILE, RecordingFileType.INSTANCE.getIcon());
    }

    @Test
    public void hasADescriptionAndADefaultExtension() {
        assertEquals("jfr", RecordingFileType.INSTANCE.getDefaultExtension());
        assertTrue(RecordingFileType.INSTANCE.getDescription().contains("Jeffrey"));
    }
}
