import { ref } from 'vue';

export default class PrimaryProfileService {
    static profile = ref('<none>');
    static fontStyle = {
        color: "green",
        "font-weight": "bold"
    }

    static {
        let profile = this.get();
        if (profile) {
            PrimaryProfileService.profile.value = profile.name;
        }
    }

    static equals(id) {
        return PrimaryProfileService.get() != null && PrimaryProfileService.get().id === id
    }

    static update(profile) {
        sessionStorage.setItem('primary-profile', JSON.stringify(profile));
        PrimaryProfileService.profile.value = profile.name;
    }

    static get() {
        return JSON.parse(sessionStorage.getItem('primary-profile'));
    }

    static id() {
        return PrimaryProfileService.get().id
    }

    static name() {
        return PrimaryProfileService.profile.value.replace('.jfr', '')
    }
}
