# Raleigh Fire Dispatch (PS03 +X)

The PS03 simulated-annealing solver (`ConfigurationSolver`) is applied to a real
linear assignment problem: sending Raleigh Fire Department stations to real incidents from
City of Raleigh open data. It is compared against the exact optimum (Hungarian method), a
greedy nearest-free-station rule, and the station Raleigh actually sent. If you would like to know more about
the tehnical aspects, there is a design document in `docs/2026-09-25-raleigh-dispatch-design.md`.

> This is a teaching model, not a dispatch tool. Minutes are straight-line estimates, incidents in a
> window are treated as simultaneous, and real dispatch also weighs unit availability,
> apparatus type, coverage, and mutual aid.

## Background

This started as a class assignment. Problem Set 3 in CSC 411 at
North Carolina State University, which is taught by Professor Gaweda. This assignment initially asked for a search-based solver for the
linear assignment problem. Then this project was developed based on the problem set's open-ended "+X" extension. Which allows students to make the initial assignment "their own". In my opinion, I wanted to make something that actually applied to a real world application. This application takes that solver solution to historical and the latest Raleigh fire data. This repository may have some of the Problem Set 3 starter code, (the PS03 environment,
simulation, utilities, and public tests) which are the instructor's, and are published here with the
instructor's permission. `LICENSE` lists those files and should be used accordingly. 

## AI use

AI tools were used, mainly Claude Code:

I chose the problem, the data sources, architecture, and the design, and I reviewed and ran the results. Claude Code helped shape the design, then wrote most of the +X code from it: data loading, the travel model, the Hungarian and greedy baselines, the UI, the benchmark, and the tests.

The commit history does not mark which commits were AI-assisted, since it is a single initial commit.

## Run it

Everything runs offline from the committed files in `inputs/raleigh/`.

- **Eclipse:** right-click `src/edu/ncsu/csc411/ps03/dispatch/ui/DispatchVisualizer.java` → Run As → Java Application.
- **Terminal:** `bash scripts/run-dispatch.sh`

Settings are in `config/configDispatch.txt`: `ITERATIONS` (SA iterations per run), `DELAY`
(ms per iteration at start), `WINDOW_MINUTES` (live window length), and `CACHE_TTL_MINUTES`.

### The window

- **Map:** city limits, stations (numbered triangles), and incidents colored by group
  (Fire, Hazardous, Alarm, Service/Other). SA's current assignment is drawn as solid lines. Tick
  *Optimal overlay* (thin green) or *Actual overlay* (dashed gray) to compare. Hover an
  incident for its group, dispatch time, and SA's station and minutes.
- **Scenario:** Florence peak (2018-09-14), Helene peak (2024-09-26), and Typical weekday
  (2019-03-12). Helene's actual stations are "not published": Raleigh left that field blank
  from May 2021 through 2025.
- **Fetch latest:** downloads the City's past-month incident feed once, caches it in
  `inputs/raleigh/cache/latest.csv` for `CACHE_TTL_MINUTES`, and adds *Latest (live)* with its
  five busiest non-overlapping windows. Offline, it falls back to the cache, and then to the snapshots.
- **Play / Pause / Step / Reset:** one SA iteration per tick; Reset starts a fresh solver.
- **Stats:** total, average, and worst estimated minutes for SA now, SA best, Optimal, Greedy,
  and Actual, plus SA's gap to the optimum and the iteration where its best was found.
- **Chart:** SA current and best total minutes by iteration; the optimum is dashed.
- **Export CSV:** writes `outputs/dispatch_<scenario>.csv` with each incident's station and
  minutes under every method, followed by a summary table.

## Regenerating the data

`bash scripts/run-dispatch.sh edu.ncsu.csc411.ps03.dispatch.data.FetchStaticData` re-downloads
the stations, the city outline, the scenarios, and the travel-model fit. It needs the network

## Tests

`bash scripts/run-tests.sh` runs every dispatch test headless.

## Data and attribution

This project would not exist without the open data published by the City of Raleigh and Wake
County.

- **Fire incidents:** [City of Raleigh Open Data](https://data.raleighnc.gov/), *Fire Incidents*
  and *Fire Incidents (Past Month)*, under the Open Raleigh data policy. Addresses are never
  requested, and coordinates are rounded to 3 decimals (about 100 m). EMS calls are not
  published by the City and are not included. The travel model is a least-squares fit of 2019
  response times from this data.
- **City limits:** [City of Raleigh Open Data](https://data.raleighnc.gov/), *Corporate Limits
  Dissolved*, under the Open Raleigh data policy. The outline is simplified to about 50 m.
- **Fire stations:** [Wake County Open Data](https://data-wake.opendata.arcgis.com/),
  *FireStations*, under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Filtered to
  Raleigh Fire Department stations (`AGENCY='RF'`) and reduced to station id, label, and location.
- Requests are made only when you click, one at a time, with the User-Agent
  `CSC411-PS03-DispatchDemo (educational)`.

The City of Raleigh and Wake County do not endorse this project and make no warranty about
their data.

## License

The code written for this project is under the MIT License (see `LICENSE`). The CSC 411 starter
code and the City of Raleigh and Wake County data are not; `LICENSE` lists them and their terms.
