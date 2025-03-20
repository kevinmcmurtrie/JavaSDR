package us.pixelmemory.kevin.sdr.resamplers;

import us.pixelmemory.kevin.sdr.IntConsumer;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;

/**
 * Downsample integer identifiers without changing them.
 * The most common value is chosen, with weight given to the Lanczos value.
 *
 * @param <T>
 */
public class DownsamplerIdentifier<T extends Throwable> implements IntConsumer<T> {
	final float destinationSampleRate;
	final Accumulator<T>[] accs;

	private static final class Accumulator<T extends Throwable> extends AccumulatorBase implements IntConsumer<T> {
		// 32 bits of value in low side
		// 16 bits of integer weight (signed)
		// 16 bits of fractional weight
		private static final int fractMultiplier = 0x10000;
		private static final int weightShift = 32;
		private static final long valueMask = 0xFFFFFFFFl; // Need this to mask int sign extension

		private final long countAndValue[];
		private final IntConsumer<T> out;

		public Accumulator(final LanczosTable lanczos, final IntConsumer<T> out, final float inPosition, final float inToOut) {
			super(lanczos, inPosition, inToOut);
			countAndValue = new long[(int) Math.min(0xFFFFFFFFl, Math.ceil(inToOut))];
			this.out = out;
		}

		@Override
		public void accept(final int v) throws T {
			final long lz = (long) Math.round(fractMultiplier * lz()) << weightShift;
			for (int pos = 0; pos < countAndValue.length; ++pos) {
				long cv = countAndValue[pos];

				if ((cv == 0L) || ((int) (cv & valueMask) == v)) {
					if (cv == 0l) {
						cv = (v & valueMask) + lz; // Set
					} else {
						cv += lz; // Increment
					}

					if (cv == 0) {
						// Zero is a magic number indicating a free space in the array. Don't let it happen.
						cv = 1l << weightShift;
					}

					// Bump newer entries to front so most common entries are found sooner
					if (pos > 0) {
						countAndValue[pos] = countAndValue[pos - 1];
						countAndValue[pos - 1] = cv;
					} else {
						countAndValue[0] = cv;
					}
					break;
				}
			}

			if (step()) {
				long highest = countAndValue[0];
				countAndValue[0] = 0;
				for (int pos = 1; (pos < countAndValue.length) && (countAndValue[pos] != 0l); ++pos) {
					if (countAndValue[pos] > highest) {
						highest = countAndValue[pos];
					}
					countAndValue[pos] = 0;
				}
				out.accept((int) (highest & valueMask));
			}
		}
	}
	//
	// public static void main (String args[]) throws InterruptedException {
	// IQVisualizer vis= new IQVisualizer();
	// vis.syncOnColor(Color.red);
	// DownsamplerIdentifier<RuntimeException> ds= new DownsamplerIdentifier<>(LanczosTable.of(2), 1000, 100, i -> vis.drawAnalog(Color.red, 5 + i/10f));
	// for (double p= -10*Math.TAU; p < 10*Math.TAU; p+= 0.01) {
	// int i= (int)Math.round(20f * Math.sin(p));
	// vis.drawAnalog(Color.green, 5 + i/10f);
	// ds.accept(i);
	// vis.repaint();
	// Thread.sleep(10);
	// }
	// }

	public float getSampleRate() {
		return destinationSampleRate;
	}

	public DownsamplerIdentifier(final LanczosTable lanczos, final float sourceSampleRate, final float destinationSampleRate, final IntConsumer<T> out) {
		this.destinationSampleRate = destinationSampleRate;

		int x = -2 * lanczos.A;
		final float ratio = sourceSampleRate / destinationSampleRate;
		accs = new Accumulator[2 * lanczos.A];
		for (int i = 0; i < 2 * lanczos.A; ++i) {
			accs[i] = new Accumulator<>(lanczos, out, x++, ratio);
		}
	}

	@Override
	public void accept(final int id) throws T {
		for (final var acc : accs) {
			acc.accept(id);
		}
	}
}
