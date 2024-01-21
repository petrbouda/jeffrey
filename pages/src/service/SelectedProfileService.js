import { ref } from 'vue';

export default class SelectedProfileService {
    static profile = ref('<none>');

    static {
        let profile = this.get();
        if (profile) {
            SelectedProfileService.profile.value = profile.name;
        }
    }

    static update(profile) {
        sessionStorage.setItem('primary-profile', JSON.stringify(profile));
        SelectedProfileService.profile.value = profile.name;
    }

    static get() {
        return JSON.parse(sessionStorage.getItem('primary-profile'));
    }

    static profileName() {
        return SelectedProfileService.profile.value.replace('.jfr', '')
    }
}
