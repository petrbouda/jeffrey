/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    private static final String BASE_PATH = "/code/jeffrey";
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
                    .thenReturn(IdeTargetStatus.linked(new IdeTarget(PORT, PROJECT_ID, IDE_NAME, PROJECT_NAME, BASE_PATH, PID)));
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
                             "ideName":"IntelliJ IDEA","projectName":"jeffrey",
                             "basePath":"/code/jeffrey","pid":9688}
                            """))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success").asBoolean().isTrue();

            verify(ideBridge).selectTarget(eq(PROFILE), targetCaptor.capture());
            assertThat(targetCaptor.getValue())
                    .isEqualTo(new IdeTarget(PORT, PROJECT_ID, IDE_NAME, PROJECT_NAME, BASE_PATH, PID));
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
