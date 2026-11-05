package jeffrey.testapp.server.service;

import jeffrey.testapp.server.Person;

import java.util.List;
import java.util.Optional;

public interface PersonService {

    Optional<Person> getRandomPerson();

    List<Person> getNPersons(int count);
}
