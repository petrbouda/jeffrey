import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class RulesService {
    static rules(profileId) {
        const content = {
            profileId: profileId
        };

        return axios.post(GlobalVars.url + '/rules', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
