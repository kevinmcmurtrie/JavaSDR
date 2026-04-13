package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPassIQ;

public class PhaseLock implements PhaseTunerLock {
	private static final boolean enableDebug = false;
	private final boolean debug;
	private final IQVisualizer vis;

	// Previous - current is the frequency offset
	private final IQSample previous = new IQSample();

	// Need two AFT rates to dampen cycling (the parallel C and RC in every PLL)
	private final double aftLimit;
	private final double tauCyclesPerSample;

	private double frequencyAft = 0;
	private double phaseAft = 0;
	private final Clock clock;
	private float phaseOffset;

	private final RCLowPassIQ errorLowPass;

	private final IQSample phaseDetector = new IQSample();
	private final IQSample frequencyDetector = new IQSample();

	public PhaseLock(final double sampleRate, final double frequency, final double aftFractionalSpeed, final double frequencyLimit, final boolean debug) {
		clock= new Clock(sampleRate, frequency);
		double samplesPerCycle = sampleRate / frequency;
		if (samplesPerCycle < 2) {
			throw new IllegalArgumentException("sampleRate is too low");
		}
		tauCyclesPerSample = Math.TAU / samplesPerCycle;
		this.aftLimit = frequencyLimit / tauCyclesPerSample;
		this.debug = debug;
		errorLowPass = new RCLowPassIQ(sampleRate, 1 / aftFractionalSpeed);
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.orange);
		}
	}

	public PhaseLock(final double sampleRate, final double aftFractionalSpeed, final double frequencyLimit, final boolean debug) {
		clock= new Clock(sampleRate, 0);
				
		tauCyclesPerSample = Math.TAU / sampleRate;
		this.aftLimit = frequencyLimit * tauCyclesPerSample;
		this.debug = debug;
		errorLowPass = new RCLowPassIQ(sampleRate, 1 / aftFractionalSpeed);
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.orange);
		}
	}

	@Override
	public double getClockRateAdjustment() {
		return frequencyAft/tauCyclesPerSample;
	}

	@Override
	public void accept(final IQSample src, final IQSample out) {
		out.set(src);
		final double c= clock.getAndTick(frequencyAft + phaseAft);
		out.rotate(-c);
		
		previous.conjugate();
		previous.multiply(out);
		previous.rotateRight();
		phaseOffset = (float) previous.phase();

		// Average in two dimensions using an IQ sample. This tolerates high levels of noise and moments of negative
		// amplitude. If phase detection comes before averaging, noise dominates so much that it doesn't average out.
		errorLowPass.accept(out, phaseDetector);
		errorLowPass.accept(previous, frequencyDetector);
		
		
		//The phase mismatch is very accurate but it can spin to a zero magnitude
		final double phaseMismatchPhase = phaseDetector.phase() * phaseDetector.magnitude();
		//The frequency mismatch is noisy and only useful when the phase mismatch is zero magnitude.
		final double frequencyMismatchPhase = frequencyDetector.phase();

		final double phaseFix= phaseMismatchPhase;
		final double frequencyFix= (0.00004 * phaseMismatchPhase + 0.00001d * frequencyMismatchPhase);
		
		// Fast adjustments to the phase. This adjustment is consumed to prevent bouncing.
		phaseAft= phaseFix;
		frequencyAft+= frequencyFix;
		//frequencyAft+= frequencyMismatchPhase/100000f;
		
		phaseDetector.rotate(-phaseFix*0.0001);	//Debounce phase correction with forward feedback
		frequencyDetector.rotate(-frequencyMismatchPhase*0.000002);	//Debounce frequency correction with forward feedback
		frequencyDetector.rotate(phaseFix*0.00001);	//Drain the opposition that builds between the phase and frequency
		
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
			vis.drawIQ(Color.blue, out);

			vis.drawAnalog(Color.gray, 0);
			vis.drawAnalog(Color.cyan, 0.1* frequencyMismatchPhase/tauCyclesPerSample);
			vis.drawAnalog(Color.orange, 0.1* phaseMismatchPhase/tauCyclesPerSample);
			vis.drawAnalog(Color.pink, frequencyAft/tauCyclesPerSample);
			vis.drawIQ(Color.magenta, frequencyDetector);
			vis.drawIQ(Color.green, phaseDetector);

			// vis.repaint();// only interactive debugging
		}
		previous.set(out);
	}

	@Override
	public double getClock() {
		return clock.getClock();
	}

	@Override
	public float getPhase() {
		return phaseOffset;
	}

}
