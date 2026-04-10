package us.pixelmemory.kevin.sdr.firfilters;

@FunctionalInterface
public interface FilterAlphaBuilder {
	FilterAlphaFIR build(float sampleRate);
}