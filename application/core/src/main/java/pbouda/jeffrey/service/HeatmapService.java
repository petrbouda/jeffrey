package pbouda.jeffrey.service;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import pbouda.jeffrey.ResourceUtils;

public class HeatmapService implements HttpService {

    @Override
    public void routing(HttpRules httpRules) {
        httpRules
                .get("/basic", (_, resp) -> resp.send(ResourceUtils.readTextFile("/data/heatmap-test.json")));
    }
}

