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
import pbouda.jeffrey.local.core.manager.SettingsManager;
import pbouda.jeffrey.local.core.resources.request.SettingsRequest;
import pbouda.jeffrey.local.core.resources.response.SettingsResponse;
import pbouda.jeffrey.shared.common.encryption.MachineFingerprint;

import java.util.List;
import java.util.Map;

public class SettingsResource {

    private final SettingsManager settingsManager;

    public SettingsResource(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @GET
    public List<SettingsResponse> findAll() {
        return settingsManager.findAll().stream()
                .map(s -> new SettingsResponse(s.category(), s.key(), s.value(), s.secret()))
                .toList();
    }

    @GET
    @Path("{category}")
    public List<SettingsResponse> findByCategory(@PathParam("category") String category) {
        return settingsManager.findByCategory(category).stream()
                .map(s -> new SettingsResponse(s.category(), s.key(), s.value(), s.secret()))
                .toList();
    }

    @PUT
    @Path("{category}/{key}")
    public void upsert(
            @PathParam("category") String category,
            @PathParam("key") String key,
            SettingsRequest request) {

        settingsManager.upsert(category, key, request.value(), request.secret());
    }

    @DELETE
    @Path("{category}/{key}")
    public void delete(
            @PathParam("category") String category,
            @PathParam("key") String key) {

        settingsManager.delete(category, key);
    }

    @GET
    @Path("encryption/mode")
    public Map<String, String> encryptionMode() {
        MachineFingerprint.BindingMode mode = settingsManager.getBindingMode();
        return Map.of("mode", mode.name());
    }
}
