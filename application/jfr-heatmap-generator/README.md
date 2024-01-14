# Generator of data files for D3 Heatmap from JFR

- https://github.com/spiermar/d3-heatmap2

Expected output:
- **columns**: seconds
- **maxvalue**: max samples in the dataset
- **rows**: milliseconds ranges
- **values**: a number of samples for the given second and a millis range (bucket) 

```json
{
  "columns": [
    0,
    1,
    2
  ],
  "maxvalue": 10,
  "rows": [
    980.0,
    960.0,
    940.0,
    ...
    0.0
  ],
  "values": [
    [
      5,
      3,
      4,
      ...
      7
    ],
    [
      1,
      3,
      3,
      ...
      10
    ],
    [
      0,
      3,
      6,
      ...
      7
    ]
  ]
}
```
