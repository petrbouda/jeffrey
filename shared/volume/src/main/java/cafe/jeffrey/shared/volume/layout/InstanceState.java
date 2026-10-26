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

package cafe.jeffrey.shared.volume.layout;

/**
 * Where an instance is, read off its newest session alone: a newer session is what finishes the
 * one before it, so only the newest can still be recording.
 */
public enum InstanceState {
    /** Registered by the provisioner, no session yet. */
    PENDING,
    /** Its newest session is recording. */
    ACTIVE,
    /** Its newest session has ended. */
    FINISHED
}
