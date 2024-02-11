import { ref } from 'vue';

export default class SecondaryProfileService {
    static profile = ref('<none>');
    static fontStyle = {
        color: "#78909c",
        "font-weight": "bold"
    }

    static {
        let profile = this.get();
        if (profile) {
            SecondaryProfileService.profile.value = profile.name;
        }
    }

    static equals(id) {
        return SecondaryProfileService.get() != null && SecondaryProfileService.get().id === id
    }

    static update(profile) {
        sessionStorage.setItem('secondary-profile', JSON.stringify(profile));
        SecondaryProfileService.profile.value = profile.name;
    }

    static get() {
        return JSON.parse(sessionStorage.getItem('secondary-profile'));
    }

    static id() {
        return SecondaryProfileService.get().id
    }

    static name() {
        return SecondaryProfileService.profile.value.replace('.jfr', '')
    }
}
