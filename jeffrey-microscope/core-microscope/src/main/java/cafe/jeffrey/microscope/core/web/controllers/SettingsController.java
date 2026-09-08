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

package cafe.jeffrey.microscope.core.web.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.configuration.SettingDescriptor;
import cafe.jeffrey.microscope.core.configuration.SettingsMetadata;
import cafe.jeffrey.microscope.core.manager.SettingUpdate;
import cafe.jeffrey.microscope.core.manager.SettingsManager;
import cafe.jeffrey.microscope.core.web.dto.request.SettingsBatchRequest;
import cafe.jeffrey.microscope.core.web.dto.request.SettingsRequest;
import cafe.jeffrey.microscope.core.web.dto.response.SettingsResponse;

import java.util.List;

@RestController
@RequestMapping("/api/internal/settings")
public class SettingsController {

    private final SettingsManager settingsManager;
    private final SettingsMetadata settingsMetadata;

    public SettingsController(SettingsManager settingsManager, SettingsMetadata settingsMetadata) {
        this.settingsManager = settingsManager;
        this.settingsMetadata = settingsMetadata;
    }

    @GetMapping
    public List<SettingsResponse> findAll() {
        return settingsMetadata.descriptors().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{category}")
    public List<SettingsResponse> findByCategory(@PathVariable("category") String category) {
        return settingsMetadata.byCategory(category).stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping
    public void upsertAll(@RequestBody SettingsBatchRequest request) {
        List<SettingUpdate> updates = request.items().stream()
                .map(item -> new SettingUpdate(item.category(), item.name(), item.value()))
                .toList();

        settingsManager.upsertAll(updates);
    }

    @PutMapping("/{category}/{*name}")
    public void upsert(
            @PathVariable("category") String category,
            @PathVariable("name") String name,
            @RequestBody SettingsRequest request) {

        // {*name} captures with a leading slash; strip it for compatibility.
        String settingName = name.startsWith("/") ? name.substring(1) : name;
        settingsManager.upsert(category, settingName, request.value());
    }

    private SettingsResponse toResponse(SettingDescriptor descriptor) {
        String value = settingsManager.getResolvedValue(descriptor.name());
        return new SettingsResponse(descriptor.category(), descriptor.name(), value);
    }
}
