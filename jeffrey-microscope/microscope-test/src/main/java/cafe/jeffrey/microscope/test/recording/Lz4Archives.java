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

package cafe.jeffrey.microscope.test.recording;

import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes an archive the way the volume scheduler's compression job does — one standard LZ4 frame —
 * so a test can lay down what Microscope finds on the volume after the job ran. Microscope itself
 * only decompresses; this is the scheduler's half, for tests only.
 */
public abstract class Lz4Archives {

    /**
     * @return {@code target}, holding {@code source} as one LZ4 frame
     */
    public static Path compress(Path source, Path target) {
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = new LZ4FrameOutputStream(Files.newOutputStream(target))) {
            in.transferTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write an LZ4 archive: source=" + source + " target=" + target, e);
        }
        return target;
    }
}
