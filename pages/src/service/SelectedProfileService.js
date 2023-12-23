import { ref } from 'vue';
import GlobalVars from '@/service/GlobalVars';

export default class SelectedProfileService {

    static profile = ref('<none>');

    static update(profile) {
        const requestOptions = {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify(profile)
        };

        fetch(GlobalVars.url + '/profiles/select', requestOptions)
            .then(() => SelectedProfileService.profile.value = profile.filename);
    }
}
