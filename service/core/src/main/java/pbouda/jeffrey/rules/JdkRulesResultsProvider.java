package pbouda.jeffrey.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.util.IPreferenceValueProvider;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.flightrecorder.rules.IResult;
import org.openjdk.jmc.flightrecorder.rules.IRule;
import org.openjdk.jmc.flightrecorder.rules.ResultProvider;
import org.openjdk.jmc.flightrecorder.rules.RuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Json;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

public class JdkRulesResultsProvider implements RulesResultsProvider {

    private static final Logger LOG = LoggerFactory.getLogger(JdkRulesResultsProvider.class);

    @Override
    public JsonNode results(Path recording) {
        try (ExecutorService executor = Executors.newFixedThreadPool(200)) {
            IItemCollection events = JfrLoaderToolkit.loadEvents(recording.toFile());
            return generateResults(events, executor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode generateResults(IItemCollection events, Executor executor) throws ExecutionException, InterruptedException {
        List<CompletableFuture<Optional<IResult>>> futures = RuleRegistry.getRules().stream()
                .map(rule -> evaluate(rule, events, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .join();

        ArrayNode results = Json.createArray();
        for (CompletableFuture<Optional<IResult>> future : futures) {
            Optional<IResult> resultOpt = future.join();

            if (resultOpt.isPresent()) {
                IResult result = resultOpt.get();
                ObjectNode json = Json.createObject()
                        .put("severity", result.getSeverity().toString())
                        .put("ruleId", result.getRule().getId())
                        .put("explanation", result.getExplanation())
                        .put("summary", result.getSummary())
                        .put("solution", result.getSolution());
                results.add(json);
            }
        }

        return results;
    }

    public static CompletableFuture<Optional<IResult>> evaluate(IRule rule, IItemCollection events, Executor executor) {
        RunnableFuture<IResult> evaluation = rule.createEvaluation(
                events, IPreferenceValueProvider.DEFAULT_VALUES, new ResultProvider());
        executor.execute(evaluation);
        return CompletableFuture.supplyAsync(() -> get(evaluation), executor);
    }

    public static <T> Optional<T> get(Future<T> resultFuture) {
        try {
            return Optional.of(resultFuture.get());
        } catch (Exception e) {
            LOG.error("Error getting result: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
