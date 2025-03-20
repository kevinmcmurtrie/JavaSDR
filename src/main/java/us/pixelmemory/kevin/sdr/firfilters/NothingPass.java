package us.pixelmemory.kevin.sdr.firfilters;

public record NothingPass() implements FilterBuilder {
	@Override
	public FilterFIR build(final float sampleRate) {
		return new FilterFIR() {
			@Override
			public int latency() {
				return 0;
			}

			@Override
			public float apply(final float[] circBuf, final int pos) {
				return 0;
			}
		};
	}
}