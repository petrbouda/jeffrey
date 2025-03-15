/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.common.config;

import pbouda.jeffrey.common.ProfilingStartEnd;

@SuppressWarnings("unchecked")
public class ConfigBuilder<T extends ConfigBuilder<?>> {
    String primaryId;
    GraphParameters graphParameters;
    ProfilingStartEnd primaryStartEnd;

    public T withPrimaryId(String primaryId) {
        this.primaryId = primaryId;
        return (T) this;
    }

    public T withGraphParameters(GraphParameters graphParameters) {
        this.graphParameters = graphParameters;
        return (T) this;
    }

    public T withPrimaryStartEnd(ProfilingStartEnd primaryStartEnd) {
        this.primaryStartEnd = primaryStartEnd;
        return (T) this;
    }

    public Config build() {
        return new Config(
                primaryId,
                graphParameters == null ? GraphParameters.builder().build() : graphParameters,
                primaryStartEnd);
    }
}
