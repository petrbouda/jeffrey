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

package pbouda.jeffrey.shared.common.encryption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretEncryptorTest {

    private SecretEncryptor encryptor;

    @BeforeEach
    void setUp() {
        MachineFingerprint fingerprint = new TestMachineFingerprint("test-machine-id:testuser:/home/test:Linux:amd64");
        encryptor = new SecretEncryptor(fingerprint);
    }

    @Nested
    class EncryptDecrypt {

        @Test
        void roundTripWithApiKey() {
            String apiKey = "sk-ant-api03-EPFV0SEAgjlApXHTtRHrBGxt7mLysHBz";

            String encrypted = encryptor.encrypt(apiKey);
            String decrypted = encryptor.decrypt(encrypted);

            assertEquals(apiKey, decrypted);
            assertNotEquals(apiKey, encrypted);
        }

        @Test
        void roundTripWithEmptyString() {
            String encrypted = encryptor.encrypt("");
            String decrypted = encryptor.decrypt(encrypted);

            assertEquals("", decrypted);
        }

        @Test
        void roundTripWithUnicodeContent() {
            String value = "secret-key-with-unicode-\u00e9\u00e0\u00fc-\u4f60\u597d";

            String encrypted = encryptor.encrypt(value);
            String decrypted = encryptor.decrypt(encrypted);

            assertEquals(value, decrypted);
        }

        @Test
        void differentEncryptionsProduceDifferentCiphertext() {
            String plaintext = "same-plaintext";

            String encrypted1 = encryptor.encrypt(plaintext);
            String encrypted2 = encryptor.encrypt(plaintext);

            // Different IVs should produce different ciphertext
            assertNotEquals(encrypted1, encrypted2);

            // Both should decrypt to the same plaintext
            assertEquals(plaintext, encryptor.decrypt(encrypted1));
            assertEquals(plaintext, encryptor.decrypt(encrypted2));
        }
    }

    @Nested
    class DecryptionFailure {

        @Test
        void failsWithDifferentFingerprint() {
            String encrypted = encryptor.encrypt("secret-value");

            MachineFingerprint differentFingerprint = new TestMachineFingerprint("different-machine:otheruser:/other:Mac:arm64");
            SecretEncryptor otherEncryptor = new SecretEncryptor(differentFingerprint);

            assertThrows(EncryptionException.class, () -> otherEncryptor.decrypt(encrypted));
        }

        @Test
        void failsWithTamperedCiphertext() {
            String encrypted = encryptor.encrypt("secret-value");

            // Tamper with the Base64 string
            char[] chars = encrypted.toCharArray();
            chars[20] = chars[20] == 'A' ? 'B' : 'A';
            String tampered = new String(chars);

            assertThrows(EncryptionException.class, () -> encryptor.decrypt(tampered));
        }

        @Test
        void failsWithTooShortCiphertext() {
            assertThrows(EncryptionException.class, () -> encryptor.decrypt("dG9vLXNob3J0"));
        }
    }

    @Nested
    class CanDecrypt {

        @Test
        void returnsTrueWhenEncryptionWorks() {
            assertTrue(encryptor.canDecrypt());
        }
    }

    /**
     * Test helper that returns a fixed fingerprint.
     */
    private static class TestMachineFingerprint extends MachineFingerprint {
        private final String fingerprint;

        TestMachineFingerprint(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        @Override
        public Result resolve() {
            return new Result(fingerprint, BindingMode.MACHINE_BOUND);
        }
    }
}
