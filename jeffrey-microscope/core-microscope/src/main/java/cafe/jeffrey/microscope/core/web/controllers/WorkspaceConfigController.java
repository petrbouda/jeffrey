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

import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.web.dto.request.ConfigValueRequest;
import cafe.jeffrey.microscope.core.web.dto.response.ScopedConfigResponse;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ConfigType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The configuration a workspace can edit: its own scope and the global one.
 *
 * <p>A project's scope is edited through the project, which is the side that knows its id.</p>
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/config")
public class WorkspaceConfigController {

    /** The scopes this endpoint may address; a project is not one of them. */
    private static final Set<ConfigScope> EDITABLE_SCOPES = Set.of(ConfigScope.GLOBAL, ConfigScope.WORKSPACE);

    private final ProjectManagerResolver resolver;

    public WorkspaceConfigController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    /** Everything that applies to this workspace, in merge order, so the UI can show inheritance. */
    @GetMapping
    public List<ScopedConfigResponse> listConfigs(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId) {

        return resolver.resolveWorkspace(hubId, workspaceId).listConfigs().stream()
                .map(ScopedConfigResponse::from)
                .toList();
    }

    @PutMapping("/{scope}/{type}")
    public ScopedConfigResponse upsertConfig(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("scope") String scope,
            @PathVariable("type") String type,
            @RequestBody ConfigValueRequest request) {

        if (request == null || request.value() == null || request.value().isBlank()) {
            throw Exceptions.invalidRequest("value is required");
        }

        WorkspaceManager workspace = resolver.resolveWorkspace(hubId, workspaceId);
        return ScopedConfigResponse.from(
                workspace.upsertConfig(editableScope(scope), configType(type), request.value()));
    }

    @DeleteMapping("/{scope}/{type}")
    public ResponseEntity<Void> deleteConfig(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("scope") String scope,
            @PathVariable("type") String type) {

        resolver.resolveWorkspace(hubId, workspaceId).deleteConfig(editableScope(scope), configType(type));
        return ResponseEntity.noContent().build();
    }

    /**
     * Rejected here rather than at the hub: an unparseable path variable is a client mistake, and
     * answering it without a round trip keeps the error about the request rather than the hub.
     */
    private static ConfigScope editableScope(String scope) {
        ConfigScope parsed = parse(ConfigScope.class, scope, "scope");
        if (!EDITABLE_SCOPES.contains(parsed)) {
            throw Exceptions.invalidRequest(
                    "scope must be one of " + EDITABLE_SCOPES + "; a project's configuration is edited through the project");
        }
        return parsed;
    }

    static ConfigType configType(String type) {
        return parse(ConfigType.class, type, "type");
    }

    private static <T extends Enum<T>> T parse(Class<T> enumType, String value, String name) {
        if (value == null || value.isBlank()) {
            throw Exceptions.invalidRequest(name + " is required");
        }
        try {
            return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw Exceptions.invalidRequest(
                    "Unknown " + name + ": " + value + "; expected one of " + List.of(enumType.getEnumConstants()));
        }
    }
}
