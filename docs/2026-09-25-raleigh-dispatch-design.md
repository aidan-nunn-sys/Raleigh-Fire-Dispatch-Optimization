# Raleigh Fire Dispatch: PS03 +X Design

## 1. Goal

Apply the PS03 linear-assignment solver (simulated annealing) to a real
problem: assigning Raleigh Fire Department stations to real incidents from City of
Raleigh open data. The +X should show how well the algorithm works against an exact
optimum and against real-world baselines. It is packaged as a small, usable desktop tool.

### Non-goals
- Not a dispatch tool. There are no claims about how Raleigh *should* dispatch.
- No map tiles, road routing, background polling, accounts, or web deployment.
- No EMS incidents. Raleigh withholds NFIRS types 300–399 and 661 for privacy.

## 2. Hard constraints

1. `Environment.java` and `ConfigurationSolver.java` are **not modified**. The +X
   runs the student's SA by constructing `new Environment(int[][])` and calling
   `updateConfiguration()`.
2. `PS03_TestCase` must still pass at 100% after every change.
3. **Java 1.8 compliance** (`.settings/org.eclipse.jdt.core.prefs`): no `java.net.http`,
   no `var`, no records. HTTP uses `HttpURLConnection`.
4. **No new jars.** JSON is parsed by a small in-project reader.
5. Everything the graders need runs **offline** from committed files.

## 3. Data sources (verified 2026-09-25, station coverage checked 2026-09-26)

| Data | Endpoint | Notes |
|---|---|---|
| Historical incidents | `https://services.arcgis.com/v400IkDOw1ad7Yad/arcgis/rest/services/Fire_Incidents_Public/FeatureServer/0` | 2007–present, monthly release. `station` is an **int**. Type fields: `incident_type_description` (pre-2026), `incident_group_name` / `incident_type_name` (2026+) |
| Past-month incidents | `https://services.arcgis.com/v400IkDOw1ad7Yad/arcgis/rest/services/Fire_Incidents_Past_Month/FeatureServer/0` | Rolling 30 days, updated daily. `station_name` is a **string** `"Station 14"`; 2026+ type fields only |
| Fire stations | `https://services1.arcgis.com/a7CWfuGP5ZnLYE7I/arcgis/rest/services/FireStations/FeatureServer/0` | Filter `AGENCY='RF'` → 28 RFD stations, `STATIONID` `S01`…`S29` (there is no 13) |
| City outline | `https://services.arcgis.com/v400IkDOw1ad7Yad/arcgis/rest/services/CorporateLimitsDissolved/FeatureServer/1` | City of Raleigh's dissolved corporate limits: one feature whose rings are islands and holes (the Wake `Corporate Limits` layer is ~1,965 separate parcel polygons, which drew internal borders and spikes) |

All queries use `f=json&outSR=4326`, request explicit `outFields` (never `address`),
and page with `resultOffset`/`resultRecordCount=2000`.

**Station coverage gap (verified 2026-09-26):** the historical feed's `station` is null for
every incident from **May 2021 through December 2025** (2018: 14,770 of 15,137 filled; 2019: 16,120 of
16,524; 2020: 14,726 of 14,993; 2021: January–April only; 2022–2025: none). It returns in 2026. The
past-month feed has `station_name` on 1,307 of 1,351 current records.

**Station ID normalization:** int `14` → `S14`; string `"Station 14"` → `S14`; null or
a station not among the 28 → `null` (counted as "unmatched" in the Actual baseline).

### Data courtesy rules
- `address` is never requested, stored, or displayed.
- Incident coordinates are rounded to 3 decimals (~100 m) when they are ingested.
- The live feed is fetched only on user click. Results are cached for `CACHE_TTL_MINUTES` (default 60).
- HTTP requests are sequential and carry `User-Agent: CSC411-PS03-DispatchDemo (educational)`, with 10 s connect/read timeouts.
- The UI footer and README credit City of Raleigh / Wake County Open Data and state
  "Teaching model, not a dispatch tool."
- Licenses: The Wake County stations layer is CC BY 4.0. Raleigh incidents and city limits are under the Open Raleigh
  Data Policy (disclaimer-style; the city may request that use stop). Check with the professor
  before publishing to GitHub.

## 4. Package layout

All new code lives under `src/edu/ncsu/csc411/ps03/dispatch/` and
`test/edu/ncsu/csc411/dispatch/`.

