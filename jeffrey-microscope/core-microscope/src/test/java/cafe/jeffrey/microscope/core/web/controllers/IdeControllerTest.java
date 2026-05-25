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

import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.ide.IdeFailureReason;
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeTarget;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdeControllerTest {

    private static final String PROFILE = "p1";
    private static final int PORT = 63342;
    private static final long PID = 9688L;
    private static final String PROJECT_ID = "loc-hash-1";
    private static final String PROJECT_NAME = "jeffrey";
    private static final String IDE_NAME = "IntelliJ IDEA";

    @Mock
    IdeBridge ideBridge;

    @Captor
    ArgumentCaptor<IdeTarget> targetCaptor;

    @Nested
    class Open {

        @Test
        void successCarriesNoneReason() {
            when(ideBridge.open(any(IdeOpenRequest.class))).thenReturn(IdeOpenResult.succeeded());
            MockMvcTester mvc = mockMvcTesterFor(new IdeController(ideBridge));

            assertThat(mvc.post()
                    .uri("/api/internal/ide/open")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"profileId":"p1","fqn":"com.example.Foo","method":"Foo.bar","line":12}
                            """))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success").asBoolean().isTrue();
        }

        @Test
        void failureExposesMessageAndReason() {
            when(ideBridge.open(any(IdeOpenRequest.class)))
                    .thenReturn(IdeOpenResult.failed("The selected IDE window is no longer open",
                            IdeFailureReason.UNREACHABLE));
            MockMvcTester mvc = mockMvcTesterFor(new IdeController(ideBridge));

            assertThat(mvc.post()
                    .uri("/api/internal/ide/open")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"profileId":"p1","fqn":"com.example.Foo","method":"Foo.bar","line":12}
                            """))
                    .hasStatusOk()
                    .bodyJson()
                    .satisfies(json -> {
                        assertThat(json).extractingPath("$.success").asBoolean().isFalse();
                        assertThat(json).extractingPath("$.reason").asString().isEqualTo("UNREACHABLE");
                        assertThat(json).extractingPath("$.message").asString()
                                .isEqualTo("The selected IDE window is no longer open");
                    });
        }
    }

    @Nested
    class Status {

        @Test
        void linkedReportsCachedWindow() {
            when(ideBridge.targetStatus(PROFILE))
                    .thenReturn(IdeTargetStatus.linked(new IdeTarget(PORT, PROJECT_ID, IDE_NAME, PROJECT_NAME, PID)));
            MockMvcTester mvc = mockMvcTesterFor(new IdeController(ideBridge));

            assertThat(mvc.get().uri("/api/internal/ide/status?profileId=p1"))
                    .hasStatusOk()
                    .bodyJson()
                    .satisfies(json -> {
                        assertThat(json).extractingPath("$.selectable").asBoolean().isTrue();
                        assertThat(json).extractingPath("$.linked").asBoolean().isTrue();
                        assertThat(json).extractingPath("$.ideName").asString().isEqualTo(IDE_NAME);
                        assertThat(json).extractingPath("$.projectName").asString().isEqualTo(PROJECT_NAME);
                        assertThat(json).extractingPath("$.port").asNumber().isEqualTo(PORT);
                    });
        }

        @Test
        void notSelectableWhenBridgeDoesNotSupportSelection() {
            when(ideBridge.targetStatus(PROFILE)).thenReturn(IdeTargetStatus.notSelectable());
            MockMvcTester mvc = mockMvcTesterFor(new IdeController(ideBridge));

            assertThat(mvc.get().uri("/api/internal/ide/status?profileId=p1"))
                    .hasStatusOk()
                    .bodyJson()
                    .satisfies(json -> {
                        assertThat(json).extractingPath("$.selectable").asBoolean().isFalse();
                        assertThat(json).extractingPath("$.linked").asBoolean().isFalse();
                    });
        }
    }

    @Nested
    class SelectTarget {

        @Test
        void buildsTargetFromRequestAndPersists() {
            when(ideBridge.selectTarget(eq(PROFILE), any(IdeTarget.class))).thenReturn(true);
            MockMvcTester mvc = mockMvcTesterFor(new IdeController(ideBridge));

            assertThat(mvc.post()
                    .uri("/api/internal/ide/target")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"profileId":"p1","port":63342,"projectId":"loc-hash-1",
                             "ideName":"IntelliJ IDEA","projectName":"jeffrey","pid":9688}
                            """))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success").asBoolean().isTrue();

            verify(ideBridge).selectTarget(eq(PROFILE), targetCaptor.capture());
            assertThat(targetCaptor.getValue())
                    .isEqualTo(new IdeTarget(PORT, PROJECT_ID, IDE_NAME, PROJECT_NAME, PID));
        }
    }

    @Nested
    class Disconnect {

        @Test
        void clearsTargetForProfile() {
            when(ideBridge.clearTarget(PROFILE)).thenReturn(true);
            MockMvcTester mvc = mockMvcTesterFor(new IdeController(ideBridge));

            assertThat(mvc.delete().uri("/api/internal/ide/target?profileId=p1"))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success").asBoolean().isTrue();

            verify(ideBridge).clearTarget(PROFILE);
        }
    }
}
