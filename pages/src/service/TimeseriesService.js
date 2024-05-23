import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class TimeseriesService {
    static generate(primaryProfileId, eventType, useWeight) {
        const content = {
            primaryProfileId: primaryProfileId,
            eventType: eventType,
            useWeight: useWeight
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/complete', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateWithSearch(primaryProfileId, eventType, search, useWeight) {
        const content = {
            primaryProfileId: primaryProfileId,
            eventType: eventType,
            search: search,
            useWeight: useWeight
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/complete/search', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateDiff(primaryProfileId, secondaryProfileId, eventType, useWeight) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            eventType: eventType,
            useWeight: useWeight
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/diff', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
