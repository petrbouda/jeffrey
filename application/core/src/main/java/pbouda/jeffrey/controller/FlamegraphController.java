package pbouda.jeffrey.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.ResourceUtils;

@RestController
@RequestMapping("/flamegraph")
public class FlamegraphController {

    @GetMapping("/basic")
    public String simple() {
        return ResourceUtils.readTextFile("/data/java-stacks-test.json");
    }
}
