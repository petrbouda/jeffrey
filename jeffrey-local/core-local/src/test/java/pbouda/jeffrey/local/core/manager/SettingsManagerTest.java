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

package pbouda.jeffrey.local.core.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import pbouda.jeffrey.local.persistence.model.Setting;
import pbouda.jeffrey.local.persistence.repository.SettingsRepository;
import pbouda.jeffrey.shared.common.encryption.MachineFingerprint;
import pbouda.jeffrey.shared.common.encryption.SecretEncryptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsManagerTest {

    @Mock
    private SettingsRepository settingsRepository;

    @Mock
    private SecretEncryptor secretEncryptor;

    @Mock
    private MachineFingerprint machineFingerprint;

    @Mock
    private Environment environment;

    private SettingsManager manager;

    @BeforeEach
    void setUp() {
        when(machineFingerprint.resolve()).thenReturn(
                new MachineFingerprint.Result("test-fingerprint", MachineFingerprint.BindingMode.MACHINE_BOUND));
        manager = new SettingsManager(settingsRepository, secretEncryptor, machineFingerprint, environment);
    }

    @Nested
    class UpsertSetting {

        @Test
        void storesPlainValueForNonSecret() {
            manager.upsert("ai", "jeffrey.local.ai.provider", "claude", false);

            verify(settingsRepository).upsert(new Setting("ai", "jeffrey.local.ai.provider", "claude", false));
            verifyNoInteractions(secretEncryptor);
        }

        @Test
        void encryptsValueForSecret() {
            when(secretEncryptor.encrypt("sk-ant-api03-key")).thenReturn("encrypted-base64");

            manager.upsert("ai", "jeffrey.local.ai.api-key", "sk-ant-api03-key", true);

            verify(secretEncryptor).encrypt("sk-ant-api03-key");
            verify(settingsRepository).upsert(new Setting("ai", "jeffrey.local.ai.api-key", "encrypted-base64", true));
        }
    }

    @Nested
    class BindingMode {

        @Test
        void returnsMachineBound() {
            assertEquals(MachineFingerprint.BindingMode.MACHINE_BOUND, manager.getBindingMode());
        }

        @Test
        void returnsUserBoundWhenFallback() {
            when(machineFingerprint.resolve()).thenReturn(
                    new MachineFingerprint.Result("user-only", MachineFingerprint.BindingMode.USER_BOUND));

            SettingsManager fallbackManager = new SettingsManager(settingsRepository, secretEncryptor, machineFingerprint, environment);
            assertEquals(MachineFingerprint.BindingMode.USER_BOUND, fallbackManager.getBindingMode());
        }
    }
}
