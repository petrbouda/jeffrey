import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';
import SelectedProfileService from '@/service/SelectedProfileService';

export default class HeatmapService {
    static getSingle(eventType) {
        const content = {
            profile: SelectedProfileService.get(),
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/heatmap/single', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
