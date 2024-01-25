import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class ProfileService {
    listRecordings() {
        return axios.get(GlobalVars.url + '/profiles/recording', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    listProfiles() {
        return axios.get(GlobalVars.url + '/profiles', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    createProfile(jfrName) {
        const content = {
            jfrName: jfrName
        };

        return axios.post(GlobalVars.url + '/profiles', content, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    deleteProfile(profileId) {
        const content = {
            profileIds: [profileId]
        };

        return axios.post(GlobalVars.url + '/profiles/delete', content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    deleteRecording(filename) {
        const content = {
            filenames: [filename]
        };

        return axios.post(GlobalVars.url + '/profiles/recording/delete', content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }
}
