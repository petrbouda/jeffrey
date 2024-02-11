import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class ProfileService {

    list() {
        return axios.get(GlobalVars.url + '/profiles', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    create(recordingFilename) {
        const content = {
            recordingFilename: recordingFilename
        };

        return axios.post(GlobalVars.url + '/profiles', content, HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    delete(profileId) {
        const content = {
            profileIds: [profileId]
        };

        return axios.post(GlobalVars.url + '/profiles/delete', content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }
}
