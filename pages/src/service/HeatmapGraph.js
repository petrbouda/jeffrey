export default class HeatmapGraph {

    chart = null;
    selectStart = null;
    selectEnd = null;
    timeRange;

    constructor(data, selectedFn) {
        this.data = data;
        this.selectedFn = selectedFn;
    }

    render(targetChart) {
        let onClick = (samples, i, j) => {
            this.select(samples, [i, j]);
        }

        let onMouseOver = (samples, i, j) => {
            document.getElementById('details').innerHTML = this.assembleRangeLabelWithSamples(samples, i, j);
            this.hover([i, j]);
        }

        const heatmapNode = document.getElementById(targetChart);

        let width = heatmapNode.offsetWidth;
        let gridSize = width / this.data.columns.length;

        if (gridSize > 10) {
            width = this.data.columns.length * 10;
        } else if (gridSize < 6) {
            width = this.data.columns.length * 6;
        }

        const ticks = Math.floor(width / 50);

        let legendWidth = Math.min(width * 0.8, 400);
        let legendTicks = legendWidth > 100 ? Math.floor(legendWidth / 50) : 2;

        this.chart = d3
            .heatmap()
            .title('')
            .subtitle('')
            .width(width)
            .legendScaleTicks(legendTicks)
            .xAxisScale([this.data.columns[0], this.data.columns[this.data.columns.length - 1]])
            .xAxisScaleTicks(ticks)
            .highlightColor('#936EB5')
            .highlightOpacity('0.4')
            .gridStrokeOpacity(0.0)
            .invertHighlightRows(true)
            .xAxisLabels(this.data.columns)
            .yAxisScale(20)

            .onClick(onClick)
            .onMouseOver(onMouseOver)
            .colorScale(
                d3
                    .scaleLinear()
                    .domain([0, this.data.maxvalue / 2, this.data.maxvalue])
                    .range(['#FFFFFF', '#FF5032', '#E50914'])
            )
            .margin({
                top: 40,
                right: 0,
                bottom: 10,
                left: 0
            })
            .legendElement('#legend')
            .legendHeight(50)
            .legendWidth(300)
            .legendMargin({ top: 5, right: 0, bottom: 30, left: 0 });

        d3.select('#' + targetChart).datum(this.data.values).call(this.chart);
    }

    calculateStartTime(start) {
        return [this.data.columns[start[0]], this.data.rows[start[1]]];
    }

    calculateEndTime(end) {
        let bucketInMillis = this.data.rows[0] - this.data.rows[1];
        let endTimeMillis = this.data.rows[end[1]] + bucketInMillis;
        if (endTimeMillis >= 1000) {
            return [this.data.columns[end[0]] + 1, 0];
        } else {
            return [this.data.columns[end[0]], endTimeMillis];
        }
    }

    select(samples, cell) {
        if (!this.selectStart) {
            this.selectStart = cell;
            this.chart.setHighlight([{ start: this.selectStart, end: this.selectStart }]);
        } else if (!this.selectEnd) {
            this.timeRange = [this.selectStart, cell];

            let startTime = this.calculateStartTime(this.timeRange[0]);
            let endTime = this.calculateEndTime(this.timeRange[1]);

            // Notify the caller that the time was changed
            this.selectedFn(startTime, endTime)

            this.selectStart = null;
            this.selectEnd = null;
            this.chart.setHighlight([]);
        } else {
            this.selectStart = cell;
            this.selectEnd = null;
            this.chart.setHighlight([{ start: this.selectStart, end: this.selectStart }]);
        }

        this.chart.updateHighlight();
    }

    hover(cell) {
        if (this.selectStart && !this.selectEnd) {
            if (cell[0] > this.selectStart[0]) {
                // column is higher
                this.chart.setHighlight([{ start: this.selectStart, end: cell }]);
                this.chart.updateHighlight();
            } else if (cell[0] === this.selectStart[0]) {
                // same column
                if (cell[1] < this.selectStart[1]) {
                    // row is higher or equal
                    this.chart.setHighlight([{ start: this.selectStart, end: cell }]);
                    this.chart.updateHighlight();
                } else {
                    this.chart.setHighlight([{ start: this.selectStart, end: this.selectStart }]);
                    this.chart.updateHighlight();
                }
            } else {
                this.chart.setHighlight([{ start: this.selectStart, end: this.selectStart }]);
                this.chart.updateHighlight();
            }
        }
    }

    assembleRangeLabelWithSamples(samples, i, j) {
        return HeatmapGraph.assembleRangeLabel(this.calculateStartTime([i, j])) + ', samples: ' + samples;
    }

    static assembleRangeLabel(time) {
        return 'seconds: ' + time[0] + ' millis: ' + time[1];
    }

    static generateFlamegraphName(profileName, selectedEventType, start, end) {
        let startTime = this.calculateStartTime(start);
        let endTime = this.calculateEndTime(end);
        return profileName + '-' + selectedEventType.code.toLowerCase() + '-' + startTime[0] + '-' + startTime[1] + '-' + endTime[0] + '-' + endTime[1];
    }
}
