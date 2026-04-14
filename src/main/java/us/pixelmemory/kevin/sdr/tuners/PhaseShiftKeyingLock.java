package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPassIQ;

/**
 * Decode PSK constellations.
 */
public final class PhaseShiftKeyingLock implements TunerLock {
	private static final boolean enableDebug = true;

	private final boolean debug;
	private final IQVisualizer vis;

	private final IQSample previous = new IQSample();
	private final IQSample rotatedPoint = new IQSample();
	private final double samplesPerCycle;
	private final IQSample phaseDetector = new IQSample();
	private final IQSample frequencyDetector = new IQSample();
	private final RCLowPassIQ errorLowPass;
	private final int points;
	private final Clock clock;

	// Need two AFT rates to dampen cycling (the parallel C and RC in every PLL)
	private final double aftLimit;
	private double frequencyAft = 0;
	private double phaseAft = 0;
	private int value = 0;

	public PhaseShiftKeyingLock(final int points, final double sampleRate, final double frequency, final double aftFractionalSpeed, final double aftPercentLimit, final boolean debug) {
		if (points < 1) {
			throw new IllegalArgumentException("at least 1 point");
		}
		clock= new Clock(sampleRate, frequency);
		this.points = points;

		samplesPerCycle = sampleRate / frequency;
		if (samplesPerCycle < 2) {
			throw new IllegalArgumentException("sampleRate is too low");
		}
		final double tauCyclesPerSample = Math.TAU / samplesPerCycle;
		this.aftLimit = aftPercentLimit / tauCyclesPerSample;
		errorLowPass = new RCLowPassIQ(sampleRate,1 / aftFractionalSpeed);

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
		clock= new Clock(sampleRate);
		this.points = points;
		samplesPerCycle = sampleRate;
		this.aftLimit = aftPercentLimit;
		errorLowPass = new RCLowPassIQ(sampleRate, 1 / aftFractionalSpeed);
		this.debug = debug;
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.red);
		}
	}


	@Override
	public double getClockRateAdjustment() {
		return frequencyAft;
	}


	/**
	 * @return Constellation point of the last tuned value
	 */
	public int getValue() {
		return value;
	}

	@Override
	public void accept(final IQSample src, final IQSample out) {
		out.set(src);
		final double c= clock.getAndTick(frequencyAft + phaseAft);
		out.rotate(-c);
		
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
		errorLowPass.accept(rotatedPoint, phaseDetector);
		errorLowPass.accept(previous, frequencyDetector);
		
		//The phase mismatch is very accurate but it can spin to a zero magnitude
		final double phaseMismatchPhase = phaseDetector.phase() * phaseDetector.magnitude();
		//The frequency mismatch is noisy and only useful when the phase mismatch is zero magnitude.
		final double frequencyMismatchPhase = frequencyDetector.phase();
		
		// Fast adjustments to the phase. This adjustment is consumed to prevent bouncing.
		phaseAft= phaseMismatchPhase;
		frequencyAft+= (0.00004 * phaseMismatchPhase + 0.0001d * frequencyMismatchPhase);
		
		phaseDetector.rotate(-phaseAft*0.0001);	//Debounce phase correction with forward feedback
		frequencyDetector.rotate(-frequencyMismatchPhase*0.00002);	//Debounce frequency correction with forward feedback
		frequencyDetector.rotate(phaseAft*0.00001);	//Drain the opposition that builds between the phase and frequency
		

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
			//vis.drawIQ(Color.pink, previous);
			vis.drawAnalog(Color.red, value);
			vis.drawAnalog(Color.cyan, 100*frequencyAft/aftLimit);
			vis.drawAnalog(Color.orange, 100*phaseAft/aftLimit);
			vis.drawIQ(Color.magenta, frequencyDetector);
			vis.drawIQ(Color.blue, out);
			//vis.drawIQ(Color.GREEN, rotatedPoint);
			vis.drawIQ(Color.green, phaseDetector);
			// vis.repaint(); //DEBUG
		}
		previous.set(out);
	}
}
