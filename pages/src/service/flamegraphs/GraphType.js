export default class GraphType {

    static PRIMARY = "PRIMARY"
    static DIFFERENTIAL = "DIFFERENTIAL"

    static isDifferential(type) {
        return type === this.DIFFERENTIAL
    }
}
