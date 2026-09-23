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

package cafe.jeffrey.hub.core.project.repository;

/**
 * The file an id named was in the session's listing and gone by the time it was reached.
 *
 * <p>On this hub that means one thing: the compression job published the archive and removed the
 * recording in between. The id is the one thing that survives that rewrite, so asking for it
 * again names the archive — which is why this is a kind of its own rather than one more refusal
 * worded differently. A caller holding an id has somewhere to go; a caller holding a path does
 * not.
 *
 * <p>An {@link IllegalArgumentException}, so a caller that knows nothing about this still reports
 * it the way it reports every other refusal from {@link RepositoryStorage#file}: the id named
 * nothing that can be served.
 */
public class FileVanishedException extends IllegalArgumentException {

    public FileVanishedException(String message) {
        super(message);
    }
}
