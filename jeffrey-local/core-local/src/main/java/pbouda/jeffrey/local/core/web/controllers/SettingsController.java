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

package pbouda.jeffrey.local.core.web.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import pbouda.jeffrey.local.core.configuration.SettingDescriptor;
import pbouda.jeffrey.local.core.configuration.SettingsMetadata;
import pbouda.jeffrey.local.core.manager.SettingsManager;
import pbouda.jeffrey.local.core.resources.request.SettingsRequest;
import pbouda.jeffrey.local.core.resources.response.SettingsResponse;
import pbouda.jeffrey.shared.common.exception.Exceptions;

import java.util.List;
import java.util.Map;

@RequestMapping("/api/internal/settings")
@ResponseBody
public class SettingsController {

    private static final String MASK = "****";

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

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "restartRequired", settingsManager.isRestartRequired(),
                "encryptionMode", settingsManager.getBindingMode().name()
        );
    }

    @GetMapping("/{category}")
    public List<SettingsResponse> findByCategory(@PathVariable("category") String category) {
        return settingsMetadata.byCategory(category).stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/{category}/{*name}")
    public void upsert(
            @PathVariable("category") String category,
            @PathVariable("name") String name,
            @RequestBody SettingsRequest request) {

        // {*name} captures with a leading slash; strip it for compatibility.
        String settingName = name.startsWith("/") ? name.substring(1) : name;
        if (!settingsMetadata.isKnown(settingName)) {
            throw Exceptions.invalidRequest("Unknown setting: " + settingName);
        }

        settingsManager.upsert(category, settingName, request.value(), request.secret());
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
