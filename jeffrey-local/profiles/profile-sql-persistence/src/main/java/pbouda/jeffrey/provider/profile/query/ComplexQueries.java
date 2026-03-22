package pbouda.jeffrey.provider.profile.query;

public interface ComplexQueries {

    interface Flamegraph {

        String simple();

        String byWeight();

        String byThread();

        String byThreadAndWeight();
    }

    interface Timeseries {

        String simple(boolean useWeight, boolean useSpecifiedThread);

        String simpleSearch(boolean useWeight, boolean useSpecifiedThread);

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
