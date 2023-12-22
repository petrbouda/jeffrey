package pbouda.jeffrey.service;

import io.helidon.config.Config;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import pbouda.jeffrey.ResourceUtils;

public class FlamegraphService implements HttpService {

    public FlamegraphService() {
        this(Config.global().get("heatmap"));
    }

    FlamegraphService(Config appConfig) {
//        greeting.set(appConfig.get("greeting").asString().orElse("Ciao"));
    }

    @Override
    public void routing(HttpRules httpRules) {
        httpRules
                .get("/basic", (_, resp) -> resp.send(ResourceUtils.readTextFile("/data/java-stacks-test.json")));
    }
}
