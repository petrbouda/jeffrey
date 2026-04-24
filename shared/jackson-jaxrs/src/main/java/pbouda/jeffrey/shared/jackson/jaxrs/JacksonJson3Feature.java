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

package pbouda.jeffrey.shared.jackson.jaxrs;

import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.ext.Provider;

/**
 * Registers {@link JacksonJsonProvider} in a single call. Drop this in place
 * of {@code JacksonFeature} (the Jersey-supplied Jackson-2 feature) in any
 * JAX-RS {@code ResourceConfig}.
 */
@Provider
public class JacksonJson3Feature implements Feature {

    @Override
    public boolean configure(FeatureContext context) {
        context.register(JacksonJsonProvider.class);
        return true;
    }
}
