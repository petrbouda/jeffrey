import GlobalVars from '@/service/GlobalVars';
import SelectedProfileService from '@/service/SelectedProfileService';

export default class GenerateFlamegraphService {
    static generate(eventTypes) {
        const arrayOfCodes = eventTypes.map(function (value) {
            return value.code;
        });

        const content = {
            profile: SelectedProfileService.get(),
            types: arrayOfCodes
        };

        const requestOptions = {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify(content)
        };

        return fetch(GlobalVars.url + '/flamegraph/generate', requestOptions)
            .then((resp) => resp.json());
    }

    static list() {
        const requestOptions = {
            method: 'GET',
            headers: { Accept: 'application/json' }
        };

        return fetch(GlobalVars.url + '/flamegraph', requestOptions)
            .then((resp) => resp.json());
    }

    static getSingle() {
        const requestOptions = {
            method: 'GET'
        };

        return fetch(GlobalVars.url + '/heatmap/basics', requestOptions)
            .then((resp) => resp.json());
    }
}
