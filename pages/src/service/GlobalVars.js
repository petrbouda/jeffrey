import { ref } from 'vue';

export default class GlobalVars {
    static url = 'http://localhost:8080';

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
            },
            {
                index: 3,
                label: 'Live Objects',
                code: 'profiler.LiveObject'
            }
        ];
    }
}
