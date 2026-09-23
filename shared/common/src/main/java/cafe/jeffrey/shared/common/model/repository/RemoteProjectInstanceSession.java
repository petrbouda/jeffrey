/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.shared.common.model.repository;

/**
 * Session metadata persisted as {@code .session-info.json} in the session
 * directory. It carries only what a reader needs: the hub materializes the session
 * row from every field, and the provisioner reads {@code order} to number the next
 * session of the instance. The project and workspace are not repeated here; the
 * directory the file sits in, and the project's own marker, already say them.
 * Files written by older provisioners carry more fields, which are ignored.
 *
 * <p>{@code heartbeatExpected} declares whether anything in this run will report
 * liveness — the {@code jeffrey-heartbeat} library, which is an ordinary
 * dependency of the application. The provisioner cannot detect that on its own:
 * whether the library is on the class path is a build-time fact, invisible to the
 * tool that writes the JVM arguments. So it is <em>declared</em>, from
 * {@code heartbeat.enabled} in the provisioner's configuration.
 *
 * <p>It is {@code null} in files written by older provisioners, and null means
 * <em>unknown</em> rather than false: the hub never applies the heartbeat deadline
 * to a session that did not promise to report, because failing to report liveness
 * it never promised is not evidence that it ended.</p>
 */
public record RemoteProjectInstanceSession(
        String sessionId,
        String instanceId,
        long createdAt,
        int order,
        String relativeSessionPath,
        Boolean heartbeatExpected) {
}
