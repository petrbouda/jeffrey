import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class FlamegraphService {

    static saveEventTypeRange(primaryProfileId, flamegraphName,  eventType, timeRange) {
        const content = {
            primaryProfileId: primaryProfileId,
            flamegraphName: flamegraphName,
            eventType: eventType,
            timeRange: timeRange
        };

        return axios.post(GlobalVars.url + '/flamegraph/save/range', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

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

    static saveEventTypeDiffRange(primaryProfileId, secondaryProfileId, flamegraphName, eventType, timeRange) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            flamegraphName: flamegraphName,
            timeRange: timeRange,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/save/diff/range', content, HttpUtils.JSON_HEADERS)
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
            eventType: eventType,
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

    static exportEventTypeRange(profileId, eventType, timeRange) {
        const content = {
            primaryProfileId: profileId,
            eventType: eventType,
            timeRange: timeRange
        };

        return axios.post(GlobalVars.url + '/flamegraph/export/range', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static exportEventTypeComplete(profileId, eventType) {
        const content = {
            primaryProfileId: profileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/export/complete', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static exportEventTypeDiffComplete(primaryProfileId, secondaryProfileId, eventType) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/flamegraph/export/diff/complete', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static exportEventTypeDiffRange(primaryProfileId, secondaryProfileId, eventType, timeRange) {
        const content = {
            primaryProfileId: primaryProfileId,
            secondaryProfileId: secondaryProfileId,
            timeRange: timeRange,
            eventType: eventType,
        };

        return axios.post(GlobalVars.url + '/flamegraph/export/diff/range', content, HttpUtils.JSON_HEADERS)
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
