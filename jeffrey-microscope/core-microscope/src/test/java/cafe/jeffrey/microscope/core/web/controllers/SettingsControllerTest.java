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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.configuration.SettingsMetadata;
import cafe.jeffrey.microscope.core.manager.SettingsManager;
import cafe.jeffrey.shared.common.encryption.MachineFingerprint.BindingMode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class SettingsControllerTest {

    @Mock
    SettingsManager settingsManager;

    @Mock
    SettingsMetadata settingsMetadata;

    @Test
    void listsEmptyWhenNoDescriptors() {
        when(settingsMetadata.descriptors()).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new SettingsController(settingsManager, settingsMetadata));

        assertThat(mvc.get().uri("/api/internal/settings"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }

    @Test
    void exposesStatus() {
        when(settingsManager.isRestartRequired()).thenReturn(true);
        when(settingsManager.getBindingMode()).thenReturn(BindingMode.MACHINE_BOUND);

        MockMvcTester mvc = mockMvcTesterFor(new SettingsController(settingsManager, settingsMetadata));

        assertThat(mvc.get().uri("/api/internal/settings/status"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.restartRequired", v -> assertThat(v).asBoolean().isTrue())
                .hasPathSatisfying("$.encryptionMode", v -> assertThat(v).asString().isEqualTo("MACHINE_BOUND"));
    }
}
