package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.FloatFunction;
import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.SimplerMath;
import us.pixelmemory.kevin.sdr.firfilters.RunningAverage;
import us.pixelmemory.kevin.sdr.firfilters.SingleFilter;

/**
 * Decode PSK constellations.
 */
public final class PhaseShiftKeyingLock implements TunerLock {
	private static final boolean enableDebug = true;

	private final boolean debug;
	private final IQVisualizer vis;

	private final IQSample previous = new IQSample();
	private final double frequencyAlignmentSpeed;
	private final FloatFunction<RuntimeException> frequencyMismatchFilter;
	private final FloatFunction<RuntimeException> phaseMismatchFilter;
	private final int points;

	// Need two AFT rates to dampen cycling (the parallel C and RC in every PLL)
	private final double aftLimit;
	private double frequencyAft = 0;
	private double phaseAft = 0;
	private int value = 0;

	public PhaseShiftKeyingLock(final int points, final double sampleRate, final double frequency, final double aftFractionalSpeed, final double aftPercentLimit, final boolean debug) {
		if (points < 1) {
			throw new IllegalArgumentException("at least 1 point");
		}
		this.points = points;

		final double samplesPerCycle = sampleRate / frequency;
		if (samplesPerCycle < 2) {
			throw new IllegalArgumentException("sampleRate is too low");
		}
		final double tauCyclesPerSample = Math.TAU / samplesPerCycle;
		this.frequencyAlignmentSpeed = aftFractionalSpeed;
		this.aftLimit = aftPercentLimit / tauCyclesPerSample;
		frequencyMismatchFilter = (aftLimit != 0) ? new SingleFilter((float) samplesPerCycle, new RunningAverage(200)) : f -> f;
		phaseMismatchFilter = (aftLimit != 0) ? new SingleFilter((float) samplesPerCycle, new RunningAverage(64)) : f -> f;
		this.debug = debug;
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.pink);
		}
	}

	public PhaseShiftKeyingLock(final int points, final double sampleRate, final double aftFractionalSpeed, final double aftPercentLimit, final boolean debug) {
		if (points < 1) {
			throw new IllegalArgumentException("at least 1 point");
		}
		this.points = points;

		this.frequencyAlignmentSpeed = aftFractionalSpeed;
		this.aftLimit = aftPercentLimit;
		frequencyMismatchFilter = (aftLimit != 0) ? new SingleFilter(1, new RunningAverage(200)) : f -> f;
		phaseMismatchFilter = (aftLimit != 0) ? new SingleFilter(1, new RunningAverage(64)) : f -> f;
		this.debug = debug;
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.pink);
		}
	}

	@Override
	public double getAFTLimit() {
		return aftLimit;
	}

	@Override
	public double getClockRateAdjustment() {
		return frequencyAft + phaseAft;
	}

	/**
	 * @return Constellation point of the last tuned value
	 */
	public int getValue() {
		return value;
	}

	@Override
	public void accept(final IQSample src, final IQSample out, final double clock) {
		previous.conjugate();
		previous.multiply(out);
		previous.rotateRight();
		final float frequencyMismatchPhaseRaw = (float) previous.phase();

		final float phase = (float) out.phase();
		final int point = (int) Math.round((phase / Math.TAU) * points);
		final float expectedPhase = ((float) Math.TAU / points) * point;
		final float errorPhase = (float) out.magnitude() * (phase - expectedPhase); // Don't use samples that pass through 0,0 because their phase is noise.
		final float staticMismatchPhase = phaseMismatchFilter.apply(TunerLock.unwrapPhaseDistance(errorPhase));
		final float frequencyMismatchPhase = frequencyMismatchFilter.apply(SimplerMath.clamp(frequencyMismatchPhaseRaw, -1f, 1f));

		// Magic numbers here. Hand-tweaked for stability.
		// A small phase error disables the frequency phase error so noise can't kick it off sync.
		phaseAft = phaseAft * 0.99 + 0.02 * staticMismatchPhase * frequencyAlignmentSpeed;
		frequencyAft += (phaseAft + Math.abs(phaseAft) * frequencyMismatchPhase) * frequencyAlignmentSpeed;

		if (frequencyAft > aftLimit) {
			System.out.println("AFT limit: " + frequencyAft);
			frequencyAft = aftLimit;
		}
		if (frequencyAft < -aftLimit) {
			System.out.println("AFT limit: " + frequencyAft);
			frequencyAft = -aftLimit;
		}

		value = (points / 2 + point) % points;
		if (enableDebug && debug) {
			vis.markCenter();
			// vis.drawIQ(Color.red, src);
			vis.drawAnalog(Color.red, 6 + src.in);
			vis.drawAnalog(Color.pink, 4.5 + 2 * value / (float) points);

			vis.drawAnalog(Color.CYAN, 100 * frequencyAft / aftLimit);
			vis.drawAnalog(Color.orange, 100 * phaseAft / aftLimit);
			vis.drawIQ(Color.magenta, previous);
			vis.drawIQ(Color.blue, out);
			vis.drawAnalog(Color.green, errorPhase / Math.PI);

			// try {
			// vis.repaint();
			// Thread.sleep(100);
			// } catch (InterruptedException e) {
			// // TODO Auto-generated catch block
			// e.printStackTrace();
			// }
		}
		previous.set(out);
	}
}
