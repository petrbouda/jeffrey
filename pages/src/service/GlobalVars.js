import { ref } from 'vue';

export default class GlobalVars {
    static url = 'http://localhost:8585';

    static jfrTypes() {
        return [
            {
                index: 0,
                label: 'Execution Samples (CPU)',
                code: 'jdk.ExecutionSample'
            },
            {
                index: 1,
                label: 'Allocations',
                code: 'jdk.ObjectAllocationInNewTLAB'
            },
            {
                index: 2,
                label: 'Locks',
                code: 'jdk.ThreadPark'
            }
        ];
    }

    static eventTypeByCode(eventCode) {
        return GlobalVars.jfrTypes()
            .find(el => el.code === eventCode)
    }
}
