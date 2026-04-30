package us.pixelmemory.kevin.sdr.tuners;


import us.pixelmemory.kevin.sdr.IQSampleProcessor;

public interface TunerLock extends IQSampleProcessor<RuntimeException>{
	/**
	 * @return Hz clock adjustment
	 */
	float getClockRateAdjustment();

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
}
