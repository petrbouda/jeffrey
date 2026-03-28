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

package pbouda.jeffrey.local.core.resources;

import jakarta.ws.rs.*;
import pbouda.jeffrey.local.core.configuration.SettingDescriptor;
import pbouda.jeffrey.local.core.configuration.SettingsMetadata;
import pbouda.jeffrey.local.core.manager.SettingsManager;
import pbouda.jeffrey.local.core.resources.request.SettingsRequest;
import pbouda.jeffrey.local.core.resources.response.SettingsResponse;

import java.util.List;
import java.util.Map;

public class SettingsResource {

    private static final String MASK = "****";

    private final SettingsManager settingsManager;
    private final SettingsMetadata settingsMetadata;

    public SettingsResource(SettingsManager settingsManager, SettingsMetadata settingsMetadata) {
        this.settingsManager = settingsManager;
        this.settingsMetadata = settingsMetadata;
    }

    @GET
    public List<SettingsResponse> findAll() {
        return settingsMetadata.descriptors().stream()
                .map(this::toResponse)
                .toList();
    }

    @GET
    @Path("{category}")
    public List<SettingsResponse> findByCategory(@PathParam("category") String category) {
        return settingsMetadata.byCategory(category).stream()
                .map(this::toResponse)
                .toList();
    }

    @PUT
    @Path("{category}/{name: .+}")
    public void upsert(
            @PathParam("category") String category,
            @PathParam("name") String name,
            SettingsRequest request) {

        if (!settingsMetadata.isKnown(name)) {
            throw new NotFoundException("Unknown setting: " + name);
        }

        settingsManager.upsert(category, name, request.value(), request.secret());
    }

    @GET
    @Path("status")
    public Map<String, Object> status() {
        return Map.of(
                "restartRequired", settingsManager.isRestartRequired(),
                "encryptionMode", settingsManager.getBindingMode().name()
        );
    }

    private SettingsResponse toResponse(SettingDescriptor descriptor) {
        String value = settingsManager.getResolvedValue(descriptor.name());
        if (descriptor.secret()) {
            value = maskValue(value);
        }
        return new SettingsResponse(descriptor.category(), descriptor.name(), value, descriptor.secret());
    }

    private static String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 8) {
            return MASK;
        }
        return value.substring(0, 4) + MASK + value.substring(value.length() - 4);
    }
}
