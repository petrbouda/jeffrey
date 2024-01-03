package pbouda.jeffrey.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import pbouda.jeffrey.flamegraph.EventType;
import pbouda.jeffrey.flamegraph.FlamegraphGenerator;
import pbouda.jeffrey.repository.FlamegraphFile;
import pbouda.jeffrey.repository.FlamegraphRepository;

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
        return repository.list();
    }

    @GetMapping("/show")
    public String getSingle(@RequestParam("name") String filename) {
        return repository.content(filename);
    }

    @PostMapping("/generate")
    public void generate(@RequestBody GenerateRequest request) {
        for (EventType type : request.types()) {
            String output = request.profile()
                    .replaceFirst(".jfr", STR."-\{type.name().toLowerCase()}.html");

            Path generated = generator.generate(request.profile(), output, type);
            LOG.info("Flamegraph generated: {}", generated);
        }
    }
}
