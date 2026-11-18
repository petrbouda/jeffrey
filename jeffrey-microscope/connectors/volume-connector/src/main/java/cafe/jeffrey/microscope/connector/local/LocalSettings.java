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

package cafe.jeffrey.microscope.connector.local;

/**
 * The {@code local} block of the connector declaration ({@code jeffrey.microscope.connector.local.*}):
 * the directory on this machine holding the recording volume.
 *
 * @param path     the absolute path of the directory holding the workspaces
 * @param readOnly whether Microscope refuses every change to the volume
 */
public record LocalSettings(String path, boolean readOnly) {
}
