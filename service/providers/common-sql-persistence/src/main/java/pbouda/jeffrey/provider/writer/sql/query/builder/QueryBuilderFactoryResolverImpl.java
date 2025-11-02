package pbouda.jeffrey.provider.writer.sql.query.builder;

import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.writer.sql.query.SQLFormatter;

import java.util.List;

public class QueryBuilderFactoryResolverImpl implements QueryBuilderFactoryResolver {

    private final SQLFormatter sqlFormatter;

    public QueryBuilderFactoryResolverImpl(SQLFormatter sqlFormatter) {
        this.sqlFormatter = sqlFormatter;
    }

    @Override
    public QueryBuilderFactory resolve(String profileId, List<Type> eventTypes) {
        if (eventTypes.size() == 1 && eventTypes.getFirst() == Type.NATIVE_LEAK) {
            return new NativeLeakQueryBuilderFactory(sqlFormatter, profileId);
        } else {
            return new DefaultQueryBuilderFactory(sqlFormatter, profileId);
        }
    }
}
