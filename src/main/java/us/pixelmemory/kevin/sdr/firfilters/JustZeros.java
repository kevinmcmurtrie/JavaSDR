package us.pixelmemory.kevin.sdr.firfilters;

public record JustZeros() implements FilterBuilder {
	public static final JustZeros INSTANCE = new JustZeros();
	private static final FilterFIR FIR = new FilterFIR() {
		@Override
		public int latency() {
			return 0;
		}

		@Override
		public float apply(final float[] circBuf, final int pos) {
			return 0f;
		}
	};

	@Override
	public FilterFIR build(final float sampleRate) {
		return FIR;
	}
}