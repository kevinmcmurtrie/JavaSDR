package us.pixelmemory.kevin.sdr.resamplers;

import us.pixelmemory.kevin.sdr.FloatPairConsumer;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;

public class DownsamplerStereo<T extends Throwable> implements FloatPairConsumer<T> {
	private final float destinationSampleRate;
	final Accumulator<T>[] accs;

	private static final class Accumulator<T extends Throwable> extends AccumulatorBase implements FloatPairConsumer<T> {
		private final FloatPairConsumer<T> out;
		private float accLeft = 0f;
		private float accRight = 0f;
		private float sum = 0f;

		public Accumulator(final LanczosTable lanczos, final FloatPairConsumer<T> out, final float inPosition, final float inToOut) {
			super(lanczos, inPosition, inToOut);
			this.out = out;
		}

		@Override
		public void accept(final float left, final float right) throws T {
			final float lz = lz();
			accLeft += lz * left;
			accRight += lz * right;
			sum += lz;
			if (step()) {
				try {
					if (sum > 0) {
						out.accept(accLeft / sum, accRight / sum);
					} else {
						out.accept(0, 0);
					}
				} finally {
					accLeft = 0;
					accRight = 0;
					sum = 0;
				}
			}
		}
	}

	public float getSampleRate() {
		return destinationSampleRate;
	}

	public DownsamplerStereo(final LanczosTable lanczos, final float sourceSampleRate, final float destinationSampleRate, final FloatPairConsumer<T> out) {
		this.destinationSampleRate = destinationSampleRate;

		int x = -2 * lanczos.A;
		final float ratio = sourceSampleRate / destinationSampleRate;
		accs = new Accumulator[2 * lanczos.A];
		for (int i = 0; i < 2 * lanczos.A; ++i) {
			accs[i] = new Accumulator<>(lanczos, out, x++, ratio);
		}
	}

	@Override
	public void accept(final float left, final float right) throws T {
		for (final var acc : accs) {
			acc.accept(left, right);
		}
	}
}
