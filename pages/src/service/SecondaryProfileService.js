import { ref } from 'vue';

export default class SecondaryProfileService {
    static profile = ref(null);
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

    static remove() {
        sessionStorage.removeItem('secondary-profile');
        SecondaryProfileService.profile.value = null;
    }

    static get() {
        return JSON.parse(sessionStorage.getItem('secondary-profile'));
    }

    static id() {
        if (SecondaryProfileService.get() != null) {
            return SecondaryProfileService.get().id
        } else {
            return null
        }
    }

    static name() {
        return SecondaryProfileService.profile.value.replace('.jfr', '')
    }
}
