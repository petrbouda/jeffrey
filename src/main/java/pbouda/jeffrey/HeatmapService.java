package pbouda.jeffrey;

import io.helidon.config.Config;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class HeatmapService implements HttpService {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    HeatmapService() {
        this(Config.global().get("heatmap"));
    }

    HeatmapService(Config appConfig) {
//        greeting.set(appConfig.get("greeting").asString().orElse("Ciao"));
    }

    @Override
    public void routing(HttpRules httpRules) {
        httpRules
                .get("/basic", this::getDefaultMessageHandler);
//                .get("/{name}", this::getMessageHandler)
//                .put("/greeting", this::updateGreetingHandler);
    }

    private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
        response.send(basicHeatmap());
    }

    public String basicHeatmap() {
        InputStream instr = HeatmapService.class.getResourceAsStream("/data/heatmap-test.json");
        try (var reader = new BufferedInputStream(instr)) {
            return new String(reader.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

