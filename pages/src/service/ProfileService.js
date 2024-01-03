import GlobalVars from '@/service/GlobalVars';

export default class ProfileService {
    list() {
        return fetch(GlobalVars.url + '/profiles')
            .then((res) => res.json());
    }
}
