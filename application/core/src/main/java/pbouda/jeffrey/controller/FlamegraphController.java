package pbouda.jeffrey.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import pbouda.jeffrey.flamegraph.EventType;
import pbouda.jeffrey.flamegraph.FlamegraphGenerator;
import pbouda.jeffrey.repository.FlamegraphFile;
import pbouda.jeffrey.repository.FlamegraphRepository;

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

    @GetMapping("/show")
    public String getSingle(@RequestParam("name") String filename) {
        return repository.content(filename);
    }

    @PostMapping("/generate")
    public List<FlamegraphFile> generate(@RequestBody GenerateRequest request) {
        for (EventType type : request.types()) {
            String outputName = request.profile()
                    .replaceFirst(".jfr", STR."-\{type.name().toLowerCase()}");

            generator.generate(request.profile(), outputName, type);
            LOG.info("Flamegraph generated: {}", outputName);
        }

        return repository.list("data");
    }

    @PostMapping("/generateRange")
    public List<FlamegraphFile> generateRange(@RequestBody GenerateWithRangeRequest request) {
        for (EventType type : List.of(request.type())) {
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
