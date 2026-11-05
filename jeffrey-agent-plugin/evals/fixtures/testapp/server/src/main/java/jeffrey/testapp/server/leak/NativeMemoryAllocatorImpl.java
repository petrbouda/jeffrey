package jeffrey.testapp.server.leak;

public class NativeMemoryAllocatorImpl implements NativeMemoryAllocator {

    private static final int ALLOCATION_SIZE = 128;

    private boolean leakNext = false;

    @Override
    public Allocation allocate() {
        leakNext ^= true;
        return new NativeAllocation(ALLOCATION_SIZE, leakNext);
    }
}
