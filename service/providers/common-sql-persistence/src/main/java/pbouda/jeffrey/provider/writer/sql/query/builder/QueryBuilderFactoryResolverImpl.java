package pbouda.jeffrey.provider.writer.sql.query.builder;

import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.provider.writer.sql.query.ComplexQueries;
import pbouda.jeffrey.provider.writer.sql.query.SQLFormatter;

import java.util.List;

public class QueryBuilderFactoryResolverImpl implements QueryBuilderFactoryResolver {

    private final SQLFormatter sqlFormatter;
    private final ComplexQueries defaultComplexQueries;
    private final ComplexQueries nativeComplexQueries;

    public QueryBuilderFactoryResolverImpl(
            SQLFormatter sqlFormatter,
            ComplexQueries defaultComplexQueries,
            ComplexQueries nativeComplexQueries) {

        this.sqlFormatter = sqlFormatter;
        this.defaultComplexQueries = defaultComplexQueries;
        this.nativeComplexQueries = nativeComplexQueries;
    }

    @Override
    public QueryBuilderFactory resolve(String profileId, List<Type> eventTypes) {
        if (eventTypes.size() == 1 && eventTypes.getFirst() == Type.NATIVE_LEAK) {
            return new NativeLeakQueryBuilderFactory(sqlFormatter, nativeComplexQueries, profileId);
        } else {
            return new DefaultQueryBuilderFactory(sqlFormatter, defaultComplexQueries, profileId);
        }
    }
}
