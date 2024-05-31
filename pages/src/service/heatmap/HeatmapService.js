import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class HeatmapService {
    static startup(profileId, eventType, useWeight) {
        const content = {
            profileId: profileId,
            heatmapName: eventType.toLowerCase(),
            eventType: eventType,
            useWeight: useWeight
        };

        return axios.post(GlobalVars.url + '/heatmap/startup', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
