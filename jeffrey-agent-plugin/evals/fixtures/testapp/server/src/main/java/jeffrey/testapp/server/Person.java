package jeffrey.testapp.server;

public record Person(
        Long id,
        String firstname,
        String lastname,
        String city,
        String country,
        String phone,
        String politicalOpinion) {

    public Person copyWithId(Long id) {
        return new Person(id, firstname, lastname, city, country, phone, politicalOpinion);
    }

    @Override
    public int hashCode() {
        return (int) (id / 10);
    }
}
