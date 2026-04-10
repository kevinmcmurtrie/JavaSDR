package us.pixelmemory.kevin.sdr;

@FunctionalInterface
public interface FloatPairFunction<T extends Throwable> {
	float apply(float a, float b) throws T;
	}
