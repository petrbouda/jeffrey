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

package cafe.jeffrey.microscope.core.manager.ide;

import cafe.jeffrey.profile.ai.chat.ProfileSourceAccess;
import cafe.jeffrey.profile.ai.chat.SourceAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Turns a profile's linked IDE window into a directory the in-app AI analysis may read.
 *
 * <p>The in-app assistant can name a class and a method and then has to stop, because it has never
 * seen the code — the same wall the external MCP server's {@code ide_} family exists to get past. The
 * checkout is already known here: a reader who linked a window told Jeffrey which project this
 * profile is about, and the IDE only offers windows it considers trusted projects.
 *
 * <p>Three things have to be true before a directory is handed over, and each of them is somebody's
 * decision rather than an inference:
 *
 * <ol>
 *   <li>the installation turned the feature on ({@code jeffrey.microscope.ai.source-access.enabled}),
 *       which is off by default — sending a reader's source to a model is not a default;</li>
 *   <li>the reader linked a window to this profile, in the IDE picker;</li>
 *   <li>the path that window reported is still a directory that exists.</li>
 * </ol>
 *
 * <p>Nothing here searches, guesses a project root, or falls back to a nearby directory. A profile
 * with no link has no sources, which is the same answer as before the feature existed.
 */
public final class IdeProfileSourceAccess implements ProfileSourceAccess {

    private static final Logger LOG = LoggerFactory.getLogger(IdeProfileSourceAccess.class);

    private final IdeBridge ideBridge;
    private final boolean enabled;

    public IdeProfileSourceAccess(IdeBridge ideBridge, boolean enabled) {
        this.ideBridge = ideBridge;
        this.enabled = enabled;
    }

    @Override
    public Optional<SourceAccess> forProfile(String profileId) {
        if (!enabled || profileId == null) {
            return Optional.empty();
        }
        IdeTargetStatus status = ideBridge.targetStatus(profileId);
        if (!status.linked() || status.basePath() == null || status.basePath().isBlank()) {
            return Optional.empty();
        }

        Path root = Path.of(status.basePath());
        if (!Files.isDirectory(root)) {
            LOG.warn("Linked IDE project is not a readable directory, no sources for the analysis: "
                    + "profile_id={} base_path={}", profileId, status.basePath());
            return Optional.empty();
        }

        LOG.debug("AI analysis may read the linked checkout: profile_id={} base_path={}",
                profileId, root);
        return Optional.of(new SourceAccess(root));
    }
}
