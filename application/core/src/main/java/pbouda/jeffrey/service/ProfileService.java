package pbouda.jeffrey.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import pbouda.jeffrey.repository.ProfileRepository;
import pbouda.jeffrey.repository.WorkingDirProfileRepository;

public class ProfileService implements HttpService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ProfileRepository repository;

    public ProfileService() {
        this(new WorkingDirProfileRepository());
    }

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public void routing(HttpRules httpRules) {
        httpRules.get((_, resp) -> MAPPER.writeValue(resp.outputStream(), repository.list()));
    }
}
