# Plugin Profiler

A RuneLite plugin that shows the CPU cost of every other installed plugin — live, in a side panel, with zero setup.

**"My client stutters. Which of my 40 plugins is doing it?"** This plugin answers that question without bisection or attaching an external profiler.

## How it works

A sampling profiler built on standard Java APIs (`Thread.getStackTrace()`), sampling the client thread and the Swing EDT at ~100 Hz. Each sampled stack is attributed to the plugin that owns its frames:

- Core plugins are identified by their package (`net.runelite.client.plugins.<name>`).
- Hub plugins are identified by their own classloader — unambiguous ownership, including shaded libraries.
- Samples with no plugin-owned frame are bucketed as "RuneLite / game engine".
- The profiler's own overhead is shown in its own bucket, always visible.

CPU only — GPU-side cost (e.g. 117HD shaders) is not visible to CPU sampling.

## Features

- Sortable per-plugin cost list with share bars and sparklines
- Rolling windows: last 5 s / last 1 min / session
- Spike log — catches the plugin that stutters *occasionally*
- Per-plugin hot frames and per-thread split
- Optional notification when a plugin sustains high cost
- Export: folded stacks ([speedscope](https://speedscope.app)-compatible) + JSON summary to `.runelite/plugin-profiler/`

## License

BSD 2-Clause. Built from [runelite/example-plugin](https://github.com/runelite/example-plugin).
