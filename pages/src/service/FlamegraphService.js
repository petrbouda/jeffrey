import GlobalVars from '@/service/GlobalVars';
import SelectedProfileService from '@/service/SelectedProfileService';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class FlamegraphService {
    static generate(eventTypes) {
        const arrayOfCodes = eventTypes.map(function (value) {
            return value.code;
        });

        const content = {
            profileId: SelectedProfileService.get().id,
            eventTypes: arrayOfCodes
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateRange(flamegraphName, eventType, start, end) {
        const content = {
            profileId: SelectedProfileService.get().id,
            flamegraphName: flamegraphName,
            timeRange: {
                start: start,
                end: end
            },
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/generateRange', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static list() {
        const content = {
            profileId: SelectedProfileService.get().id,
        };

        return axios.post(GlobalVars.url + '/flamegraph', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static getSingle(flamegraphId) {
        const content = {
            profileId: SelectedProfileService.get().id,
            flamegraphId: flamegraphId
        };

        return axios.post(GlobalVars.url + '/flamegraph/single', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static delete(flamegraphName) {
        return axios.post(GlobalVars.url + '/flamegraph/delete', flamegraphName, HttpUtils.JSON_CONTENT_TYPE_HEADER);
    }
}
