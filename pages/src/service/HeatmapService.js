import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';
import SelectedProfileService from '@/service/SelectedProfileService';

export default class HeatmapService {
    static startup(eventType) {
        const content = {
            profileId: SelectedProfileService.get().id,
            heatmapName: eventType.toLowerCase(),
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/heatmap/startup', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
