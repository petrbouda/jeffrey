package pbouda.jeffrey.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.ResourceUtils;

@RestController
@RequestMapping("/heatmap")
public class HeatmapController {

    @GetMapping("/basics")
    public String simple() {
        return ResourceUtils.readTextFile("/data/heatmap-test.json");
    }
}
