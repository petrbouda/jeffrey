export default class GlobalVars {
    static url = import.meta.env.DEV
        ? 'http://localhost:8585'
        : ''

    static SAP_EVENT_LINK = 'https://sap.github.io/SapMachine/jfrevents/23.html'
}
