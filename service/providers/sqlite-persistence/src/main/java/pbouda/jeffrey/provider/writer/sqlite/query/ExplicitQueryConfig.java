/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.writer.sqlite.query;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.time.RelativeTimeRange;

import java.util.ArrayList;
import java.util.List;

public class ExplicitQueryConfig {

    private RelativeTimeRange timeRange;
    private String profileId;
    private String groupBy;
    private String orderBy;
    private List<Type> types = new ArrayList<>();
    private final List<String> fields = new ArrayList<>();

    public ExplicitQueryConfig withProfileId(String profileId) {
        this.profileId = profileId;
        return this;
    }

    public ExplicitQueryConfig withTypes(List<Type> types) {
        this.types = types;
        return this;
    }

    public ExplicitQueryConfig withTimeRange(RelativeTimeRange timeRange) {
        this.timeRange = timeRange;
        return this;
    }

    public ExplicitQueryConfig addField(String field) {
        this.fields.add(field);
        return this;
    }

    public ExplicitQueryConfig withGroupBy(String groupBy) {
        this.groupBy = groupBy;
        return this;
    }

    public ExplicitQueryConfig withOrderBy(String orderBy) {
        this.orderBy = orderBy;
        return this;
    }

    public RelativeTimeRange timeRange() {
        return timeRange;
    }

    public String profileId() {
        return profileId;
    }

    public List<Type> types() {
        return types;
    }

    public List<String> fields() {
        return fields;
    }

    public String groupBy() {
        return groupBy;
    }

    public String orderBy() {
        return orderBy;
    }
}
