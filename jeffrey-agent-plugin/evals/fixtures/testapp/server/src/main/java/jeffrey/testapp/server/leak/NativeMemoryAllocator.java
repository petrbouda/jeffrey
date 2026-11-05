package jeffrey.testapp.server.leak;

import java.io.Closeable;

public interface NativeMemoryAllocator {

    Allocation allocate();

    interface Allocation extends Closeable {
    }

    class HeapAllocation implements Allocation {
        @Override
        public void close() {
        }
    }

    class NativeAllocation implements Allocation {

        private final long address;
        private final boolean leaked;

        public NativeAllocation() {
            this.leaked = true;
            this.address = -1;
        }

        public NativeAllocation(long bytes, boolean leaked) {
            this.address = UnsafeHolder.INSTANCE.allocateMemory(bytes);
            this.leaked = leaked;
        }

        @Override
        public void close() {
            if (leaked) {
                UnsafeHolder.INSTANCE.freeMemory(address);
            }
        }
    }
}
