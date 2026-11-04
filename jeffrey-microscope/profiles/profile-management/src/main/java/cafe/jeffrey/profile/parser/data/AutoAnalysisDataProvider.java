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

package cafe.jeffrey.profile.parser.data;

import org.openjdk.jmc.common.IDisplayable;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.item.ItemCollectionToolkit;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.flightrecorder.rules.*;
import org.openjdk.jmc.flightrecorder.rules.util.RulesToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Runs the JMC rule set over a recording.
 *
 * <p>JMC takes every file at once and builds one item collection from them, so the rules see the
 * whole recording rather than one file of it — a rule that reasons about the run as a whole (GC
 * pressure over the window, say) would otherwise fire on a fragment.
 *
 * <p>A recording that holds two chains of chunks ({@link RecordingChains}: async-profiler with
 * {@code jfrsync} writes the JDK's own recording beside its own) is loaded one chain at a time and
 * the collections merged before the rules run. Loaded together, the two chains' differing
 * definitions of the same event types race: JMC keeps whichever it meets first under the name and
 * renames the other, so the rules that read the recording's settings would judge the same file
 * differently from one evaluation to the next. Loaded apart, each chain keeps its types under their
 * names, and the rules still see both: context switches need the JDK's events and async-profiler's
 * at once, allocation rules read async-profiler's settings and method profiling the JDK's.
 */
public final class AutoAnalysisDataProvider implements Function<List<Path>, List<AutoAnalysisResult>> {

    private static final Logger LOG = LoggerFactory.getLogger(AutoAnalysisDataProvider.class);

    private final TempDirFactory tempDirFactory;

    public AutoAnalysisDataProvider(TempDirFactory tempDirFactory) {
        this.tempDirFactory = tempDirFactory;
    }

    @Override
    public List<AutoAnalysisResult> apply(List<Path> recordings) {
        return generate(recordings);
    }

    /**
     * @return every rule's answer, once each, ordered by the rule's id
     */
    public List<AutoAnalysisResult> generate(List<Path> recordings) {
        try (TempDirectory scratch = tempDirFactory.newTempDir()) {
            IItemCollection events = load(RecordingChains.of(recordings, scratch.path()));
            List<Map.Entry<IRule, Future<IResult>>> futures =
                    RulesToolkit.evaluateParallel(uniqueById(RuleRegistry.getRules()), events, null, 0)
                            .entrySet().stream()
                            .sorted(Comparator.comparing((o) -> o.getKey().getId()))
                            .toList();

            List<AutoAnalysisResult> results = new ArrayList<>();

            for (Map.Entry<IRule, Future<IResult>> resultEntry : futures) {
                IResult result;
                try {
                    result = resultEntry.getValue().get();
                } catch (Throwable t) {
                    LOG.warn("Cannot get a Analysis result: rule={}", resultEntry.getKey().getName(), t);
                    continue;
                }

                if (result != null) {
                    results.add(toAnalysisItem(result));
                }
            }

            return results;
        } catch (Throwable t) {
            if (t instanceof Error error) {
                throw error;
            }
            throw Exceptions.internal("Got exception when creating report for " + recordings, (Exception) t);
        }
    }

    /**
     * Each rule once, the first of its id in the registry's order. JMC 9.1.2's rules jar lists six
     * rules twice in its service file (Class Leak, DMS Incidents, DebugNonSafepoints, GC Setup,
     * Discouraged VM Options, Exceptional Dump Reason). The packaged jar runs on the class path,
     * where the service loader drops the repeated names; on the module path, which Microscope's
     * module requires the jar on — the tests, a run from the IDE — it instantiates every line, and
     * each of the six answered twice. The answers no longer depend on how the jar is loaded.
     */
    static List<IRule> uniqueById(Collection<IRule> rules) {
        Map<String, IRule> byId = new LinkedHashMap<>();
        for (IRule rule : rules) {
            byId.putIfAbsent(rule.getId(), rule);
        }
        return List.copyOf(byId.values());
    }

    /**
     * One chain as one collection; several, each loaded on its own and merged — the JDK's own
     * recording first. Some rules take the first recording's facts they meet (its start, its
     * length), and they were written against the JVM's own recording: met after async-profiler's,
     * the CPU-load rules find too few samples in a recording holding nearly nine hundred.
     */
    private static IItemCollection load(Map<RecordingEventSource, List<Path>> chains) throws Exception {
        List<IItemCollection> collections = new ArrayList<>();
        List<RecordingEventSource> order = chains.keySet().stream()
                .sorted(Comparator.comparing((RecordingEventSource producer) -> producer != RecordingEventSource.JDK))
                .toList();
        for (RecordingEventSource producer : order) {
            collections.add(JfrLoaderToolkit.loadEvents(chains.get(producer).stream().map(Path::toFile).toList()));
        }
        if (collections.size() == 1) {
            return collections.getFirst();
        }
        return ItemCollectionToolkit.merge(collections::stream);
    }

    private static AutoAnalysisResult toAnalysisItem(IResult result) {
        IQuantity score = result.getResult(TypedResult.SCORE);
        return new AutoAnalysisResult(
                result.getRule().getName(),
                AutoAnalysisResult.Severity.valueOf(result.getSeverity().name()),
                ResultToolkit.populateMessage(result, result.getExplanation(), false),
                ResultToolkit.populateMessage(result, result.getSummary(), false),
                ResultToolkit.populateMessage(result, result.getSolution(), false),
                score != null ? score.displayUsing(IDisplayable.AUTO) : null,
                result.getRule().getTopic());
    }
}
