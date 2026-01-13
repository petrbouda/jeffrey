# Create Flag Dashboard

Create please a visualization for jdk.Flag event-types that shows all the configuration for JVM flags.
jdk.Flag is usually JVM command-line options that control various aspects of the Java Virtual Machine's behavior and performance.
And JFR is able to capture events related to these flags, providing insights into how the JVM is configured and how it operates during runtime.

Could you please create a new page in ProfileDetails in Events section in sidebar menu right below the Event Viewer.

# Flag Dashboard
- try to find a suitable visualization, modern and attractive, compact way to display it
- visualization for the jdk.Flag event-types
- shows all the configuration for JVM flags
- provide some way to filter or search for specific flags
- some flags are duplicated with the same value (use distinct values only)
- some flags can change then use the latest value and show the previous value as well some different way to indicate the change

# Nice To have 
- group flags by categories (e.g., memory management, garbage collection, performance tuning) - is it actually possible?
- explanation and description to the flags (we can download from external source the description and store it locally to classpath/resources and show it with flags)
- create a How it works section that explains it, explains the categories (Product, Ergonomic, ...), and so on (e.g. HeapDump GC Roots Dashboard has How it works section)

Please, use ultrathink and modern design principles for the visualization, use consistent visualization with other pages and styling 