```
dispatch/
  data/
    JsonReader.java            minimal recursive-descent JSON → Map/List/String/Double/Boolean/null
    ArcGisClient.java          HttpURLConnection GET + paging; returns List<Map> of features
    Station.java               id, label, lat, lon (immutable)
    Incident.java              id, dispatchMillis, arriveMillis, group, lat, lon, actualStationId
    Scenario.java              name, stations, incidents (≤ 28), warnings
    ScenarioIO.java            read/write stations.csv, scenario CSVs, outline CSV
    RaleighIncidentClient.java fetch past-month feed → List<Incident>; cache; busiest windows
    FetchStaticData.java       main(): regenerates every committed file in inputs/raleigh/
  model/
    Geo.java                   haversine miles
    TravelModel.java           seconds = TURNOUT_SECONDS + SECONDS_PER_MILE × miles
    DispatchMatrix.java        Scenario + TravelModel → int[28][28] values[task][worker]
    AssignmentMethod.java      interface: int[] solve(int[][] values)
    HungarianMethod.java       exact optimum, O(n³)
    GreedyNearestMethod.java   time-ordered nearest free station
    ActualDispatch.java        per-incident actual station (may repeat, may be unmatched)
    DispatchMetrics.java       total / avg / worst minutes, gap to optimal
  ui/
    DispatchVisualizer.java    JFrame + main()
    MapPanel.java
    ControlPanel.java
    StatsPanel.java
    ConvergenceChart.java
  benchmark/
    DispatchBenchmark.java     headless main(): writes outputs/benchmark_*.csv
```

## 5. Committed data files (`inputs/raleigh/`)

| File | Format |
|---|---|
| `stations.csv` | `station_id,label,lat,lon`: 28 rows |
| `raleigh_outline.csv` | `ring,lat,lon`: rings from `maxAllowableOffset=0.0005`, keeping rings whose bounding box spans ≥ 0.005° in either axis; target ≤ 3,000 points |
| `travel_model.properties` | `TURNOUT_SECONDS`, `SECONDS_PER_MILE`, `SAMPLE_SIZE`, `R_SQUARED`, `FIT_FROM`, `FIT_TO`, `CHECK_SOURCE`, `CHECK_SAMPLE_SIZE`, `CHECK_MAE_SECONDS`, `CHECK_BIAS_SECONDS` |
| `scenarios/florence_peak.csv` | incident CSV (below) |
| `scenarios/helene_peak.csv` | incident CSV |
| `scenarios/typical_weekday.csv` | incident CSV |
| `scenarios/florence_day.csv` | incident CSV, all of 2018-09-14 (no 28 cap), used only by the scaling benchmark |
| `cache/latest.csv` | live cache, git-ignored (`/inputs/raleigh/cache/` added to `.gitignore`) |

Incident CSV:
```
# name=Florence peak
# window=2018-09-14T18:00-04:00/2018-09-14T20:00-04:00
# source=Fire_Incidents_Public fetched 2026-09-25
incident_id,dispatch_iso,arrive_iso,group,lat,lon,actual_station
18-012345,2018-09-14T18:03:11-04:00,2018-09-14T18:08:40-04:00,Fire,35.781,-78.642,S01
```
`arrive_iso` and `actual_station` may be empty.

### Scenario selection (in `FetchStaticData`)
- **Florence peak:** the busiest 2-hour window across 2018-09-13 … 2018-09-17.
- **Helene peak:** the busiest 2-hour window across 2024-09-26 … 2024-09-28. Its incidents have no
  published station, so Actual is shown as "not published for this period" instead of a number.
- **Typical weekday:** 2019-03-12 17:00–19:00 local (a Tuesday, same as the originally planned 2024-03-12,
  but inside the years that have station data).
- The busiest window comes from a sliding window over dispatch times (1-minute step).
- If a window has more than 28 incidents, keep the first 28 by dispatch time and record a warning.

### Travel model calibration (in `FetchStaticData`)
- Sample: historical incidents dispatched 2019-01-01 … 2019-12-31 (local) whose station
  normalizes to one of the 28 and that have both dispatch and arrive times (12,585 have both
  a station and an arrive time). 2019 is the most recent full year with station data.
- Keep `60 s ≤ arrive − dispatch ≤ 1800 s`.
- x = haversine miles from the responding station, y = seconds. Fit by ordinary least squares.
- Write the intercept (`TURNOUT_SECONDS`), slope (`SECONDS_PER_MILE`), n, and R².
- **Currency check:** apply the 2019 model to the past-month feed (same filters, `station_name`)
  and write its sample size, mean absolute error, and mean bias (actual − predicted seconds).
  The report uses this to say whether 2019 response times still describe today.
- The report states the result honestly: straight-line distance is only a proxy.

## 6. Model

### Matrix
- N = 28 (stations). Workers = stations in `stations.csv` order. Tasks = scenario
  incidents in dispatch order (k ≤ 28), then 28 − k dummy rows.
- Real row i, worker j: `values[i][j] = max(0, MAX_SECONDS − est(j, i))`, `MAX_SECONDS = 3600`,
  where `est` = `TravelModel` seconds, rounded to an int.
- Dummy rows: `values[i][j] = 0` for every j. They are constant, so they never affect which station takes a real call.
- Configuration format matches PS03: `config[worker] = task`, scored by `Environment.calcScore`.

