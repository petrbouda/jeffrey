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
            profile: SelectedProfileService.get(),
            eventTypes: arrayOfCodes
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateRange(flamegraphName, eventType, start, end) {
        const content = {
            profile: SelectedProfileService.get(),
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
        return axios.get(GlobalVars.url + '/flamegraph', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    static getSingle(flamegraphName) {
        const content = {
            filename: flamegraphName
        };

        return axios.post(GlobalVars.url + '/flamegraph/single', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static delete(flamegraphName) {
        return axios.post(GlobalVars.url + '/flamegraph/delete', flamegraphName, HttpUtils.JSON_CONTENT_TYPE_HEADER);
    }
}
