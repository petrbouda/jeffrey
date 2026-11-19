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

package cafe.jeffrey.microscope.connector.azure;

import cafe.jeffrey.microscope.connector.local.LocalDirectoryAddress;
import cafe.jeffrey.microscope.connector.api.spi.ConnectorProvider;
import cafe.jeffrey.microscope.connector.api.spi.ConnectorProviders;
import cafe.jeffrey.microscope.connector.api.spi.DeclaredConnector;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AzureFilesConnectorProviderTest {

    private static final String ENDPOINT = "https://jeffreyprod.file.core.windows.net";
    private static final String SAS = "sv=2026-06-06&ss=f&srt=sco&sp=rl&sig=c2lnbmF0dXJl";

    private final AzureFilesConnectorProvider provider = new AzureFilesConnectorProvider();

    private static AzureFilesSettings share(String protocol, String auth, String sasToken) {
        return new AzureFilesSettings(ENDPOINT, "recordings", null, protocol, auth, sasToken, false);
    }

    /** Both kinds are found beside each other on the module path, each declared with provides. */
    @Test
    void isFoundBesideTheLocalKind() {
        List<ConnectorProvider<?>> found = ConnectorProviders.load();

        assertEquals(List.of(AzureFilesAddress.KIND, LocalDirectoryAddress.KIND),
                found.stream().map(ConnectorProvider::kind).toList());
    }

    @Nested
    class Declare {

        @Test
        void aShareIsWritableOverSmbWithASasByDefaultNamedByItsShare() {
            DeclaredConnector connector = provider.declare(share(null, null, SAS));

            assertEquals(new AzureFilesAddress(ENDPOINT, "recordings", "", AzureFilesProtocol.SMB,
                    AzureFilesAuth.SAS, false), connector.address());
            assertEquals("recordings", connector.defaultName());
        }

        @Test
        void entraNeedsNoToken() {
            assertEquals(AzureFilesAuth.ENTRA,
                    ((AzureFilesAddress) provider.declare(share("nfs", "entra", null)).address()).auth());
        }

        @Test
        void aSasWithoutItsTokenIsRefused() {
            assertThrows(IllegalArgumentException.class, () -> provider.declare(share("smb", "sas", " ")));
            assertThrows(IllegalArgumentException.class, () -> provider.declare(share("smb", "sas", null)));
        }

        @Test
        void anUnknownProtocolOrSignInIsRefused() {
            assertThrows(IllegalArgumentException.class, () -> provider.declare(share("ftp", "sas", SAS)));
            assertThrows(IllegalArgumentException.class, () -> provider.declare(share("smb", "password", SAS)));
        }

        /** A portal's SAS URL pasted as the endpoint is refused without being quoted. */
        @Test
        void aSasUrlAsTheEndpointIsRefusedWithoutQuotingIt() {
            AzureFilesSettings pasted = new AzureFilesSettings(ENDPOINT + "/?sv=2026-06-06&sp=rl&sig=PASTED",
                    "recordings", null, null, null, SAS, false);

            IllegalArgumentException refused =
                    assertThrows(IllegalArgumentException.class, () -> provider.declare(pasted));
            assertFalse(refused.getMessage().contains("PASTED"));
        }
    }

    /** The token reaches the volume it opens, and never the address, the connector or the settings' string. */
    @Test
    void theSasTokenStaysOutOfEverythingThatIsPrinted() {
        DeclaredConnector connector = provider.declare(share("smb", "sas", " " + SAS + " "));

        assertFalse(connector.address().toString().contains("sig="));
        assertFalse(connector.address().identity().contains("sig="));
        assertFalse(connector.toString().contains("sig="));
        assertFalse(share("smb", "sas", SAS).toString().contains("sig="));
    }
}
