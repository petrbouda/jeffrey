package pbouda.jeffrey.provider.writer.sql.query;

public interface ComplexQueries {

    interface Flamegraph {

        String simple();

        String byWeight();

        String byThread();

        String byThreadAndWeight();
    }

    interface Timeseries {

        String simple(boolean useWeight);

        String filterable(boolean useWeight);

        String frameBased(boolean useWeight);
    }

    interface SubSecond {

        String simple(boolean useWeight);

    }

    Flamegraph flamegraph();

    Timeseries timeseries();

    SubSecond subSecond();
}
