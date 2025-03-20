package us.pixelmemory.kevin.sdr.resamplers;

import us.pixelmemory.kevin.sdr.FloatConsumer;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;

public class Downsampler<T extends Throwable> implements FloatConsumer<T> {
	final float destinationSampleRate;
	final Accumulator<T>[] accs;

	private static final class Accumulator<T extends Throwable> extends AccumulatorBase implements FloatConsumer<T> {
		private final FloatConsumer<T> out;
		private float acc = 0f;
		private float sum = 0f;

		public Accumulator(final LanczosTable lanczos, final FloatConsumer<T> out, final float inPosition, final float inToOut) {
			super(lanczos, inPosition, inToOut);
			this.out = out;
		}

		@Override
		public void accept(final float f) throws T {
			final float lz = lz();
			acc += lz * f;
			sum += lz;
			if (step()) {
				try {
					out.accept((sum > 0) ? acc / sum : 0);
				} finally {
					acc = 0;
					sum = 0;
				}
			}
		}
	}

	public float getSampleRate() {
		return destinationSampleRate;
	}

	public Downsampler(final LanczosTable lanczos, final float sourceSampleRate, final float destinationSampleRate, final FloatConsumer<T> out) {
		this.destinationSampleRate = destinationSampleRate;

		int x = -2 * lanczos.A;
		final float ratio = sourceSampleRate / destinationSampleRate;
		accs = new Accumulator[2 * lanczos.A];
		for (int i = 0; i < 2 * lanczos.A; ++i) {
			accs[i] = new Accumulator<>(lanczos, out, x++, ratio);
		}
	}

	@Override
	public void accept(final float f) throws T {
		for (final var acc : accs) {
			acc.accept(f);
		}
	}
}
