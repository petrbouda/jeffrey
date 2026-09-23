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
