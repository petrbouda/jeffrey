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

package cafe.jeffrey.shared.common.filesystem;

import java.nio.file.Path;

public interface TempDirFactory {

    TempDirectory newTempDir();

    TempDirectory newTempDir(String directory);

    static TempDirFactory of(Path tempDir) {
        return new TempDirFactory() {
            @Override
            public TempDirectory newTempDir() {
                return newTempDir(System.nanoTime() + "");
            }

            @Override
            public TempDirectory newTempDir(String directory) {
                return new TempDirectory(tempDir.resolve(directory));
            }
        };
    }
}
