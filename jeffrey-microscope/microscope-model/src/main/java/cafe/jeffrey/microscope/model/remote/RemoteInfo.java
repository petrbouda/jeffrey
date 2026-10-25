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

package cafe.jeffrey.microscope.model.remote;

/**
 * The one remote this Microscope reaches: a Jeffrey Hub, or the recording volume on an Azure file
 * share. It is declared in configuration only ({@code jeffrey.microscope.remote.*}) and never stored;
 * its workspaces and projects are read live and never cached here.
 *
 * @param name    what the UI and the MCP answers call it
 * @param address where it is and what kind it is
 */
public record RemoteInfo(String name, RemoteAddress address) {

    public RemoteInfo {
        if (name == null || name.isBlank() || address == null) {
            throw new IllegalArgumentException("A remote needs a name and an address");
        }
    }
}
