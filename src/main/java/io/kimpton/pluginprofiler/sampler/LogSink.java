package io.kimpton.pluginprofiler.sampler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Milestone-2 sink: tallies samples and logs a per-plugin table every 5 s.
 * Runs entirely on the sampler thread — no synchronisation needed.
 * Replaced by ProfileStore in milestone 3.
 */
@Slf4j
public class LogSink implements SamplerService.SampleSink
{
	private static final long LOG_INTERVAL_MS = 5_000L;
	private static final int MAX_ROWS = 15;

	private final Map<String, long[]> counts = new HashMap<>();
	private final Map<String, Map<String, Integer>> topFrames = new HashMap<>();
	private long windowStartMillis = System.currentTimeMillis();
	private long overheadStartNanos;
	private long total;

	@Override
	public void accept(Sample sample)
	{
		long[] perThread = counts.computeIfAbsent(sample.getOwner(), k -> new long[ThreadKind.values().length]);
		perThread[sample.getThread().ordinal()]++;
		total++;

		if (sample.getTopFrame() != null)
		{
			topFrames.computeIfAbsent(sample.getOwner(), k -> new HashMap<>())
				.merge(sample.getTopFrame(), 1, Integer::sum);
		}
	}

	@Override
	public void tick(long cumulativeSamplingNanos)
	{
		long now = System.currentTimeMillis();
		long elapsed = now - windowStartMillis;
		if (elapsed < LOG_INTERVAL_MS)
		{
			return;
		}

		if (total > 0)
		{
			log.debug("{}", buildTable(elapsed, cumulativeSamplingNanos - overheadStartNanos));
		}

		counts.clear();
		topFrames.clear();
		total = 0;
		windowStartMillis = now;
		overheadStartNanos = cumulativeSamplingNanos;
	}

	private String buildTable(long elapsedMillis, long overheadNanos)
	{
		List<Map.Entry<String, long[]>> rows = new ArrayList<>(counts.entrySet());
		rows.sort((a, b) -> Long.compare(sum(b.getValue()), sum(a.getValue())));

		StringBuilder sb = new StringBuilder();
		double overheadMs = overheadNanos / 1_000_000.0;
		sb.append(String.format("profile %.1fs, %d samples, self-overhead %.1fms (%.2f%%)",
			elapsedMillis / 1000.0, total, overheadMs, 100.0 * overheadMs / elapsedMillis));

		int shown = 0;
		for (Map.Entry<String, long[]> row : rows)
		{
			if (shown++ >= MAX_ROWS)
			{
				sb.append("\n  ... ").append(rows.size() - MAX_ROWS).append(" more");
				break;
			}

			long client = row.getValue()[ThreadKind.CLIENT.ordinal()];
			long edt = row.getValue()[ThreadKind.EDT.ordinal()];
			sb.append(String.format("%n  %5.1f%%  %-28s client=%d edt=%d",
				100.0 * sum(row.getValue()) / total, row.getKey(), client, edt));

			String hottest = hottestFrame(row.getKey());
			if (hottest != null)
			{
				sb.append("  [").append(hottest).append(']');
			}
		}
		return sb.toString();
	}

	private String hottestFrame(String owner)
	{
		Map<String, Integer> frames = topFrames.get(owner);
		if (frames == null || frames.isEmpty())
		{
			return null;
		}
		return frames.entrySet().stream()
			.max(Map.Entry.comparingByValue())
			.map(e -> e.getKey() + " x" + e.getValue())
			.orElse(null);
	}

	private static long sum(long[] perThread)
	{
		long s = 0;
		for (long v : perThread)
		{
			s += v;
		}
		return s;
	}
}
