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
import pbouda.jeffrey.local.persistence.model.Setting;
import pbouda.jeffrey.local.persistence.repository.SettingsRepository;
import pbouda.jeffrey.shared.common.encryption.MachineFingerprint;
import pbouda.jeffrey.shared.common.encryption.SecretEncryptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsManagerTest {

    @Mock
    private SettingsRepository settingsRepository;

    @Mock
    private SecretEncryptor secretEncryptor;

    @Mock
    private MachineFingerprint machineFingerprint;

    private SettingsManager manager;

    @BeforeEach
    void setUp() {
        when(machineFingerprint.resolve()).thenReturn(
                new MachineFingerprint.Result("test-fingerprint", MachineFingerprint.BindingMode.MACHINE_BOUND));
        manager = new SettingsManager(settingsRepository, secretEncryptor, machineFingerprint);
    }

    @Nested
    class UpsertSetting {

        @Test
        void storesPlainValueForNonSecret() {
            manager.upsert("ai", "provider", "claude", false);

            verify(settingsRepository).upsert(new Setting("ai", "provider", "claude", false));
            verifyNoInteractions(secretEncryptor);
        }

        @Test
        void encryptsValueForSecret() {
            when(secretEncryptor.encrypt("sk-ant-api03-key")).thenReturn("encrypted-base64");

            manager.upsert("ai", "api-key", "sk-ant-api03-key", true);

            verify(secretEncryptor).encrypt("sk-ant-api03-key");
            verify(settingsRepository).upsert(new Setting("ai", "api-key", "encrypted-base64", true));
        }
    }

    @Nested
    class GetDecryptedValue {

        @Test
        void decryptsSecretValue() {
            when(settingsRepository.find("ai", "api-key")).thenReturn(
                    Optional.of(new Setting("ai", "api-key", "encrypted-base64", true)));
            when(secretEncryptor.decrypt("encrypted-base64")).thenReturn("sk-ant-api03-key");

            Optional<String> result = manager.getDecryptedValue("ai", "api-key");

            assertTrue(result.isPresent());
            assertEquals("sk-ant-api03-key", result.get());
        }

        @Test
        void returnsPlainValueForNonSecret() {
            when(settingsRepository.find("ai", "provider")).thenReturn(
                    Optional.of(new Setting("ai", "provider", "claude", false)));

            Optional<String> result = manager.getDecryptedValue("ai", "provider");

            assertTrue(result.isPresent());
            assertEquals("claude", result.get());
            verifyNoInteractions(secretEncryptor);
        }

        @Test
        void returnsEmptyWhenNotFound() {
            when(settingsRepository.find("ai", "missing")).thenReturn(Optional.empty());

            Optional<String> result = manager.getDecryptedValue("ai", "missing");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class FindAll {

        @Test
        void masksSecretValues() {
            when(settingsRepository.findAll()).thenReturn(List.of(
                    new Setting("ai", "provider", "claude", false),
                    new Setting("ai", "api-key", "encrypted-base64", true)
            ));
            when(secretEncryptor.decrypt("encrypted-base64")).thenReturn("sk-ant-api03-longkey1234");

            List<Setting> result = manager.findAll();

            assertEquals(2, result.size());

            Setting provider = result.stream().filter(s -> "provider".equals(s.key())).findFirst().orElseThrow();
            assertEquals("claude", provider.value());
            assertFalse(provider.secret());

            Setting apiKey = result.stream().filter(s -> "api-key".equals(s.key())).findFirst().orElseThrow();
            assertTrue(apiKey.secret());
            assertTrue(apiKey.value().contains("****"));
            assertNotEquals("sk-ant-api03-longkey1234", apiKey.value());
        }
    }

    @Nested
    class DeleteSetting {

        @Test
        void delegatesToRepository() {
            manager.delete("ai", "provider");

            verify(settingsRepository).delete("ai", "provider");
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

            SettingsManager fallbackManager = new SettingsManager(settingsRepository, secretEncryptor, machineFingerprint);
            assertEquals(MachineFingerprint.BindingMode.USER_BOUND, fallbackManager.getBindingMode());
        }
    }
}
