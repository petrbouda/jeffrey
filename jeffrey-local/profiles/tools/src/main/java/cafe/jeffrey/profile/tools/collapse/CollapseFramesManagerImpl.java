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

package cafe.jeffrey.profile.tools.collapse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provider.profile.repository.ProfileCacheRepository;
import cafe.jeffrey.provider.profile.repository.ProfileToolsRepository;
import cafe.jeffrey.provider.profile.repository.ProfileToolsRepository.StacktraceRecord;
import cafe.jeffrey.provider.profile.model.EventFrame;
import cafe.jeffrey.provider.profile.writer.SingleThreadHasher;

import java.util.*;

public class CollapseFramesManagerImpl implements CollapseFramesManager {

    private static final Logger LOG = LoggerFactory.getLogger(CollapseFramesManagerImpl.class);

    private static final int PREVIEW_LIMIT = 10;

    private final ProfileToolsRepository toolsRepository;
    private final ProfileCacheRepository cacheRepository;

    public CollapseFramesManagerImpl(
            ProfileToolsRepository toolsRepository,
            ProfileCacheRepository cacheRepository) {

        this.toolsRepository = toolsRepository;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public CollapsePreviewResult preview(CollapseRequest request) {
        LOG.debug("Previewing frame collapse: patterns={} label={}", request.patterns(), request.label());

        Set<Long> allMatchingHashes = new LinkedHashSet<>();
        List<ProfileToolsRepository.FrameSample> allSamples = new ArrayList<>();

        for (String pattern : request.patterns()) {
            allMatchingHashes.addAll(toolsRepository.findMatchingFrameHashes(pattern));
            if (allSamples.size() < PREVIEW_LIMIT) {
                allSamples.addAll(toolsRepository.sampleMatchingFrames(pattern, PREVIEW_LIMIT));
            }
        }

        int matchingFrames = allMatchingHashes.size();
        int affectedStacktraces = allMatchingHashes.isEmpty() ? 0
                : toolsRepository.findAffectedStacktraces(new ArrayList<>(allMatchingHashes)).size();
        var samples = allSamples.stream().distinct().limit(PREVIEW_LIMIT).toList();

        return new CollapsePreviewResult(matchingFrames, affectedStacktraces, samples);
    }

    @Override
    public CollapseApplyResult execute(CollapseRequest request) {
        LOG.info("Executing frame collapse: patterns={} label={}", request.patterns(), request.label());

        Set<Long> allMatchingHashes = new LinkedHashSet<>();
        for (String pattern : request.patterns()) {
            allMatchingHashes.addAll(toolsRepository.findMatchingFrameHashes(pattern));
        }

        List<Long> matchingFrameHashes = new ArrayList<>(allMatchingHashes);
        if (matchingFrameHashes.isEmpty()) {
            return new CollapseApplyResult(0, 0);
        }

        SingleThreadHasher hasher = new SingleThreadHasher();

        // Create synthetic frame
        EventFrame syntheticFrame = new EventFrame(request.label(), "", "Interpreted", 0, 0);
        long syntheticFrameHash = hasher.hashFrame(syntheticFrame);
        toolsRepository.insertSyntheticFrame(syntheticFrameHash, request.label());

        Set<Long> matchingSet = new HashSet<>(matchingFrameHashes);
        List<StacktraceRecord> affected = toolsRepository.findAffectedStacktraces(matchingFrameHashes);

        Map<Long, Long> oldToNewMapping = new HashMap<>();
        Map<Long, StacktraceRecord> newStacktraces = new LinkedHashMap<>();
        int mergedCount = 0;

        for (StacktraceRecord st : affected) {
            long[] collapsed = collapseConsecutive(st.frameHashes(), matchingSet, syntheticFrameHash);
            long newHash = hasher.hashStackTrace(collapsed);

            oldToNewMapping.put(st.stacktraceHash(), newHash);

            if (newStacktraces.containsKey(newHash)) {
                mergedCount++;
            } else {
                newStacktraces.put(newHash, new StacktraceRecord(newHash, st.typeId(), collapsed, st.tagIds()));
            }
        }

        // Apply the transformation
        if (!oldToNewMapping.isEmpty()) {
            toolsRepository.applyStacktraceTransformation(oldToNewMapping, new ArrayList<>(newStacktraces.values()));
        }

        // Cleanup orphaned data
        toolsRepository.deleteOrphanedStacktraces();
        toolsRepository.deleteOrphanedFrames();
        toolsRepository.deleteOrphanedThreads();
        cacheRepository.clearAll();

        LOG.info("Frame collapse completed: affected_stacktraces={} merged={}", affected.size(), mergedCount);

        return new CollapseApplyResult(affected.size(), mergedCount);
    }

    /**
     * Replace consecutive runs of matching frames with a single synthetic frame hash.
     */
    static long[] collapseConsecutive(long[] frameHashes, Set<Long> matchingSet, long syntheticHash) {
        List<Long> result = new ArrayList<>();
        boolean lastWasMatch = false;

        for (long hash : frameHashes) {
            if (matchingSet.contains(hash)) {
                if (!lastWasMatch) {
                    result.add(syntheticHash);
                    lastWasMatch = true;
                }
                // Skip consecutive matches (collapsed into the synthetic)
            } else {
                result.add(hash);
                lastWasMatch = false;
            }
        }

        return result.stream().mapToLong(Long::longValue).toArray();
    }
}
