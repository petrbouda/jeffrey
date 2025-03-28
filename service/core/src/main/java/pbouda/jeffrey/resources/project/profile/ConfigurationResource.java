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

package pbouda.jeffrey.resources.project.profile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import pbouda.jeffrey.manager.ProfileConfigurationManager;

public class ConfigurationResource {

    private final ProfileConfigurationManager configurationManager;

    public ConfigurationResource(ProfileConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @GET
    public JsonNode list() {
        return configurationManager.configuration();
    }
}
