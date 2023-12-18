package pbouda.jeffrey;

import io.helidon.config.Config;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;

import java.util.Map;

public class FlamegraphService implements HttpService {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    FlamegraphService() {
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
