package us.pixelmemory.kevin.sdr.tuners;

public interface PhaseTunerLock extends TunerLock {
	float getClock();
	float getPhase();
}
