package io.kimpton.pluginprofiler.sampler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Samples the client thread and the EDT at a fixed rate on a dedicated daemon
 * thread, attributes each stack, and hands samples to a sink.
 *
 * The sampler thread never touches the client API or Swing; it only calls
 * {@link Thread#getStackTrace()} on the captured thread handles. All sink
 * callbacks run on the sampler thread.
 */
@Slf4j
public class SamplerService
{
	public interface SampleSink
	{
		void accept(Sample sample);

		/**
		 * Called once per sampling tick after all samples for that tick were
		 * delivered. {@code cumulativeSamplingNanos} is the total time this
		 * sampler has spent capturing stacks since start — the profiler's own
		 * overhead.
		 */
		default void tick(long cumulativeSamplingNanos)
		{
		}
	}

	private final StackAttributor attributor;
	private final SampleSink sink;

	@Getter
	@Setter
	private volatile Thread clientThread;
	@Setter
	private volatile Thread edtThread;
	@Setter
	private volatile boolean paused;

	private final AtomicLong samplingNanos = new AtomicLong();
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> task;

	public SamplerService(StackAttributor attributor, SampleSink sink)
	{
		this.attributor = attributor;
		this.sink = sink;
	}

	public synchronized void start(int rateHz)
	{
		stop();
		executor = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread t = new Thread(r, "plugin-profiler-sampler");
			t.setDaemon(true);
			return t;
		});
		long periodNanos = TimeUnit.SECONDS.toNanos(1) / Math.max(1, rateHz);
		task = executor.scheduleAtFixedRate(this::sampleTick, periodNanos, periodNanos, TimeUnit.NANOSECONDS);
		log.debug("sampler started at {} Hz", rateHz);
	}

	public synchronized void stop()
	{
		if (task != null)
		{
			task.cancel(false);
			task = null;
		}
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
	}

	private void sampleTick()
	{
		if (paused)
		{
			return;
		}

		try
		{
			long start = System.nanoTime();
			long now = System.currentTimeMillis();
			sampleThread(clientThread, ThreadKind.CLIENT, now);
			sampleThread(edtThread, ThreadKind.EDT, now);
			samplingNanos.addAndGet(System.nanoTime() - start);
			sink.tick(samplingNanos.get());
		}
		catch (RuntimeException e)
		{
			// never let one bad tick kill the schedule
			log.debug("sampling tick failed", e);
		}
	}

	private void sampleThread(Thread thread, ThreadKind kind, long now)
	{
		if (thread == null || !thread.isAlive())
		{
			return;
		}

		StackTraceElement[] stack = thread.getStackTrace();
		if (stack.length == 0)
		{
			// thread was idle or not yet started; nothing to attribute
			return;
		}

		StackAttributor.Attribution attribution = attributor.attribute(stack);
		sink.accept(new Sample(kind, attribution.getOwner(), attribution.getTopFrame(), now));
	}
}
