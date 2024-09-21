

package io.jafar.parser;

import io.jafar.parser.internal_api.metadata.MetadataClass;

import java.util.function.Predicate;

@FunctionalInterface
public interface TypeFilter extends Predicate<MetadataClass> {
}
