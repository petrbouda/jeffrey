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

package cafe.jeffrey.provisioner;

import java.util.Map;

/**
 * Identity bundle propagated from the Provisioner to the Jeffrey Agent so the
 * agent can emit the {@code jeffrey.AppInformation} JFR event into every chunk.
 * Grouped into a record to keep {@link FeatureBuilder} call sites readable.
 *
 * @param attributes free-form custom metadata; serialized and Base64-encoded by
 *                   {@link FeatureBuilder} before being passed as an agent argument
 */
public record AppIdentity(
        String workspaceId,
        String projectId,
        String projectName,
        String projectLabel,
        String instanceId,
        String sessionId,
        int sessionOrder,
        Map<String, String> attributes,
        long provisionedAt) {
}
