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
