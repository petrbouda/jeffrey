export default class HttpUtils {
    static JSON_HEADERS = {
        headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json'
        }
    };

    static JSON_ACCEPT_HEADER = {
        headers: {
            Accept: 'application/json'
        }
    };

    static JSON_CONTENT_TYPE_HEADER = {
        headers: {
            'Content-Type': 'application/json'
        }
    };

    static RETURN_DATA = function (response) {
        return response.data;
    };
}
