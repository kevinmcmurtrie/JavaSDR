package us.pixelmemory.kevin.sdr;

@FunctionalInterface
public interface FloatFunction<T extends Throwable> {
	float apply(float f) throws T;

}
