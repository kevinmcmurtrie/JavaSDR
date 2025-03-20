package us.pixelmemory.kevin.sdr.firfilters;

public interface FilterFIR {
	int latency();

	float apply(float circBuf[], int pos);
}