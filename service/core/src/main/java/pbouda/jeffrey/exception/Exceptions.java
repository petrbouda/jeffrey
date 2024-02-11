package pbouda.jeffrey.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

public abstract class Exceptions {

    public static final Supplier<ResponseStatusException> PROFILE_NOT_FOUND =
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");

    public static final Supplier<ResponseStatusException> FLAMEGRAPH_NOT_FOUND =
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flamegraph not found");

    public static Supplier<ResponseStatusException> serverError(String message) {
        return () -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

}
