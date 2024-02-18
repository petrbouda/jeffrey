import GlobalVars from '@/service/GlobalVars';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class FlamegraphService {
    static generate(eventType) {
        const content = {
            profileId: PrimaryProfileService.get().id,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateRange(profileId, name, eventType, timeRange) {
        const content = {
            profileId: profileId,
            name: name,
            timeRange: timeRange,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate/range', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateDiff(primaryProfileId, secondaryProfileId, flamegraphName, eventType, timeRange) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            name: flamegraphName,
            timeRange: timeRange,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate/diff', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static list(profileId) {
        const content = {
            profileId: profileId,
        };

        return axios.post(GlobalVars.url + '/flamegraph/all', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static export(profileId, flamegraphId, eventType) {
        const content = {
            profileId: profileId,
            flamegraphId: flamegraphId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/export', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static testData() {
        return axios.get(GlobalVars.url + '/flamegraph/test', HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    static get(profileId, flamegraphId, eventType) {
        const content = {
            profileId: profileId,
            flamegraphId: flamegraphId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static getPredefined(profileId, eventType) {
        const content = {
            profileId: profileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate/predefined', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static delete(profileId, flamegraphId) {
        const content = {
            profileId: profileId,
            flamegraphId: flamegraphId
        };

        return axios.post(GlobalVars.url + '/flamegraph/delete', content, HttpUtils.JSON_CONTENT_TYPE_HEADER);
    }
}
