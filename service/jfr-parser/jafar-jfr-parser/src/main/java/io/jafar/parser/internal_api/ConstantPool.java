

package io.jafar.parser.internal_api;

import io.jafar.parser.internal_api.metadata.MetadataClass;

public interface ConstantPool {
    Object get(long id);
    int size();

    boolean isEmpty();
    MetadataClass getType();
}
