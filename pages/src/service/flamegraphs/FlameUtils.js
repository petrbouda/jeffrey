export default class FlameUtils {

    static toastExported(toast) {
        return () => {
            toast.add({severity: 'success', summary: 'Successful', detail: 'Flamegraph exported', life: 3000});
        }
    }

    static canvasResize(flamegraph, minusPadding = 0) {
        let w = document.getElementById("flamegraphCanvas")
            .parentElement.clientWidth

        if (flamegraph != null) {
            flamegraph.resizeCanvas(w - minusPadding)
        }
    }

    static registerAdjustableScrollableComponent(flamegraph, scrollableComponent) {
        if (scrollableComponent != null) {
            let el = document.getElementsByClassName(scrollableComponent)[0]
            el.addEventListener("scroll", () => {
                flamegraph.updateScrollPositionY(el.scrollTop)
                flamegraph.removeHighlight()
            });
        }
    }

    static contextMenuItems(searchInTimeseries, searchInFlamegraph, resetZoom) {
        let contextMenuItems = []

        if (searchInTimeseries != null) {
            contextMenuItems.push({
                label: 'Search in Timeseries',
                icon: 'pi pi-chart-bar',
                command: searchInTimeseries
            })
        }

        if (searchInFlamegraph != null) {
            contextMenuItems.push({
                label: 'Search in Flamegraph',
                icon: 'pi pi-align-center',
                command: searchInFlamegraph
            })
        }

        if (resetZoom != null) {
            contextMenuItems.push({
                label: 'Zoom out Flamegraph',
                icon: 'pi pi-search-minus',
                command: resetZoom
            })
        }

        contextMenuItems.push(
            {
                separator: true
            }, {
                label: 'Close',
                icon: 'pi pi-times'
            }
        )

        return contextMenuItems
    }
}
