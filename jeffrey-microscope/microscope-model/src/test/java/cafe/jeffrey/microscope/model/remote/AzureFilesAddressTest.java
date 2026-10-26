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

package cafe.jeffrey.microscope.model.remote;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AzureFilesAddressTest {

    private static final String ENDPOINT = "https://jeffreyprod.file.core.windows.net";
    private static final String SIGNATURE = "sig=SECRETSIGNATURE";

    private static AzureFilesAddress address(String endpoint) {
        return new AzureFilesAddress(endpoint, "recordings", null, AzureFilesProtocol.SMB, AzureFilesAuth.SAS, false);
    }

    @Nested
    class Endpoint {

        @Test
        void keepsTheSchemeAndTheHostLowerCased() {
            assertEquals(ENDPOINT, address("https://JeffreyProd.file.core.windows.net/").endpoint());
        }

        @Test
        void keepsAnExplicitPort() {
            assertEquals("https://localhost:10000", address("https://localhost:10000").endpoint());
        }

        /**
         * The portal's "File service SAS URL": accepted, the signature would be stored, listed and
         * logged as part of the address.
         */
        @Test
        void refusesASasUrlWithoutQuotingIt() {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> address(ENDPOINT + "/?sv=2024-11-04&ss=f&sp=rl&" + SIGNATURE));

            assertFalse(refused.getMessage().contains(SIGNATURE), refused.getMessage());
            assertTrue(refused.getMessage().contains("sas-token"), refused.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "http://jeffreyprod.file.core.windows.net",
                "https://user:password@jeffreyprod.file.core.windows.net",
                "https://jeffreyprod.file.core.windows.net/recordings",
                "https://jeffreyprod.file.core.windows.net/#fragment",
                "https://jeffreyprod.file.core.windows.net?",
                "jeffreyprod.file.core.windows.net",
                " "})
        void refusesAnythingButASchemeAndAHost(String endpoint) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> address(endpoint));

            assertFalse(refused.getMessage().contains("password"), refused.getMessage());
        }
    }

    @Nested
    class Identity {

        @Test
        void isTheShareAndItsRootWhetherOrNotItMayBeChanged() {
            AzureFilesAddress writable = new AzureFilesAddress(
                    ENDPOINT, "recordings", "/volume/", AzureFilesProtocol.NFS, AzureFilesAuth.ENTRA, false);
            AzureFilesAddress readOnly = new AzureFilesAddress(
                    ENDPOINT, "recordings", "volume", AzureFilesProtocol.NFS, AzureFilesAuth.ENTRA, true);

            assertEquals("azure-files:" + ENDPOINT + "/recordings/volume", writable.identity());
            assertEquals(writable.identity(), readOnly.identity());
            assertFalse(writable.readOnly());
            assertTrue(readOnly.readOnly());
        }

        @Test
        void refusesARootThatWalksUp() {
            assertThrows(IllegalArgumentException.class, () -> new AzureFilesAddress(
                    ENDPOINT, "recordings", "volume/../other", AzureFilesProtocol.SMB, AzureFilesAuth.SAS, false));
        }
    }
}
