package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;

public interface TunerLock {
	/**
	 * Informational. Get the AFT limit of this tuner lock.
	 *
	 * @return Value specific to this lock
	 */
	double getAFTLimit();

	/**
	 * Get and consume the sin/cos clock rate per sample adjustment.
	 * An implementation may return a one-time value that is consumed to
	 * perform phase flipping.
	 *
	 * @return sin/cos per sample adjustment
	 */
	default double consumeClockRateAdjustment() {
		return getClockRateAdjustment();
	}

	/**
	 * Informational. Get the sin/cos clock rate per sample adjustment.
	 *
	 * @return sin/cos per sample adjustment
	 */
	double getClockRateAdjustment();

	void accept(IQSample src, IQSample out, double clock);

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

	static TunerLock getNoLock(final boolean debug) {
		if (debug) {
			final IQVisualizer vis = new IQVisualizer();
			final IQSample clockIQ = new IQSample();
			vis.syncOnColor(Color.green);

			return new TunerLock() {
				@Override
				public double getAFTLimit() {
					return 0;
				}

				@Override
				public double getClockRateAdjustment() {
					return 0;
				}

				@Override
				public void accept(final IQSample src, final IQSample out, final double clock) {
					clockIQ.setMoment(clock);
					vis.drawIQ(Color.green, clockIQ);
					vis.drawIQ(Color.red, src);
					vis.drawIQ(Color.blue, out);
				}
			};
		} else {
			return new TunerLock() {
				@Override
				public double getAFTLimit() {
					return 0;
				}

				@Override
				public double getClockRateAdjustment() {
					return 0;
				}

				@Override
				public void accept(final IQSample src, final IQSample out, final double clock) {
				}
			};
		}
	}
}
