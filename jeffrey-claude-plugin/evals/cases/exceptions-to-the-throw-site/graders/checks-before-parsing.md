---
# The fix is to check the format before parsing, not to catch the exception.
type: regex
pattern: '(?:check|validat|test|match|verif)\w*\b[^.\n]{0,80}\b(?:before|first|prior to)\b[^.\n]{0,60}pars|before (?:calling |the |it reaches )?`?(?:Integer[.#])?parseInt|(?:check|validat|match)\w*[^.\n]{0,60}(?:format|prefix|digits?|numeric|regex)[^.\n]{0,60}(?:before|first)|instead of (?:catching|throwing|relying on)|(?:check|validat|test)\w*\s+(?:it|the (?:code|input|string|format|value))\s+first|without (?:throwing|an exception)'
flags: i
---
