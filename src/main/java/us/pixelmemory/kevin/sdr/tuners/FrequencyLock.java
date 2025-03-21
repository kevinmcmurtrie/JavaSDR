package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPassIQ;

public final class FrequencyLock implements AnalogTunerLock {
	private static final boolean enableDebug = false;
	private final boolean debug;
	private final IQVisualizer vis;

	// Previous - current is the frequency offset
	private final IQSample previous = new IQSample();

	// Need two AFT rates to dampen cycling (the parallel C and RC in every PLL)
	private final double aftLimit;
	private double frequencyAft = 0;
	private double phaseAft = 0;
	private float frequencyMismatchPhase;

	private final RCLowPassIQ tuningLowPass;
	private final IQSample frequencyDetector = new IQSample();

	public FrequencyLock(final double aftFractionalSpeed, final double aftPercentLimit, final boolean debug) {
		this.aftLimit = aftPercentLimit;
		this.debug = debug;
		tuningLowPass = new RCLowPassIQ(1 / aftFractionalSpeed);
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
		return frequencyAft + phaseAft;
	}

	@Override
	public float getValue() {
		return frequencyMismatchPhase;
	}

	@Override
	public void accept(final IQSample src, final IQSample out, final double clock) {
		previous.conjugate();
		previous.multiply(out);
		previous.rotateRight();

		// Average in two dimensions using an IQ sample. This tolerates high levels of noise and moments of negative
		// amplitude. If phase detection comes before averaging, noise dominates so much that it doesn't average out.
		tuningLowPass.accept(previous, frequencyDetector);
		frequencyMismatchPhase = (float) previous.phase();

		phaseAft = (float) frequencyDetector.phase();

		// Very slow adjustments to the frequency. This one is extremely delicate.
		frequencyAft += 0.00002f * phaseAft;

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
			vis.drawAnalog(Color.cyan, 100 * frequencyAft / aftLimit);
			vis.drawAnalog(Color.orange, 100 * phaseAft / aftLimit);
			vis.drawIQ(Color.magenta, frequencyDetector);
			vis.drawIQ(Color.blue, out);
			vis.drawAnalog(Color.white, frequencyMismatchPhase);
		}
		previous.set(out);
	}
}
