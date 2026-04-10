package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPassIQ;

public class FrequencyLock implements PhaseTunerLock {
	private static final boolean enableDebug = true;
	private final boolean debug;
	private final IQVisualizer vis;

	// Previous - current is the frequency offset
	private final IQSample previous = new IQSample();
	private final double samplesPerCycle;

	// Need two AFT rates to dampen cycling (the parallel C and RC in every PLL)
	private final double aftLimit;
	private double frequencyAft = 0;
	private float clock = 0;
	private float phaseOffset;

	private final RCLowPassIQ frequencyErrorLowPass;

	private final IQSample frequencyDetector = new IQSample();

	public FrequencyLock(final double sampleRate, final double frequency, final double aftFractionalSpeed, final double frequencyLimit, final boolean debug) {
		samplesPerCycle = sampleRate / frequency;
		if (samplesPerCycle < 2) {
			throw new IllegalArgumentException("sampleRate is too low");
		}
		final double tauCyclesPerSample = Math.TAU / samplesPerCycle;
		this.aftLimit = frequencyLimit / tauCyclesPerSample;
		this.debug = debug;
		frequencyErrorLowPass = new RCLowPassIQ(sampleRate, 1 / aftFractionalSpeed);
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.orange);
		}
	}

	public FrequencyLock(final double sampleRate, final double aftFractionalSpeed, final double frequencyLimit, final boolean debug) {
		samplesPerCycle = 1;
		final double tauCyclesPerSample = Math.TAU / samplesPerCycle;
		this.aftLimit = frequencyLimit / tauCyclesPerSample;
		this.debug = debug;
		frequencyErrorLowPass = new RCLowPassIQ(sampleRate, 1 / aftFractionalSpeed);
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.cyan);
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
	public void accept(final IQSample src, final IQSample out, final double clock) {
		previous.conjugate();
		previous.multiply(out);
		previous.rotateRight();
		phaseOffset = (float) previous.phase();

		// Average in two dimensions using an IQ sample. This tolerates high levels of noise and moments of negative
		// amplitude. If phase detection comes before averaging, noise dominates so much that it doesn't average out.
		frequencyErrorLowPass.accept(previous, frequencyDetector);

		final double frequencyMismatchPhase = frequencyDetector.phase() * frequencyDetector.magnitude();

		// Fast adjustments to the phase. This adjustment is consumed to prevent bouncing.

		// Very slow adjustments to the frequency. This one is extremely delicate.
		frequencyAft += 0.0002f * frequencyMismatchPhase / samplesPerCycle;

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
			vis.markCenter();
			vis.drawIQ(Color.red, src);
			vis.drawAnalog(Color.gray, 0);
			vis.drawAnalog(Color.cyan, 10 * samplesPerCycle * frequencyAft);
			vis.drawAnalog(Color.orange, phaseOffset * frequencyAft);

			vis.drawIQ(Color.magenta, frequencyDetector);
			vis.drawIQ(Color.blue, out);

			// vis.repaint();// only interactive debugging
		}
		previous.set(out);

		this.clock = (float) clock;
	}

	@Override
	public float getClock() {
		return clock;
	}

	@Override
	public float getPhase() {
		return phaseOffset;
	}
}
