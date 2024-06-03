package pbouda.jeffrey.rules;

import org.openjdk.jmc.common.IDisplayable;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.util.LabeledIdentifier;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.flightrecorder.rules.*;
import org.openjdk.jmc.flightrecorder.rules.util.RulesToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class RuleResultsGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(RuleResultsGenerator.class);

    public static List<AnalysisItem> generate(Path file) {
        try {
            IItemCollection events = JfrLoaderToolkit.loadEvents(file.toFile());
            List<Map.Entry<IRule, Future<IResult>>> futures = RulesToolkit.evaluateParallel(
                            RuleRegistry.getRules(), events, null, 0)
                    .entrySet().stream()
                    .sorted(Comparator.comparing((o) -> o.getKey().getId()))
                    .toList();

            List<AnalysisItem> results = new ArrayList<>();

            for (Map.Entry<IRule, Future<IResult>> resultEntry : futures) {
                IResult result;
                try {
                    result = resultEntry.getValue().get();
                } catch (Throwable t) {
                    LOG.warn("Cannot get a Analysis result: rule={}", resultEntry.getKey().getName(), t);
                    continue;
                }

                if (result != null) {
                    IQuantity score = result.getResult(TypedResult.SCORE);
                    var item = new AnalysisItem(
                            result.getRule(),
                            result.getSeverity(),
                            ResultToolkit.populateMessage(result, result.getExplanation(), false),
                            ResultToolkit.populateMessage(result, result.getSummary(), false),
                            ResultToolkit.populateMessage(result, result.getSolution(), false),
                            score != null ? score.displayUsing(IDisplayable.AUTO) : null
                    );

                    results.add(item);
                }
            }

            return results;
        } catch (Throwable t) {
            throw new RuntimeException("Got exception when creating report for " + file, t);
        }
    }

    private static String toString(Object member) {
        if (member instanceof IQuantity quantity) {
            return quantity.displayUsing(IDisplayable.AUTO);
        } else if (member instanceof LabeledIdentifier identifier) {
            return identifier.getName();
        } else {
            return String.valueOf(member);
        }
    }
}
