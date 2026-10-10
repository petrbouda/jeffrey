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

package cafe.jeffrey.hub.model.repository;

/**
 * Why a session ended without ever sending a heartbeat — attached only to such a session.
 *
 * <p>Every session is expected to report liveness; one that wrote no liveness file within the
 * startup grace is finished at its start and carries this. What it holds is what the UI needs to
 * tell the user how to turn heartbeats on.</p>
 *
 * @param agentPresent whether the Jeffrey Agent jar was written into the session directory: present
 *                     means the agent was attached and the JVM never got as far as running it (or the
 *                     application switched liveness off); absent means the agent was switched off
 */
public record MissingHeartbeat(boolean agentPresent) {
}
