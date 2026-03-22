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

package pbouda.jeffrey.profile.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.provider.profile.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.profile.repository.ProfileFrameRepository;

public class ProfileToolsManagerImpl implements ProfileToolsManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileToolsManagerImpl.class);

    private static final int PREVIEW_LIMIT = 10;

    private final ProfileFrameRepository frameRepository;
    private final ProfileCacheRepository cacheRepository;

    public ProfileToolsManagerImpl(
            ProfileFrameRepository frameRepository,
            ProfileCacheRepository cacheRepository) {

        this.frameRepository = frameRepository;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public RenamePreviewResult previewRename(RenameRequest request) {
        LOG.debug("Previewing frame rename: search={} replacement={}", request.search(), request.replacement());

        int count = frameRepository.countFramesByClassNameContaining(request.search());
        var samples = frameRepository.previewRename(request.search(), request.replacement(), PREVIEW_LIMIT);

        return new RenamePreviewResult(count, samples);
    }

    @Override
    public RenameResult executeRename(RenameRequest request) {
        LOG.info("Executing frame rename: search={} replacement={}", request.search(), request.replacement());

        int renamed = frameRepository.renameClassNames(request.search(), request.replacement());
        cacheRepository.clearAll();

        LOG.info("Frame rename completed: renamedFrames={}", renamed);
        return new RenameResult(renamed);
    }
}
