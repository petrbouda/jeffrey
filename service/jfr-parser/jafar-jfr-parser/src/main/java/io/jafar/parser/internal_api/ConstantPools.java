

package io.jafar.parser.internal_api;

import io.jafar.parser.internal_api.ConstantPool;

import java.util.stream.Stream;

public interface ConstantPools {
    io.jafar.parser.internal_api.ConstantPool getConstantPool(long typeId);
    boolean hasConstantPool(long typeId);
    boolean isReady();
    void setReady();

    Stream<? extends ConstantPool> pools();
}
