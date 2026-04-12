package us.pixelmemory.kevin.sdr;

@FunctionalInterface
public interface IQSampleProcessor<T extends Throwable> {
	void accept(IQSample in, IQSample out) throws T;
}