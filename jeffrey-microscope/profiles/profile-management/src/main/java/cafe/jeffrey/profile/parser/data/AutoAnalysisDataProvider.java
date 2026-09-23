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
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.flightrecorder.rules.*;
import org.openjdk.jmc.flightrecorder.rules.util.RulesToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class AutoAnalysisDataProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AutoAnalysisDataProvider.class);

    /**
     * Runs the JMC rule set over every file of the recording at once. JMC takes the files as a
     * list and builds one item collection from them, so the rules see the whole recording rather
     * than one file of it — a rule that reasons about the run as a whole (GC pressure over the
     * window, say) would otherwise fire on a fragment.
     */
    public static List<AutoAnalysisResult> generate(List<Path> recordings) {
        try {
            IItemCollection events = JfrLoaderToolkit.loadEvents(
                    recordings.stream().map(Path::toFile).toList());
            List<Map.Entry<IRule, Future<IResult>>> futures =
                    RulesToolkit.evaluateParallel(RuleRegistry.getRules(), events, null, 0)
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
