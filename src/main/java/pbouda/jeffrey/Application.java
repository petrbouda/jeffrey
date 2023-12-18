package pbouda.jeffrey;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.staticcontent.StaticContentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        // load logging configuration
        LogConfig.configureRuntime();

        // initialize global config from default configuration
        Config config = Config.create();
        Config.global(config);

        HttpRouting.Builder routing = HttpRouting.builder()
                .register("/", StaticContentService.builder("/static").welcomeFileName("index.html"))
                .register("/heatmap", new HeatmapService())
                .register("/flamegraph", new FlamegraphService());

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(routing)
                .build()
                .start();

        LOG.info("WEB server is up! http://localhost:{}", server.port());
    }
}
