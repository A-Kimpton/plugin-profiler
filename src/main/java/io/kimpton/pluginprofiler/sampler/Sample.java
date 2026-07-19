package io.kimpton.pluginprofiler.sampler;

import lombok.Value;

@Value
public class Sample
{
	ThreadKind thread;
	String owner;
	String topFrame;
	long timestampMillis;
}
