package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleProcessor;
import us.pixelmemory.kevin.sdr.IQVisualizer;

public interface TunerLock extends IQSampleProcessor<RuntimeException>{
	/**
	 * @return Hz clock adjustment
	 */
	double getClockRateAdjustment();

	static double unwrapPhaseDistance(final double d) {
		if (d > Math.PI) {
			return d - Math.TAU;
		}
		if (d < -Math.PI) {
			return Math.TAU + d;
		}
		return d;
	}

	static float unwrapPhaseDistance(final float d) {
		if (d > (float) Math.PI) {
			return d - (float) Math.TAU;
		}
		if (d < (float) -Math.PI) {
			return (float) Math.TAU + d;
		}
		return d;
	}

	static TunerLock getNoLock(final double sampleRate, final double frequency, final boolean debug) {
		final Clock clock= new Clock(sampleRate, frequency);
		if (debug) {
			final IQVisualizer vis = new IQVisualizer();
			vis.syncOnColor(Color.blue);

			return new TunerLock() {

				@Override
				public double getClockRateAdjustment() {
					return 0;
				}

				@Override
				public void accept(IQSample in, IQSample out) {
					out.set(in);
					out.rotate(-clock.getAndTick());
					vis.drawIQ(Color.red, in);
					vis.drawIQ(Color.blue, out);
				}
			};
		} else {
			return new TunerLock() {
				@Override
				public double getClockRateAdjustment() {
					return 0;
				}

				@Override
				public void accept(IQSample in, IQSample out) {
					out.set(in);
					out.rotate(-clock.getAndTick());
				}
			};
		}
	}
}
