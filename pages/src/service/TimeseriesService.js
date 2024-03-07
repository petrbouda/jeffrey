import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class TimeseriesService {
    static generate(primaryProfileId, eventType) {
        const content = {
            primaryProfileId: primaryProfileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/complete', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateDiff(primaryProfileId, secondaryProfileId, eventType) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/diff', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
