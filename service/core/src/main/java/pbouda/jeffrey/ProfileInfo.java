package pbouda.jeffrey;

public abstract class ProfileInfo {

    public static String directoryName(String profileFilename) {
        return profileFilename
                .replace(".jfr", "")
                .replace(" ", "_");
    }
}
