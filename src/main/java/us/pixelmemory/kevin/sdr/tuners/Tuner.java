package us.pixelmemory.kevin.sdr.tuners;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleProcessor;

public final class Tuner<LOCK extends TunerLock> implements IQSampleProcessor<RuntimeException, LOCK> {
	private final LOCK lock;
	private final double sampleRate;
	private final double frequency;

	private Clock clock;
	private IQFaker faker = null;

	public Tuner(final LOCK lock, final double sampleRate, final double frequency) {
		this.lock = lock;
		this.sampleRate = sampleRate;
		this.frequency = frequency;
		clock= new Clock(sampleRate, frequency);
	}

	// De-drift tuning.
	public Tuner(final LOCK lock, final double sampleRate) {
		this.lock = lock;
		this.sampleRate = sampleRate;
		frequency = 0;
		clock= new Clock(sampleRate, 0);
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
		return clock.getClock();
	}

	public double getClockRate() {
		return clock.tauPerSample + lock.getClockRateAdjustment();
	}


	/**
	 * @param src Input sample
	 * @param out Output IQSample
	 * @return AFT result
	 */
	@Override
	public LOCK accept(final IQSample src, final IQSample out) {
		out.set(src);
		final double c= clock.getAndTick(lock.consumeClockRateAdjustment());
		out.rotate(-c);
		lock.accept(src, out, c);
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

	//This has to be used before the clock tick consumes a rate adjustment
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
