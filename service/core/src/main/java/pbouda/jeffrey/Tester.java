package pbouda.jeffrey;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.jfr.configuration.ProfileInformationProvider;

import java.nio.file.Path;

public class Tester {

    public static void main(String[] args) {
        Path path = Path.of("/home/pbouda/.jeffrey/recordings/power-trading-management-service-jfr-profile.jfr");
        ObjectNode jsonNode = new ProfileInformationProvider(path)
                .get();

        System.out.println();
    }
}
