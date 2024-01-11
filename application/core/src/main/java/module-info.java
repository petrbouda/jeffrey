module pbouda.jeffrey {
    requires pbouda.jeffrey.generator.heatmap;

    requires com.fasterxml.jackson.databind;
    requires org.openjdk.jmc.flightrecorder;
    requires org.slf4j;

    requires spring.beans;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.web;
    requires spring.webflux;
}
