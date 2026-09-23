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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.common.event.GCHeapConfiguration;
import cafe.jeffrey.profile.heapdump.model.HeapDumpConfig;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpSession;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Determines whether a heap dump uses compressed oops, recording the chosen
 * value and its provenance to the heap-dump-config sidecar. Detection priority:
 * manual override → JFR event → heap inference → defaulted.
 *
 * <p>Two entry points: {@link #resolveAndStore} opens its own session for
 * heap inference; {@link #resolveAndStoreInSession} reuses a session that the
 * caller already has open so the initial index build is attributed to that
 * outer session's timings.
 */
public final class CompressedOopsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(CompressedOopsResolver.class);

    private static final String CONFIG_FILE = "heap-dump-config.json";

    private static final String CONFIG_DISPLAY_NAME = "Heap dump config";

    private static final long DEFAULT_TOTAL_OVERCOUNT = 0L;

    private enum Source {
        MANUAL, JFR, INFERRED, DEFAULTED;

        @Override
        public String toString() {
            return name();
        }
    }

    private final ProfileInfo profileInfo;

    private final HeapDumpSessionTemplate sessions;

    private final ProfileEventRepository eventRepository;

    private final HeapDumpReportStore reports;

    public CompressedOopsResolver(
            ProfileInfo profileInfo,
            HeapDumpSessionTemplate sessions,
            ProfileEventRepository eventRepository,
            HeapDumpReportStore reports) {

        this.profileInfo = profileInfo;
        this.sessions = sessions;
        this.eventRepository = eventRepository;
        this.reports = reports;
    }

    public HeapDumpConfig resolveAndStore(Boolean manualOverride) {
        return persist(resolveWithoutSession(manualOverride));
    }

    public HeapDumpConfig resolveAndStoreInSession(Boolean manualOverride, HeapDumpSession session) throws SQLException {
        return persist(resolveInSession(manualOverride, session));
    }

    public Optional<HeapDumpConfig> read() {
        return reports.read(CONFIG_FILE, HeapDumpConfig.class);
    }

    private Resolution resolveWithoutSession(Boolean manualOverride) {
        if (manualOverride != null) {
            return new Resolution(manualOverride, Source.MANUAL);
        }
        Optional<Boolean> jfrValue = detectFromJfr();
        if (jfrValue.isPresent()) {
            return new Resolution(jfrValue.get(), Source.JFR);
        }
        Optional<Boolean> inferred = inferFromHeap();
        if (inferred.isPresent()) {
            return new Resolution(inferred.get(), Source.INFERRED);
        }
        return new Resolution(false, Source.DEFAULTED);
    }

    private Resolution resolveInSession(Boolean manualOverride, HeapDumpSession session) throws SQLException {
        if (manualOverride != null) {
            return new Resolution(manualOverride, Source.MANUAL);
        }
        Optional<Boolean> jfrValue = detectFromJfr();
        if (jfrValue.isPresent()) {
            return new Resolution(jfrValue.get(), Source.JFR);
        }
        return new Resolution(session.view().metadata().compressedOops(), Source.INFERRED);
    }

    private HeapDumpConfig persist(Resolution resolution) {
        HeapDumpConfig config = new HeapDumpConfig(resolution.compressedOops(), resolution.source().toString(), DEFAULT_TOTAL_OVERCOUNT);
        reports.write(CONFIG_FILE, config, CONFIG_DISPLAY_NAME);
        LOG.info("Compressed oops resolved: compressedOops={} source={} profileId={}",
                resolution.compressedOops(), resolution.source(), profileInfo.id());
        return config;
    }

    private Optional<Boolean> detectFromJfr() {
        try {
            return eventRepository.latestJsonFields(Type.GC_HEAP_CONFIGURATION)
                    .map(fields -> Json.treeToValue(fields, GCHeapConfiguration.class))
                    .map(GCHeapConfiguration::usesCompressedOops);
        } catch (Exception e) {
            LOG.debug("Could not detect compressed oops from JFR events: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Boolean> inferFromHeap() {
        return sessions.execute(session -> session.view().metadata().compressedOops());
    }

    private record Resolution(boolean compressedOops, Source source) { }
}
