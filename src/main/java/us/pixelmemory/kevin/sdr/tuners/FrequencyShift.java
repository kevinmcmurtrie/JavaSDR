package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleConsumer;
import us.pixelmemory.kevin.sdr.IQSampleProcessor;
import us.pixelmemory.kevin.sdr.IQVisualizer;

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
			iqOut.rotate((float)(clock.getAndTick()));
		};
	}
	
	public  <T extends Throwable> IQSampleConsumer<T> asConsumer (final IQSampleConsumer<T> out) {
		final IQSample iqOut= new IQSample();
		final Clock clock= new Clock(sampleRate, frequency);
		return (iqIn) -> {
			iqOut.set(iqIn);
			iqOut.rotate((float)(clock.getAndTick()));
			out.accept(iqOut);
		};
	}
	
	
	public static void main (String args[]) throws Exception {
		final IQVisualizer vis = new IQVisualizer();
		float sampleRate= 200000;
		float frequency = 1900;
		
		IQSampleProcessor<Exception> fl= new FrequencyShift(sampleRate, -frequency).asProcessor();
		Clock c = new Clock(sampleRate, frequency);
		final IQSample src= new IQSample();
		final IQSample out= new IQSample();
		
		for (int i= 0; i < 10000000; ++i) {
			src.setMoment((float)c.getAndTick());
			fl.accept(src, out);
			vis.drawIQ(Color.red, src);
			vis.drawIQ(Color.blue, out);
			if (i % 10 == 0) {
				vis.repaint();
				Thread.sleep(100);
				vis.fadeLight();
			}
		}
	}
}
