package us.pixelmemory.kevin.sdr.firfilters;

/**
 * Average of the past cycleCount*sampleRate samples (integer)
 */
public record RunningAverage(int cycleCount) implements FilterBuilder {
	@Override
	public FilterFIR build(final float sampleRate) {
		return new RunningAverageFIR((int) Math.ceil(sampleRate * cycleCount));
	}
}