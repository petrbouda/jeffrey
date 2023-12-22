module pbouda.jeffrey {
    requires org.openjdk.jmc.flightrecorder;
    requires org.openjdk.jmc.common;

    requires org.slf4j;
    requires jul.to.slf4j;

    requires io.helidon.config;
    requires io.helidon.logging.common;
    requires io.helidon.webserver;
    requires io.helidon.webserver.cors;

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
}
