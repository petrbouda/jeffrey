package pbouda.jeffrey.init;

import picocli.CommandLine;

public class VersionProvider implements CommandLine.IVersionProvider {

    public static final String[] NOT_AVAILABLE = {"not-available"};

    @Override
    public String[] getVersion() {
        try {
            String version = ResourceUtils.readFromClasspath("jeffrey-tag.txt");
            if (version.isBlank()) {
                return NOT_AVAILABLE;
            } else {
                return new String[]{version};
            }
        } catch (Exception ex) {
            return NOT_AVAILABLE;
        }
    }
}
