import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';
import Flamegraph from "@/service/Flamegraph";

export default class TimeseriesService {
    static generate(primaryProfileId, eventType, valueMode) {
        const content = {
            primaryProfileId: primaryProfileId,
            eventType: eventType,
            weightValueMode: valueMode === Flamegraph.WEIGHT_MODE
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/complete', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateWithSearch(primaryProfileId, eventType, search, valueMode) {
        const content = {
            primaryProfileId: primaryProfileId,
            eventType: eventType,
            search: search,
            weightValueMode: valueMode === Flamegraph.WEIGHT_MODE
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/complete/search', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateDiff(primaryProfileId, secondaryProfileId, eventType, valueMode) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            eventType: eventType,
            weightValueMode: valueMode === Flamegraph.WEIGHT_MODE
        };

        return axios.post(GlobalVars.url + '/timeseries/generate/diff', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
