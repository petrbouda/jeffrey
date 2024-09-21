

package io.jafar.parser.internal_api;

import io.jafar.parser.internal_api.metadata.MetadataClass;

public interface MetadataLookup {

    String getString(int idx);

    MetadataClass getClass(long id);
}
