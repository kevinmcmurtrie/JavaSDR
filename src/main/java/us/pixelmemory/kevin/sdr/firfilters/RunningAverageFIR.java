package us.pixelmemory.kevin.sdr.firfilters;

public final class RunningAverageFIR implements FilterFIR {
	private final int samples;
	private final int latency;
	private double runningSum = 0f;

	public RunningAverageFIR(final int samples) {
		this.samples = samples;
		latency = Math.ceilDiv(samples, 2);
	}

	@Override
	public int latency() {
		return latency;
	}

	@Override
	public float apply(final float[] circBuf, final int pos) {
		runningSum += circBuf[(pos + latency) & (circBuf.length - 1)];
		runningSum -= circBuf[(pos + latency - samples) & (circBuf.length - 1)];
		return (float) (runningSum / samples);
	}
}