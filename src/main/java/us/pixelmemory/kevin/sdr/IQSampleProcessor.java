package us.pixelmemory.kevin.sdr;

@FunctionalInterface
public interface IQSampleProcessor<T extends Throwable, SIDE> {
	SIDE accept(IQSample in, IQSample out) throws T;
}