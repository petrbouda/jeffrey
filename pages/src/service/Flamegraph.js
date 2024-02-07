export default class Flamegraph {

    static FRAME_HEIGHT = 20;
    static FRAME_HEIGHT_2 = 21;

    depth = null;

    // set up by draw function
    canvasHeight = null;
    canvasWidth = null;
    currentRoot = null;
    currentRootLevel = null;
    pxPerSample = null

    reversed = false;

    visibleFrames = [];

    constructor(data, canvas, hl, status) {
        this.hl = hl;
        this.status = status;
        this.depth = data.depth;
        this.levels = data.levels;
        this.currentRoot = this.levels[0][0];
        this.currentRootLevel = 0;
        this.currentPattern = null;
        this.canvas = canvas;
        this.context = canvas.getContext('2d');
        this.visibleFrames = Flamegraph.initializeLevels(this.depth);

        // const height = Math.min(data.depth * Flamegraph.FRAME_HEIGHT_2, 32767)
        this.resizeCanvas(canvas.offsetWidth, canvas.offsetHeight);

        this.canvas.onmousemove = this.#onMouseMoveEvent();
        this.canvas.onmouseout = this.#onMouseOut();
        this.canvas.ondblclick = this.#onDoubleClick();
    }

    #onMouseMoveEvent() {
        return () => {
            const level = Math.floor((this.reversed ? event.offsetY : this.canvasHeight - event.offsetY) / Flamegraph.FRAME_HEIGHT_2);

            if (level >= 0 && level < this.levels.length) {
                let frame = this.#lookupFrame(level);

                if (frame) {
                    if (frame !== this.currentRoot) {
                        getSelection().removeAllRanges();
                    }

                    this.hl.style.left = Math.max(frame.left - this.currentRoot.left, 0) * this.pxPerSample + this.canvas.offsetLeft + 'px';
                    this.hl.style.width = Math.min(frame.width, this.currentRoot.width) * this.pxPerSample  + 'px';
                    this.hl.style.top = (this.reversed ? level * Flamegraph.FRAME_HEIGHT_2 : this.canvasHeight - (level + 1) * Flamegraph.FRAME_HEIGHT_2) + this.canvas.offsetTop + 'px';
                    this.hl.firstChild.textContent = frame.title;
                    this.hl.style.display = 'block';

                    this.canvas.title = frame.title + '\n(' + Flamegraph.#samples(frame.width) + frame.details + ', ' + Flamegraph.#pct(frame.width, this.levels[0][0].width) + '%)';
                    this.canvas.style.cursor = 'pointer';
                    this.canvas.onclick = () => {
                        if (frame !== this.currentRoot) {
                            this.#draw(frame, level, this.currentPattern);
                            this.canvas.onmousemove();
                        }
                    };
                    this.status.textContent = this.canvas.title;
                    this.status.style.display = 'inline-block';
                    this.status.style.left = document.getElementById('layout-main').getBoundingClientRect().left + 'px';
                    this.status.style.bottom = this.status.style.bottom + 10 + 'px';
                    return;
                }

                this.canvas.onmouseout();
            }
        };
    }

    #onMouseOut() {
        return () => {
            this.hl.style.display = 'none';
            this.status.style.display = 'none';
            this.canvas.title = '';
            this.canvas.style.cursor = '';
            this.canvas.onclick = '';
        };
    };

    #onDoubleClick() {
        return () => {
            getSelection().selectAllChildren(this.hl);
        };
    };

    static #samples(n) {
        return n === 1 ? '1 sample' : n.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',') + ' samples';
    }

    static #pct(a, b) {
        return a >= b ? '100' : ((100 * a) / b).toFixed(2);
    }

    #lookupFrame(level) {
        let frames = this.visibleFrames[level];
        for (let i = 0; i < frames.length; i++) {
            let visibleFrame = frames[i];

            if (this.context.isPointInPath(Flamegraph.#toPath2D(visibleFrame.rect), event.offsetX, event.offsetY)) {
                return visibleFrame.frame;
            }
        }
    }

    resizeCanvas(width, height) {
        if (height != null) {
            this.canvasHeight = height;
            this.canvas.height = this.canvasHeight * (devicePixelRatio || 1);
        }

        this.canvasWidth = width;
        this.canvas.style.width = this.canvasWidth + 'px';

        this.canvas.width = this.canvasWidth * (devicePixelRatio || 1);
        if (devicePixelRatio) {
            this.context.scale(devicePixelRatio, devicePixelRatio);
        }
        this.context.font = '12px Arial';
        this.drawRoot();
    }

    static initializeLevels(depth) {
        let levels = Array(depth + 1);
        for (let h = 0; h < levels.length; h++) {
            levels[h] = [];
        }
        return levels;
    }

    clearCanvas() {
        this.context.fillStyle = '#ffffff';
        this.context.fillRect(0, 0, this.canvasWidth, this.canvasHeight);
    }

    drawRoot() {
        this.#draw(this.currentRoot, this.currentRootLevel, this.currentPattern);
    }

    reverse() {
        this.reversed = !this.reversed;
        this.#draw(this.currentRoot, this.currentRootLevel, this.currentPattern);
    }

    search(pattern) {
        this.currentPattern = pattern;
        let highlighted = this.#draw(this.currentRoot, this.currentRootLevel, this.currentPattern);
        let highlightedTotal = Flamegraph.#calculateHighlighted(highlighted);
        return Flamegraph.#pct(highlightedTotal, this.currentRoot.width);
    }

    resetSearch() {
        this.currentPattern = null;
        this.#draw(this.currentRoot, this.currentRootLevel, this.currentPattern);
    }

    #draw(root, rootLevel, pattern) {
        this.clearCanvas();
        this.visibleFrames = Flamegraph.initializeLevels(this.depth);

        this.currentRoot = root;
        this.currentRootLevel = rootLevel;

        this.pxPerSample = this.canvasWidth / root.width;

        const xStart = root.left;
        const xEnd = xStart + root.width;
        const highlighted = []

        for (let level = 0; level < this.levels.length; level++) {
            const y = this.reversed ? level * (Flamegraph.FRAME_HEIGHT + 1) : this.canvasHeight - (level + 1) * (Flamegraph.FRAME_HEIGHT_2);
            const frames = this.levels[level];

            for (let i = 0; i < frames.length; i++) {
                let frame = frames[i];
                if (Flamegraph.#frame_not_overflow(frame, xStart, xEnd)) {
                    let isHighlighted = Flamegraph.#isMethodHighlighted(highlighted, frame, pattern);
                    let isUnderRoot = level < rootLevel;

                    const rectangle = this.#createRectangle(this.pxPerSample, frame, y, xStart);
                    this.visibleFrames[level].push({ rect: rectangle, frame: frame });
                    this.#drawFrame(this.pxPerSample, frame, y, xStart, rectangle, isHighlighted, isUnderRoot);
                }
            }
        }

        return highlighted
    }

    static #frame_not_overflow(frame, xStart, xEnd) {
        return frame.left < xEnd && frame.left + frame.width > xStart;
    }

    static #highlight(highlighted, frame) {
        return highlighted[frame.left] >= frame.width || (highlighted[frame.left] = frame.width)
    }

    static #isMethodHighlighted(highlighted, frame, pattern) {
        return pattern && frame.title.match(pattern) && Flamegraph.#highlight(highlighted, frame);
    }

    static #calculateHighlighted(highlighted) {
        let total = 0;
        let left = 0;
        Object.keys(highlighted)
            .sort(function(a, b) {
                return a - b;
            })
            .forEach(function(x) {
                if (+x >= left) {
                    total += highlighted[x];
                    left = +x + highlighted[x];
                }
            });
        return total;
    }

    static #toPath2D(rect) {
        const path = new Path2D()
        path.rect(rect.x, rect.y, rect.width, rect.height)
        return path;
    }

    #createRectangle(pxPerSample, frame, y, xStart) {
        const x = (frame.left - xStart) * pxPerSample;
        const width = frame.width * pxPerSample;
        return { x: x, y: y, width: width, height: Flamegraph.FRAME_HEIGHT };
    }

    #drawFrame(pxPerSample, frame, y, xStart, rect, isHighlighted, isUnderRoot) {
        const path = Flamegraph.#toPath2D(rect)

        this.context.fillStyle = isHighlighted ? '#ee00ee' : frame.color;
        this.context.fill(path);

        // Do we want to fill the text, or the frame is too small and leave it empty
        if (frame.width * pxPerSample >= 21) {
            const chars = Math.floor((frame.width * pxPerSample) / 7);
            const title = frame.title.length <= chars ? frame.title : frame.title.substring(0, chars - 2) + '..';
            this.context.fillStyle = '#000000';
            this.context.fillText(title, Math.max(frame.left - xStart, 0) * pxPerSample + 3, y + 14, frame.width * pxPerSample - 6);
        }

        if (isUnderRoot) {
            this.context.fillStyle = 'rgba(255, 255, 255, 0.5)';
            this.context.fill(path);
        }
    }
}