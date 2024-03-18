import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class FlamegraphService {

    static generateEventTypeRange(profileId, eventType, timeRange) {
        const content = {
            primaryProfileId: profileId,
            eventType: eventType,
            timeRange: timeRange
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate/range', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateEventTypeComplete(profileId, eventType) {
        const content = {
            primaryProfileId: profileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate/complete', content, HttpUtils.JSON_HEADERS)
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

    static generateEventTypeDiffComplete(primaryProfileId, secondaryProfileId, eventType) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate/diff/complete', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static generateEventTypeDiffRange(primaryProfileId, secondaryProfileId, eventType, timeRange) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            timeRange: timeRange,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/generate/diff/range', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static list(profileId) {
        const content = {
            profileId: profileId,
        };

        return axios.post(GlobalVars.url + '/flamegraph/all', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static exportById(profileId, flamegraphId) {
        const content = {
            profileId: profileId,
            flamegraphId: flamegraphId,
        };

        return axios.post(GlobalVars.url + '/flamegraph/export/id', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static exportByEventType(profileId, eventType) {
        const content = {
            profileId: profileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/export/event', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static getById(profileId, flamegraphId) {
        const content = {
            profileId: profileId,
            flamegraphId: flamegraphId,
        };

        return axios.post(GlobalVars.url + '/flamegraph/content/id', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static getByEventType(profileId, eventType) {
        const content = {
            profileId: profileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/content/event', content, HttpUtils.JSON_HEADERS)
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
