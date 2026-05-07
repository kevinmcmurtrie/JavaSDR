package us.pixelmemory.kevin.sdr.resamplers;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.FloatArrayConsumer;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;
import us.pixelmemory.kevin.sdr.tuners.Clock;

public class DownsamplerArray<T extends Throwable> implements FloatArrayConsumer<T> {
	private final float destinationSampleRate;
	final Accumulator<T>[] accs;

	private static final class Accumulator<T extends Throwable> extends AccumulatorBase implements FloatArrayConsumer<T> {
		private final FloatArrayConsumer<T> out;
		final int elementCount;
		private final float[] acc;
		private float sum = 0f;

		public Accumulator(final LanczosTable lanczos, final FloatArrayConsumer<T> out, final float inPosition, final int elementCount, final float inToOut) {
			super(lanczos, inPosition, inToOut);
			this.acc = new float[elementCount];
			this.elementCount = elementCount;
			this.out = out;
		}

		@Override
		public void accept(final float[] f) throws T {
			final float lz = lz();
			for (int i = 0; i < elementCount; ++i) {
				acc[i] += lz * f[i];
			}
			sum += lz;
			if (step()) {
				try {
					if (sum > 0) {
						for (int i = 0; i < elementCount; ++i) {
							acc[i] /= sum;
						}
					} else {
						for (int i = 0; i < elementCount; ++i) {
							acc[i] = 0;
						}
					}
					out.accept(acc);
				} finally {
					for (int i = 0; i < elementCount; ++i) {
						acc[i] = 0;
					}
					sum = 0;
				}
			}
		}

	}

	public float getSampleRate() {
		return destinationSampleRate;
	}

	public DownsamplerArray(final LanczosTable lanczos, final float sourceSampleRate, final float destinationSampleRate, final int elementCount, final FloatArrayConsumer<T> out) {
		this.destinationSampleRate = destinationSampleRate;
		int x = -2 * lanczos.A;
		final float ratio = sourceSampleRate / destinationSampleRate;
		accs = new Accumulator[2 * lanczos.A];
		for (int i = 0; i < 2 * lanczos.A; ++i) {
			accs[i] = new Accumulator<>(lanczos, out, x++, elementCount, ratio);
		}
	}

	@Override
	public void accept(final float[] f) throws T {
		for (final var acc : accs) {
			acc.accept(f);
		}
	}

	public static void main(final String args[]) throws InterruptedException {
		final IQVisualizer vis = new IQVisualizer(4);

		final FloatArrayConsumer<RuntimeException> out = f -> {
			vis.fadeLight();
			vis.drawAnalog(Color.red, 2 + f[0]);
			vis.drawAnalog(Color.blue, f[1]);
			vis.repaint();
		};

		final DownsamplerArray<RuntimeException> ds = new DownsamplerArray<>(LanczosTable.of(4), 1000000.0f, 19000f, 2, out);
		final Clock c = new Clock(1000000.0f, 1187.5f);

		final float f[] = new float[2];

		for (int i = 0; i < 10000000; ++i) {
			final double wave = c.tickAndGet();
			f[0] = (float) Math.sin(wave);
			f[1] = (float) Math.cos(wave);
			ds.accept(f);
			Thread.sleep(1);
		}
	}
}
