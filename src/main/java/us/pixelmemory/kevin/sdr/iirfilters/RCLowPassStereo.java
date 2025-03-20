package us.pixelmemory.kevin.sdr.iirfilters;

import us.pixelmemory.kevin.sdr.FloatPairConsumer;

public class RCLowPassStereo<T extends Throwable> implements FloatPairConsumer<T> {
	private final double ratio;
	private final FloatPairConsumer<T> out;
	private double leftAcc = 0d;
	private double rightAcc = 0d;

	public RCLowPassStereo(final double sampleRate, final double r, final double c, final FloatPairConsumer<T> consumer) {
		this(sampleRate, r * c, consumer);
	}

	public RCLowPassStereo(final double sampleRate, final double tau, final FloatPairConsumer<T> consumer) {
		ratio = tau * sampleRate;
		out = consumer;
	}

	@Override
	public void accept(final float left, final float right) throws T {
		leftAcc += (left - leftAcc) / ratio;
		rightAcc += (right - rightAcc) / ratio;
		out.accept((float) leftAcc, (float) rightAcc);
	}
}
