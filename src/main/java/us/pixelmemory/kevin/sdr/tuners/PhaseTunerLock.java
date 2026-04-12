package us.pixelmemory.kevin.sdr.tuners;

public interface PhaseTunerLock extends TunerLock {
	double getClock();
	float getPhase();
}
