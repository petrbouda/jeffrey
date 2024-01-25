import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';

export default class RecordingService {
    list() {
        return axios.get(GlobalVars.url + '/recordings', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }

    delete(filename) {
        const content = {
            filenames: [filename]
        };

        return axios.post(GlobalVars.url + '/recordings/delete', content, HttpUtils.JSON_CONTENT_TYPE_HEADER)
            .then(HttpUtils.RETURN_DATA);
    }
}
