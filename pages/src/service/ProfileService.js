export default class ProfileService {
    getAllProfiles() {
        return fetch('http://localhost:8080/profiles')
            .then((res) => res.json());
    }
}
