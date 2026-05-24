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

package cafe.jeffrey.microscope.core.manager.ide;

/**
 * Request to fetch the source of a class from a locally-running IDE plugin.
 *
 * <p>The {@code method} carries no semantic meaning for source retrieval, but it is required to
 * reconstruct the {@code /ide/{fqn}.{method}} path the plugin expects: the plugin always drops the
 * last dotted segment (treating it as the method) and uses the preceding segment as the class name.
 *
 * @param profileId the profile this request belongs to; used by the {@code default} bridge to look
 *                  up the cached IDE-window target. Nullable (the {@code jfr-profiler-plugin} bridge
 *                  ignores it).
 * @param fqn       fully-qualified class name (e.g. {@code com.example.OrderService})
 * @param method    method reference as {@code ClassName.methodName} (e.g. {@code OrderService.processOrder})
 */
public record IdeSourceRequest(String profileId, String fqn, String method) {

    public IdeSourceRequest {
        if (fqn == null || fqn.isBlank()) {
            throw new IllegalArgumentException("fqn must not be blank");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method must not be blank");
        }
    }
}
