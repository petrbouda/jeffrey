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

package cafe.jeffrey.profile.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provider.profile.api.ProfileCacheRepository;
import cafe.jeffrey.provider.profile.api.ProfileFrameRepository;

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
