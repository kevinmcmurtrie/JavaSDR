package us.pixelmemory.kevin.sdr.tuners;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleConsumer;
import us.pixelmemory.kevin.sdr.IQSampleProcessor;

/**
 * Perform a static frequency shift.  This doesn't have any interesting internals so
 * it is converted to a trivial IQSampleProcessor or IQSampleConsumer.
 */
public final class FrequencyShift{
	private final double sampleRate;
	private final double frequency;
	
	public FrequencyShift(final double sampleRate, final double frequency) {
		this.sampleRate = sampleRate;
		this.frequency = frequency;
	}
	
	public <T extends Throwable> IQSampleProcessor<T> asProcessor () {
		final Clock clock= new Clock(sampleRate, frequency);
		return (iqIn, iqOut) -> {
			iqOut.set(iqIn);
			iqOut.rotate(-clock.getAndTick());
		};
	}
	
	public  <T extends Throwable> IQSampleConsumer<T> asConsumer (final IQSampleConsumer<T> out) {
		final IQSample iqOut= new IQSample();
		final Clock clock= new Clock(sampleRate, frequency);
		return (iqIn) -> {
			iqOut.set(iqIn);
			iqOut.rotate(-clock.getAndTick());
			out.accept(iqOut);
		};
	}
}
