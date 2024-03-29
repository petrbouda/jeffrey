import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class EventViewerService {

    static allEventTypes(primaryProfileId) {
        const content = {
            primaryProfileId: primaryProfileId
        };

        return axios.post(GlobalVars.url + '/viewer/all', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static events(primaryProfileId, eventType) {
        const content = {
            primaryProfileId: primaryProfileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/viewer/events', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }

    static eventColumns(primaryProfileId, eventType) {
        const content = {
            primaryProfileId: primaryProfileId,
            eventType: eventType
        };

        return axios.post(GlobalVars.url + '/viewer/events/columns', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
