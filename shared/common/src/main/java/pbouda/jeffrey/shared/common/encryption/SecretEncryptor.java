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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts and decrypts secret values using AES-256-GCM with a key derived from
 * the machine fingerprint via HKDF-SHA256.
 *
 * <p>Storage format: Base64(IV[12] || ciphertext || GCM-tag[16])</p>
 *
 * <p>The encryption key is derived at construction time and cached for the lifetime
 * of this instance. If the machine fingerprint changes (different machine, different user),
 * decryption will fail with an authentication error.</p>
 */
public final class SecretEncryptor {

    private static final Logger LOG = LoggerFactory.getLogger(SecretEncryptor.class);

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int AES_KEY_LENGTH_BYTES = 32;

    private static final byte[] HKDF_SALT = "jeffrey-local-settings-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HKDF_INFO = "secret-encryption".getBytes(StandardCharsets.UTF_8);

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretEncryptor(MachineFingerprint machineFingerprint) {
        this.secretKey = deriveKey(machineFingerprint.resolve().fingerprint());
    }

    /**
     * Encrypts a plaintext string and returns a Base64-encoded ciphertext.
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine: IV || ciphertext (includes GCM tag)
            byte[] combined = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH_BYTES, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Failed to encrypt secret", e);
        }
    }

    /**
     * Decrypts a Base64-encoded ciphertext and returns the plaintext string.
     *
     * @throws EncryptionException if decryption fails (wrong key, tampered data, etc.)
     */
    public String decrypt(String base64Ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(base64Ciphertext);

            if (combined.length < IV_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / 8) {
                throw new EncryptionException("Ciphertext too short to contain IV and GCM tag");
            }

            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);

            byte[] ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new EncryptionException(
                    "Decryption failed — machine fingerprint may have changed. Please re-enter your secrets.", e);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new EncryptionException("Failed to decrypt secret", e);
        }
    }

    /**
     * Tests whether decryption is possible by encrypting and decrypting a sentinel value.
     */
    public boolean canDecrypt() {
        try {
            String sentinel = "jeffrey-encryption-test";
            String encrypted = encrypt(sentinel);
            String decrypted = decrypt(encrypted);
            return sentinel.equals(decrypted);
        } catch (EncryptionException e) {
            LOG.warn("Encryption self-test failed: {}", e.getMessage());
            return false;
        }
    }

    private static SecretKey deriveKey(String fingerprint) {
        try {
            byte[] ikm = MessageDigest.getInstance("SHA-256")
                    .digest(fingerprint.getBytes(StandardCharsets.UTF_8));

            KDF kdf = KDF.getInstance("HKDF-SHA256");
            HKDFParameterSpec params = HKDFParameterSpec.ofExtract()
                    .addIKM(ikm)
                    .addSalt(HKDF_SALT)
                    .thenExpand(HKDF_INFO, AES_KEY_LENGTH_BYTES);

            return kdf.deriveKey("AES", params);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Failed to derive encryption key", e);
        }
    }
}
