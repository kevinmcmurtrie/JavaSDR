package us.pixelmemory.kevin.sdr.firfilters;

public interface FilterAlphaFIR {
	int latency();

	float apply(float circBufSignal[], float circBufAlpha[],int pos);
}