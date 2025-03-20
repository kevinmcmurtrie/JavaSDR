package us.pixelmemory.kevin.sdr.tuners;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleProcessor;

public final class Tuner<LOCK extends TunerLock> implements IQSampleProcessor<RuntimeException, LOCK> {
	private final LOCK lock;
	private final double tauCyclesPerSample;
	private final double sampleRate;
	private final double frequency;

	private double clock = 0.0d;
	private IQFaker faker = null;

	/**
	 * @param samplesPerCycle
	 * @param aftFractionalSpeed Phase error applied to aft
	 * @param aftLimit Maximum percent phase per each sample (should be small)
	 */
	public Tuner(final LOCK lock, final double sampleRate, final double frequency) {
		this.lock = lock;
		this.sampleRate = sampleRate;
		this.frequency = frequency;
		final double samplesPerCycle = sampleRate / frequency;
		if (Math.abs(samplesPerCycle) <= 2) {
			throw new IllegalArgumentException("sampleRate is too low");
		}
		this.tauCyclesPerSample = Math.TAU / samplesPerCycle;
	}

	// De-drift tuning.
	public Tuner(final LOCK lock, final double sampleRate) {
		this.lock = lock;
		this.sampleRate = sampleRate;
		frequency = 0;
		this.tauCyclesPerSample = 0;
	}

	public double getSampleRate() {
		return sampleRate;
	}

	public double getFrequency() {
		return frequency;
	}

	/**
	 * @return -Math.PI .. Math.PI as sawtooth /|/|/|
	 */
	public double getClock() {
		return clock;
	}

	public double getClockRate() {
		return tauCyclesPerSample + lock.getClockRateAdjustment();
	}

	private void clockTick() {
		clock += tauCyclesPerSample + lock.consumeClockRateAdjustment();
		if (clock > Math.PI) {
			clock -= Math.TAU;
		}
		if (clock < -Math.PI) {
			clock += Math.TAU;
		}
	}

	/**
	 * @param src Input sample
	 * @param out Output IQSample
	 * @return AFT result
	 */
	@Override
	public LOCK accept(final IQSample src, final IQSample out) {
		//FIXME - Use new rotate method
		out.setMoment(clock);
		out.conjugate();
		out.multiply(src);
		out.rotateRight();
		lock.accept(src, out, getClock());
		clockTick();
		return lock;
	}

	/**
	 * Tune using an IQSample faked from a single value using a delay. This only works for an extremely narrow bandwidth
	 * with a phase lock.
	 *
	 * @param src
	 * @param out
	 */
	public LOCK accept(final float src, final IQSample out) {
		if (faker == null) {
			faker = new IQFaker();
		}
		return accept(faker.fakeIQ(src), out);
	}

	public LOCK accept(final float src, final float signalFrequency, final IQSample out) {
		if (faker == null) {
			faker = new IQFaker(Math.TAU / (sampleRate / signalFrequency));
		}
		return accept(faker.fakeIQ(src), out);
	}

	private final class IQFaker {
		@FunctionalInterface
		interface Clock {
			double getClockRate();
		}

		private final Clock clockSrc;
		private final IQSample iq = new IQSample();
		private final float circBuffer[];
		private int pos = 0;

		IQFaker() {
			final int size = (int) (2 * getSampleRate() / getFrequency());
			circBuffer = new float[size];
			clockSrc = Tuner.this::getClockRate;
		}

		IQFaker(final double actualFrequency) {
			final int size = (int) (2 * getSampleRate() / getFrequency());
			circBuffer = new float[size];
			clockSrc = () -> actualFrequency;
		}

		IQSample fakeIQ(final float f) {
			// Linear interpolation of delayed sample
			final float delay = (float) (0.5 * Math.PI / clockSrc.getClockRate()); // Already delayed one sample
			int phasedPosition1 = (int) delay;
			final float phasedPositionFraction = delay - phasedPosition1;

			phasedPosition1 = pos - phasedPosition1;
			if (phasedPosition1 < 0) {
				phasedPosition1 += circBuffer.length;
			}

			int phasedPosition2 = phasedPosition1 - 1;
			if (phasedPosition2 < 0) {
				phasedPosition2 += circBuffer.length;
			}

			final float delayedSample = circBuffer[phasedPosition1] * (1 - phasedPositionFraction) + circBuffer[phasedPosition2] * phasedPositionFraction;
			iq.set(f, -delayedSample);

			circBuffer[pos] = f;
			pos++;
			if (pos == circBuffer.length) {
				pos = 0;
			}
			return iq;
		}
	}
}
