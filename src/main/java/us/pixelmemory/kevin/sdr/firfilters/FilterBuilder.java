package us.pixelmemory.kevin.sdr.firfilters;

@FunctionalInterface
public interface FilterBuilder {
	FilterFIR build(float sampleRate);
}