### Methods
- **SA (student's):** `Environment env = new Environment(values)`. One `updateConfiguration()` per
  tick; current = `getCurrentConfiguration()`, best = `getBestConfiguration()`.
- **Hungarian:** minimizes `cost = MAX_SECONDS − value` with the potentials-based O(n³)
  algorithm. Returns `int[] config[worker] = task`.
- **Greedy nearest:** for each real incident in dispatch order, pick the lowest-`est` unused station.
  Leftover stations are paired with dummy rows in index order.
- **Actual:** each incident's `actual_station`. Stations can repeat. Unmatched incidents
  are excluded from Actual's totals, and the unmatched count is shown next to it. When every
  incident is unmatched (any window from May 2021 through 2025), Actual is "not available"
  rather than 0 minutes.

### Metrics (real incidents only)
- Total estimated minutes, average, worst single incident.
- Gap to optimal = `(total − optimalTotal) / optimalTotal`.
- For SA: the iteration where its best was first reached.

### Caveats (shown in the UI info text and in the report)
- A window treats its incidents as simultaneous.
- Real dispatch accounts for unit availability, apparatus type, coverage, and mutual aid, none of which appear
  in the data. So "Actual" gives context and is not a score.

## 7. UI

`DispatchVisualizer.main`: one non-resizable window of about 1100×700 that reuses `ColorPalette`.
Settings come from `config/configDispatch.txt` through the existing `ConfigurationLoader`:
`ITERATIONS=1000`, `DELAY=50`, `WINDOW_MINUTES=120`, `CACHE_TTL_MINUTES=60`.

- **MapPanel (left):**
  - Equirectangular projection with longitude scaled by cos(lat₀), fitted to the outline bounds with a margin.
  - Outline: light fill. Stations: triangles labeled with their number. Incidents: circles colored by group (Fire, Hazardous, Alarm, Service/Other).
  - Current SA assignment: solid lines, colored per station.
  - Overlay checkboxes: Optimal (thin green) and Actual (dashed gray).
  - Hovering an incident shows a tooltip with group, dispatch time, SA station, and estimated minutes.
- **ControlPanel (right, top):**
  - Scenario combo: the 3 snapshots, plus "Latest (live)" after a fetch.
  - `Fetch latest` runs in a `SwingWorker` and shows how old the cache is.
  - Window combo: 5 busiest non-overlapping windows of `WINDOW_MINUTES` in the fetched feed.
  - Play/Pause, Step, Reset (rebuilds the `Environment`), speed slider (1–200 ms), iteration counter / ITERATIONS.
- **StatsPanel (right, middle):**
  - Rows: SA now, SA best, Optimal, Greedy, Actual (with its unmatched count). Columns: total / avg / worst minutes.
  - Gap-to-optimal line. SA rows refresh every tick.
- **ConvergenceChart (bottom):** SA current and best total minutes against iteration, with the optimum as a dashed line.
- **Export CSV:** writes `outputs/dispatch_<scenario>.csv`: a per-incident row
  (`incident_id,group,sa_station,sa_min,opt_station,opt_min,greedy_station,greedy_min,actual_station,actual_min`)
  followed by summary rows.
- **Footer:** data attribution and the disclaimer.
- **Errors:**
  - A network or parse failure shows a status message in the footer and falls back to the cache, then the snapshots.
  - A missing committed file shows an error dialog naming the file and `FetchStaticData`.

## 8. Testing

JUnit 4 (same style as `PS03_TestCase`), no network:

| Test class | Cases |
|---|---|
| `JsonReaderTest` | objects, arrays, nesting, escapes, numbers (negative/exponent), null/booleans, malformed input throws |
| `IncidentClientTest` | parses saved fixtures `test/resources/raleigh_historical_sample.json` and `raleigh_pastmonth_sample.json`; station normalization; missing fields; the `exceededTransferLimit` paging flag |
| `DistanceTest` | Belltower (35.7866, −78.6639) → State Capitol (35.7804, −78.6391) within 1% of the reference haversine value |
| `MatrixBuilderTest` | always 28×28; dummy rows constant; closer station → strictly higher value |
| `HungarianTest` | equals brute-force optimum on 50 seeded random 6×6 matrices; public inputs 01–05 score ≥ the test thresholds |
| `GreedyTest` | valid permutation; hand-built 3-station case picks the nearest free station |
| `ScenarioTest` | CSV round-trip; > 28 incidents trimmed with a warning; no `address` column ever written |

- `PS03_TestCase` still passes at 100%.
- Manual UI checklist before screenshots: play, pause, step, reset, both overlays, tooltip,
  scenario switch, fetch while online, fetch while offline (fallback), export.


## 9. Build order (each stage is submittable)

1. **Core:** `data` (minus the live client), `model`, `FetchStaticData`, committed data files,
   `DispatchBenchmark`, and the tests.
2. **UI:** `ui` package plus `config/configDispatch.txt`.
3. **Live-ish:** `RaleighIncidentClient` fetch/cache/busiest windows plus UI wiring. This is cut first if time runs short.
4. **Docs:** `README_DISPATCH.md`.