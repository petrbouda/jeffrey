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

package cafe.jeffrey.profile.manager.model.blocking;

/**
 * One virtual-thread pinning incident, from a {@code jdk.VirtualThreadPinned} event. Pinning means
 * the virtual thread could not unmount from its carrier (e.g. blocking inside a synchronized block
 * on older JDKs), turning cheap virtual-thread blocking into carrier-thread blocking.
 *
 * @param thread        the pinned virtual thread
 * @param durationNanos how long the thread stayed pinned
 */
public record PinnedThreadEntry(String thread, long durationNanos) {
}
