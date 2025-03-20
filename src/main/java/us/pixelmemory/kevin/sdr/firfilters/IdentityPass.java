package us.pixelmemory.kevin.sdr.firfilters;

public record IdentityPass() implements FilterBuilder {
	@Override
	public FilterFIR build(final float sampleRate) {
		return new FilterFIR() {
			@Override
			public int latency() {
				return 1;
			}

			@Override
			public float apply(final float[] circBuf, final int pos) {
				return circBuf[pos];
			}
		};
	}
}