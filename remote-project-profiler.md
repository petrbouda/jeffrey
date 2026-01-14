# Implement the possibility to update Profiler Settings remotely

In case of RemoteWorkspace we would like to be able to update Profiler Settings remotely as well.
It should be available in ProjectDetail in the page ProjectProfilerSettigsView.vue.

You can find another example how the remote communication is handled e.g. from RecordingsList.vue if the project is remote,
then the local Jeffrey instance needs to communicate with the remote Jeffrey instance to get the recordings list.
The same should be applied for updating the Profiler Settings remotely. I would like to be able to update the Profiler Settings
and get the current configuration of the Profiler.
