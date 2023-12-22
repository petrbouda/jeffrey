package pbouda.jeffrey;

import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;

public class CorsAllowAllFeature implements HttpFeature {

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.addFilter((chain, _, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
            chain.proceed();
        });
    }
}
