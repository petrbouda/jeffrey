import { ref } from 'vue';

export default class SelectedProfileService {
    static profile = ref('<none>');

    static {
        this.profile.value = this.get()
    }

    static update(profile) {
        localStorage.setItem('primary-profile', profile.filename);
        SelectedProfileService.profile.value = profile.filename;
    }

    static get() {
        return localStorage.getItem('primary-profile');
    }
}
