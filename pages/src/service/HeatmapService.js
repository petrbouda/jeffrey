import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';
import PrimaryProfileService from '@/service/PrimaryProfileService';

export default class HeatmapService {
    static startup(profileId, eventType) {
        const content = {
            profileId: profileId,
            heatmapName: eventType.toLowerCase(),
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/heatmap/startup', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
