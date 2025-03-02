/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.reader.jfr.data;

import com.fasterxml.jackson.databind.JsonNode;
import org.openjdk.jmc.common.IDisplayable;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.flightrecorder.rules.*;
import org.openjdk.jmc.flightrecorder.rules.util.RulesToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.analysis.AutoAnalysisResult;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class AutoAnalysisDataProvider implements JfrSpecificDataProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AutoAnalysisDataProvider.class);

    public static final String CACHE_KEY = "jfr_specific_auto_analysis";

    @Override
    public JfrSpecificData provide(List<Path> recordings) {
        List<AutoAnalysisResult> list = generate(recordings).stream()
                .sorted(Comparator.comparing(a -> a.severity().order()))
                .toList();

        return new JfrSpecificData(CACHE_KEY, Json.toTree(list));
    }

    public static List<AutoAnalysisResult> generate(List<Path> recordings) {
        try {
            List<File> files = recordings.stream()
                    .map(Path::toFile)
                    .toList();

            IItemCollection events = JfrLoaderToolkit.loadEvents(files);
            List<Map.Entry<IRule, Future<IResult>>> futures = RulesToolkit.evaluateParallel(
                            RuleRegistry.getRules(), events, null, 0)
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
            throw new RuntimeException("Got exception when creating report for " + recordings, t);
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
                score != null ? score.displayUsing(IDisplayable.AUTO) : null);
    }
}
