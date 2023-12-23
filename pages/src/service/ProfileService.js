import GlobalVars from '@/service/GlobalVars';

export default class ProfileService {
    getAllProfiles() {
        return fetch(GlobalVars.url + '/profiles')
            .then((res) => res.json());
    }
}
