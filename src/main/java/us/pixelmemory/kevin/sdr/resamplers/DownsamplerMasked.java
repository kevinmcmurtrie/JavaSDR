package us.pixelmemory.kevin.sdr.resamplers;

import us.pixelmemory.kevin.sdr.FloatConsumer;
import us.pixelmemory.kevin.sdr.FloatPairConsumer;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;

public class DownsamplerMasked<T extends Throwable> implements FloatPairConsumer<T> {
	private final float destinationSampleRate;
	final Accumulator<T>[] accs;

	private static final class Accumulator<T extends Throwable> extends AccumulatorBase implements FloatPairConsumer<T> {
		private final FloatConsumer<T> out;
		private float accSignal = 0f;
		private float sum = 0f;

		public Accumulator(final LanczosTable lanczos, final FloatConsumer<T> out, final float inPosition, final float inToOut) {
			super(lanczos, inPosition, inToOut);
			this.out = out;
		}

		@Override
		public void accept(final float signal, final float mask) throws T {
			final float lz = lz() * mask;
			accSignal += lz * signal;
			sum += lz;
			if (step()) {
				try {
					if (sum > 0) {
						out.accept(accSignal / sum);
					} else {
						out.accept(0);
					}
				} finally {
					accSignal = 0;
					sum = 0;
				}
			}
		}
	}

	public float getSampleRate() {
		return destinationSampleRate;
	}

	public DownsamplerMasked(final LanczosTable lanczos, final float sourceSampleRate, final float destinationSampleRate, final FloatConsumer<T> out) {
		this.destinationSampleRate = destinationSampleRate;

		int x = -2 * lanczos.A;
		final float ratio = sourceSampleRate / destinationSampleRate;
		accs = new Accumulator[2 * lanczos.A];
		for (int i = 0; i < 2 * lanczos.A; ++i) {
			accs[i] = new Accumulator<>(lanczos, out, x++, ratio);
		}
	}

	@Override
	public void accept(final float signal, final float mask) throws T {
		for (final var acc : accs) {
			acc.accept(signal, mask);
		}
	}
}
