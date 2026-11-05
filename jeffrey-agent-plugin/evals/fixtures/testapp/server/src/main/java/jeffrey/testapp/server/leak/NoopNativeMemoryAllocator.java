package jeffrey.testapp.server.leak;

public class NoopNativeMemoryAllocator implements NativeMemoryAllocator {

    @Override
    public Allocation allocate() {
        return new HeapAllocation();
    }
}
