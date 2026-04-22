package us.pixelmemory.kevin.sdr.firfilters;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.FloatArrayConsumer;
import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.tuners.Clock;
import us.pixelmemory.kevin.sdr.tuners.PhaseLock;

/**
 * Lanczos A should be a higher level for narrower bands.  If the bandwidth is too narrow and the Lanczos level too low,
 * there will be out of band ringing that goes in and out of phase.  These are the ripple ends of the Lanczos excessively amplified.
 */
public record BandPass(LanczosTable lanczos, float lowBand, float highBand) implements FilterBuilder {
	
	public static void main(String args[]) throws InterruptedException {
		float sampleRate = 1000000;
		float frequency = 19000;
		float modFrequency= 40;
		float modGain= 0.1f;

		final IQVisualizer vis = new IQVisualizer();
		
		
		final FloatArrayConsumer<RuntimeException> consumer = f -> {
			vis.drawAnalog(Color.white, 2);
			vis.drawAnalog(Color.lightGray, 4);
			vis.drawAnalog(Color.gray, 0);
			vis.drawAnalog(Color.red, 2* f[0] +2);
			vis.drawAnalog(Color.blue, 2* f[1] +2);
			vis.drawAnalog(Color.orange, f[2]);
			vis.repaint();
			vis.fadeLight();
		};
		
		final FilterBuilder filterBuilder = new BandPass(LanczosTable.of(6),  frequency - 100f, frequency + 100f);
		MultiFilter<RuntimeException> mf = new MultiFilter<>(sampleRate, consumer, 2, filterBuilder);

		Clock c = new Clock(sampleRate, frequency);
		Clock c2 = new Clock(sampleRate, modFrequency);

		for (int i = 0; i < 10000000; ++i) {
			float mod= (float) Math.cos(c2.getAndTick());
			float src = (float) Math.cos(c.getAndTick(modGain*mod));

			mf.accept(src, src, mod);
			Thread.sleep(1);

		}
	}
	
	
	@Override
	public BandPassFIR build(final float sampleRate) {
		// IQVisualizer vis = new IQVisualizer();

		// Lanczos distance for half wave
		final int lowLatency = (int) Math.ceil(0.5 * lanczos.A * sampleRate / lowBand);
		final int highLatency = (int) Math.ceil(0.5 * lanczos.A * sampleRate / highBand);
		final float lowScale = (float) (2d * lowBand / sampleRate);
		final float highScale = (float) (2d * highBand / sampleRate);

		// Test run at center frequency to get filter gain.
		// I don't know the formula to compensate for the gain drifting as lowScale and highScale converge.
		final double clockRate = (lowBand + highBand) * Math.PI / sampleRate;
		if (Math.abs(clockRate) >= Math.PI) {
			throw new IllegalArgumentException("Sample rate too low");
		}
		double clock = clockRate * -lowLatency;

		float lowSum = 0;
		float highSum = 0;
		float lowAcc = 0;
		float highAcc = 0;

		// Lowest end of larger low band
		float lowLzPos = -lowLatency * lowScale;
		for (int i = -lowLatency; i < -highLatency; ++i, lowLzPos += lowScale, clock += clockRate) {
			final double sample = Math.cos(clock);
			final float lzLow = lanczos.apply(lowLzPos);
			lowSum += lzLow;
			lowAcc += lzLow * sample;
			// for (int x = 0; x < 7; ++x) {
			// vis.drawAnalog(Color.red, sample);
			// vis.drawAnalog(Color.green, lzLow);
			// vis.drawAnalog(Color.blue, 0);
			// }
		}

		// Overlapping area
		float highLzPos = -highLatency * highScale;
		for (int i = -highLatency; i <= highLatency; ++i, lowLzPos += lowScale, highLzPos += highScale, clock += clockRate) {
			final double sample = Math.cos(clock);
			final float lzLow = lanczos.apply(lowLzPos);
			lowSum += lzLow;
			lowAcc += lzLow * sample;
			final float lzHigh = lanczos.apply(highLzPos);
			highSum += lzHigh;
			highAcc += lzHigh * sample;
			// for (int x = 0; x < 7; ++x) {
			// vis.drawAnalog(Color.red, sample);
			// vis.drawAnalog(Color.green, lzLow);
			// vis.drawAnalog(Color.blue, lzHigh);
			// }
		}

		// Highest end of larger low band
		for (int i = highLatency + 1; i <= lowLatency; ++i, lowLzPos += lowScale, clock += clockRate) {
			final double sample = Math.cos(clock);
			final float lzLow = lanczos.apply(lowLzPos);
			lowSum += lzLow;
			lowAcc += lzLow * sample;
			// for (int x = 0; x < 7; ++x) {
			// vis.drawAnalog(Color.red, sample);
			// vis.drawAnalog(Color.green, lzLow);
			// vis.drawAnalog(Color.blue, 0);
			// }
		}

		// vis.repaint();
		// vis.close();
		return new BandPassFIR(lanczos, lowScale, highScale, lowLatency, highLatency, lowSum, highSum, (float) (1d / ((highAcc / highSum) - (lowAcc / lowSum))));
	}
}