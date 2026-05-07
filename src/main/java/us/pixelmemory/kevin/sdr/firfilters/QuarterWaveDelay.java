package us.pixelmemory.kevin.sdr.firfilters;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.FloatConsumer;
import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleConsumer;
import us.pixelmemory.kevin.sdr.IQVisualizer;

/**
 * Converts a fixed frequency signal to a positive frequency in IQ format using a fixed quarter wave delay.
 * The delay uses simple linear interpolation so it works best for high sample rates relative to the frequency.
 * <br>
 * Used to find constant frequency pilot carriers inside another signal.
 */
public final class QuarterWaveDelay {
	private final float circBuffer[];
	private int pos = 0;
	private int sampleDelay;
	private float splitweight;

	public QuarterWaveDelay(final float sampleRate, final float frequency) {
		this(sampleRate / (4f * frequency));
	}

	public QuarterWaveDelay(final float sampleDelay) {
		this.sampleDelay = (int) sampleDelay;
		circBuffer = new float[1 + this.sampleDelay];
		splitweight = sampleDelay - this.sampleDelay;
	}

	public void convert(final float f, IQSample out) {
		out.set(f, delay(f));
	}
	
	public float delay (float f) {
		int delayIdx1 = pos - sampleDelay;
		if (delayIdx1 < 0) {
			delayIdx1 += circBuffer.length;
		}
				
		pos++;
		if (pos >= circBuffer.length) {
			pos -= circBuffer.length;
		}
		
		int delayIdx0 = pos - sampleDelay;
		if (delayIdx0 < 0) {
			delayIdx0 += circBuffer.length;
		}
		
		final float delayed= splitweight * circBuffer[delayIdx1] + (1 - splitweight) * circBuffer[delayIdx0];
		circBuffer[pos] = f;
		return delayed;
	}
	
	public  <T extends Throwable> FloatConsumer<T> asConsumer (final IQSampleConsumer<T> out) {
		final IQSample iqOut= new IQSample();
		return (f) -> {
			convert(f, iqOut);
			out.accept(iqOut);
		};
	}

	public static void main(String args[]) throws InterruptedException {
		final IQVisualizer vis = new IQVisualizer();

		final float sampleRate = 400000.0f;
		final float frequency = 19000;
		final float amFrequency = 20000;
		
		QuarterWaveDelay ts = new QuarterWaveDelay(sampleRate, frequency);

		final float tauCyclesPerSample = (float)(frequency * Math.TAU / sampleRate);
		final float amTauCyclesPerSample = (float)(amFrequency * Math.TAU / sampleRate);

		IQSample iq = new IQSample();
		IQSample fakeIq = new IQSample();

		for (int i = 0; i < 10000; ++i) {
			float c = i * tauCyclesPerSample;
			float am= i * amTauCyclesPerSample;
			
			iq.setMoment(c);
			iq.multiply(0.5f + 0.1f*(float)Math.sin(am));
			ts.convert(iq.in, fakeIq);

			vis.fade();
			vis.drawIQ(Color.red, iq);
			vis.drawIQ(Color.green, fakeIq);
			
			fakeIq.rotate(-c);
			vis.drawIQ(Color.blue, fakeIq);
			vis.repaint();
			Thread.sleep(10);
		}
	}
}
