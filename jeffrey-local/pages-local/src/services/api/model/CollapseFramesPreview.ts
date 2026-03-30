export default interface CollapseFramesPreview {
  matchingFrames: number
  affectedStacktraces: number
  samples: { className: string; methodName: string }[]
}
