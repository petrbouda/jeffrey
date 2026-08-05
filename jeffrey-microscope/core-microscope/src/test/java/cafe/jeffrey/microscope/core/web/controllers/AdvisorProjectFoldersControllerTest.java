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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.manager.advisor.AdvisorProjectFoldersManager;
import cafe.jeffrey.microscope.core.manager.advisor.ProjectFolderDraft;
import cafe.jeffrey.microscope.core.manager.advisor.ProjectFolderStatus;
import cafe.jeffrey.microscope.persistence.api.AdvisorProjectFolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class AdvisorProjectFoldersControllerTest {

    private static final String BASE_URI = "/api/internal/advisor/project-folders";
    private static final String FOLDER_ID = "019fb9d3-1da5-7794-b950-ccc7d236db11";
    private static final Instant CREATED_AT = Instant.parse("2026-08-05T09:00:00Z");

    @Mock
    AdvisorProjectFoldersManager manager;

    @Test
    void listsFoldersWithTheirPresence() {
        when(manager.list()).thenReturn(List.of(
                status("order-service", "/home/me/order-service", true),
                status("gone", "/home/me/gone", false)));

        MockMvcTester mvc = mockMvcTesterFor(new AdvisorProjectFoldersController(manager));

        assertThat(mvc.get().uri(BASE_URI))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$[0].name", v -> assertThat(v).asString().isEqualTo("order-service"))
                .hasPathSatisfying("$[0].present", v -> assertThat(v).isEqualTo(true))
                .hasPathSatisfying("$[1].present", v -> assertThat(v).isEqualTo(false))
                .hasPathSatisfying("$[0].createdAt", v -> assertThat(v).isEqualTo(CREATED_AT.toEpochMilli()));
    }

    @Test
    void createsAFolder() {
        when(manager.create(any())).thenReturn(status("order-service", "/home/me/order-service", true));

        MockMvcTester mvc = mockMvcTesterFor(new AdvisorProjectFoldersController(manager));

        assertThat(mvc.post().uri(BASE_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"order-service\",\"path\":\"/home/me/order-service\"}"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.folderId", v -> assertThat(v).asString().isEqualTo(FOLDER_ID));

        verify(manager).create(new ProjectFolderDraft("order-service", "/home/me/order-service"));
    }

    // The draft rejects a relative path before the manager is reached; the advice maps it to 400.
    @Test
    void answersBadRequestForARelativePath() {
        MockMvcTester mvc = mockMvcTesterFor(new AdvisorProjectFoldersController(manager));

        assertThat(mvc.post().uri(BASE_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"order-service\",\"path\":\"projects/order-service\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void answersNotFoundWhenUpdatingAnUnknownFolder() {
        when(manager.update(eq(FOLDER_ID), any())).thenReturn(Optional.empty());

        MockMvcTester mvc = mockMvcTesterFor(new AdvisorProjectFoldersController(manager));

        assertThat(mvc.put().uri(BASE_URI + "/" + FOLDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"order-service\",\"path\":\"/home/me/order-service\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletesAKnownFolder() {
        when(manager.delete(FOLDER_ID)).thenReturn(true);

        MockMvcTester mvc = mockMvcTesterFor(new AdvisorProjectFoldersController(manager));

        assertThat(mvc.delete().uri(BASE_URI + "/" + FOLDER_ID))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void answersNotFoundWhenDeletingAnUnknownFolder() {
        when(manager.delete(FOLDER_ID)).thenReturn(false);

        MockMvcTester mvc = mockMvcTesterFor(new AdvisorProjectFoldersController(manager));

        assertThat(mvc.delete().uri(BASE_URI + "/" + FOLDER_ID))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private static ProjectFolderStatus status(String name, String path, boolean present) {
        return new ProjectFolderStatus(new AdvisorProjectFolder(FOLDER_ID, name, path, CREATED_AT), present);
    }
}
