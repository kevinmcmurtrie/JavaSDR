package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPassIQ;

/**
 * Decode PSK constellations.
 */
public final class PhaseShiftKeyingLock implements TunerLock {
	private static final boolean enableDebug = false;

	private final boolean debug;
	private final IQVisualizer vis;

	private final IQSample previous = new IQSample();
	private final IQSample rotatedPoint = new IQSample();
	private final double samplesPerCycle;
	private final IQSample phaseDetector = new IQSample();
	private final IQSample frequencyDetector = new IQSample();
	private final RCLowPassIQ tuningLowPass;
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

		samplesPerCycle = sampleRate / frequency;
		if (samplesPerCycle < 2) {
			throw new IllegalArgumentException("sampleRate is too low");
		}
		final double tauCyclesPerSample = Math.TAU / samplesPerCycle;
		this.aftLimit = aftPercentLimit / tauCyclesPerSample;
		tuningLowPass = new RCLowPassIQ(1 / aftFractionalSpeed);

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
		samplesPerCycle = sampleRate;
		this.aftLimit = aftPercentLimit;
		tuningLowPass = new RCLowPassIQ(1 / aftFractionalSpeed);
		this.debug = debug;
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.red);
		}
	}

	@Override
	public double getAFTLimit() {
		return aftLimit;
	}

	@Override
	public double getClockRateAdjustment() {
		return frequencyAft;
	}

	@Override
	public double consumeClockRateAdjustment() {
		final double adj = frequencyAft + phaseAft;
		phaseDetector.rotate(-phaseAft / 2);
		phaseAft = 0;
		return adj;
	}

	/**
	 * @return Constellation point of the last tuned value
	 */
	public int getValue() {
		return value;
	}

	@Override
	public void accept(final IQSample src, final IQSample out, final double clock) {
		final float phase = (float) out.phase();
		value = (int) Math.round((phase / Math.TAU) * points);
		if (value < 0) {
			value += points;
		}
		rotatedPoint.set(out);
		rotatedPoint.rotate((value * Math.TAU) / points);

		previous.conjugate();
		previous.multiply(out);
		previous.rotateRight();

		// Average in two dimensions using an IQ sample. This tolerates high levels of noise and moments of negative
		// amplitude. If phase detection comes before averaging, noise dominates so much that it doesn't average out.

		tuningLowPass.accept(rotatedPoint, phaseDetector);
		tuningLowPass.accept(previous, frequencyDetector);

		final double staticMismatchPhase = phaseDetector.phase() * phaseDetector.magnitude();
		final double frequencyMismatchPhase = frequencyDetector.phase() * frequencyDetector.magnitude();

		// Fast adjustments to the phase. This adjustment is consumed to prevent bouncing.
		phaseAft = frequencyMismatchPhase + staticMismatchPhase;

		// Slower adjustments to the frequency.
		frequencyAft += phaseAft / samplesPerCycle;

		if (frequencyAft > aftLimit) {
			if (enableDebug) {
				System.out.println("AFT limit: " + frequencyAft);
			}
			frequencyAft = aftLimit;
		}
		if (frequencyAft < -aftLimit) {
			if (enableDebug) {
				System.out.println("AFT limit: " + frequencyAft);
			}
			frequencyAft = -aftLimit;
		}

		if (enableDebug && debug) {
			// vis.fadeStrong(); //DEBUG
			vis.markCenter();
			vis.drawIQ(Color.pink, previous);
			vis.drawAnalog(Color.red, value);
			vis.drawAnalog(Color.cyan, 100 * frequencyAft / aftLimit);
			vis.drawAnalog(Color.orange, 100 * phaseAft / aftLimit);
			vis.drawIQ(Color.magenta, frequencyDetector);
			vis.drawIQ(Color.blue, out);
			vis.drawIQ(Color.green, phaseDetector);
			// vis.repaint(); //DEBUG
		}
		previous.set(out);
	}
}
