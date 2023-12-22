package pbouda.jeffrey;

import java.nio.file.Path;

public abstract class WorkingDirectory {

    private static final Path USER_HOME_DIR = Path.of(System.getProperty("user.home"));
    private static final String WORKING_DIR_NAME = ".jeffrey";

    public static final Path PATH = USER_HOME_DIR.resolve(WORKING_DIR_NAME);


}
