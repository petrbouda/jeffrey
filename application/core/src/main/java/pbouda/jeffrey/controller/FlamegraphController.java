package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import pbouda.jeffrey.WorkingDirectory;
import pbouda.jeffrey.controller.model.GenerateRequest;
import pbouda.jeffrey.controller.model.GenerateWithRangeRequest;
import pbouda.jeffrey.controller.model.GetFlamegraphRequest;
import pbouda.jeffrey.flamegraph.EventType;
import pbouda.jeffrey.flamegraph.FlamegraphGenerator;
import pbouda.jeffrey.repository.FlamegraphFile;
import pbouda.jeffrey.repository.FlamegraphRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/flamegraph")
public class FlamegraphController {

    private static final Logger LOG = LoggerFactory.getLogger(FlamegraphController.class);

    private final FlamegraphGenerator generator;
    private final FlamegraphRepository repository;

    public FlamegraphController(FlamegraphGenerator generator, FlamegraphRepository repository) {
        this.generator = generator;
        this.repository = repository;
    }

    @GetMapping
    public List<FlamegraphFile> list() {
        return repository.list("data");
    }

    @PostMapping("/single")
    public String getSingle(@RequestBody GetFlamegraphRequest request) {
        return repository.content(request.filename());
    }

    @PostMapping("/generate")
    public List<FlamegraphFile> generate(@RequestBody GenerateRequest request) {
        for (EventType type : request.eventTypes()) {
            String outputName = request.profile()
                    .replaceFirst(".jfr", STR."-\{type.name().toLowerCase()}");

            generator.generate(request.profile(), outputName, type);
            LOG.info("Flamegraph generated: {}", outputName);
        }

        return repository.list("data");
    }

    @PostMapping("/delete")
    public void delete(@RequestBody JsonNode filename) throws IOException {
        Path flamegraphPath = WorkingDirectory.GENERATED_DIR.resolve(filename.asText());
        if (Files.exists(flamegraphPath)) {
            Files.delete(flamegraphPath);
            LOG.info(STR."Flamegraph deleted: \{flamegraphPath}");
        } else {
            LOG.warn(STR."Cannot delete the flamegraph, the file does not exist: \{flamegraphPath}");
        }
    }

    @PostMapping("/generateRange")
    public List<FlamegraphFile> generateRange(@RequestBody GenerateWithRangeRequest request) {
        for (EventType type : List.of(request.eventType())) {
            String outputName = request.flamegraphName();
            TimeRange timeRange = request.timeRange();
            generator.generate(request.profile(), outputName, type, millis(timeRange.start()), millis(timeRange.end()));
            LOG.info("Flamegraph generated: {}", outputName);
        }

        return repository.list("data");
    }

    private static long millis(int[] time) {
        return millis(time[0], time[1]);
    }

    private static long millis(int seconds, int millis) {
        return seconds * 1000L + millis;
    }
}
