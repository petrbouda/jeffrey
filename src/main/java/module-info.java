module pbouda.jeffrey {
    requires org.openjdk.jmc.flightrecorder;
    requires org.openjdk.jmc.common;

    requires org.slf4j;
    requires jul.to.slf4j;

    requires io.helidon.logging.common;
    requires io.helidon.config;
    requires io.helidon.webserver;
    requires io.helidon.webserver.staticcontent;
    requires jakarta.json;
}
