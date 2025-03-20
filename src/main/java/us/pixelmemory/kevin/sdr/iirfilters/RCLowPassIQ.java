package us.pixelmemory.kevin.sdr.iirfilters;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleProcessor;

public class RCLowPassIQ implements IQSampleProcessor<RuntimeException, Void> {
	private final double ratio;

	public RCLowPassIQ(final double sampleRate, final double r, final double c) {
		this(sampleRate, r * c);
	}

	public RCLowPassIQ(final double sampleRate, final double tau) {
		this(tau * sampleRate);
	}

	public RCLowPassIQ(final double ratio) {
		this.ratio = ratio;
	}

	@Override
	public Void accept(final IQSample in, final IQSample out) throws RuntimeException {
		out.in += (in.in - out.in) / ratio;
		out.quad += (in.quad - out.quad) / ratio;
		return null;
	}
}
