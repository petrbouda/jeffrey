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

package cafe.jeffrey.agent;

/**
 * Application and session identity parsed from the agent arguments and propagated
 * from the Provisioner. Carries everything the {@link AppInformationEvent} needs
 * except {@code jvmStartedAt}, which the agent reads from the running JVM.
 *
 * @param projectLabel already Base64-decoded
 * @param attributes serialized {@code key=value;key=value}, already Base64-decoded
 */
public record AppInformation(
        String workspaceId,
        String projectId,
        String projectName,
        String projectLabel,
        String instanceId,
        String sessionId,
        int sessionOrder,
        String attributes,
        long provisionedAt) {
}
