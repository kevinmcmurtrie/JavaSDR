package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPassIQ;

public class PhaseLock implements PhaseTunerLock {
	private static final boolean enableDebug = true;
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
	
	public static void main (String args[]) throws Exception {
		double sampleRate= 100000;
		double frequency = 100;
		
		PhaseLock fl= new PhaseLock(sampleRate, frequency, 1, 200, true);
		Clock c = new Clock(sampleRate, frequency +50);
		final IQSample src= new IQSample();
		final IQSample out= new IQSample();
		
		for (int i= 0; i < 10000000; ++i) {
			src.setMoment(c.getAndTick());
			fl.accept(src, out);
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

		// Average in two dimensions using an IQ sample. This tolerates high levels of noise and moments of negative
		// amplitude. If phase detection comes before averaging, noise dominates so much that it doesn't average out.
		errorLowPass.accept(out, phaseDetector);
		errorLowPass.accept(previous, frequencyDetector);
		
		//The phase mismatch is very accurate but it can spin to a zero magnitude
		final double phaseMismatchPhase = phaseDetector.phase() * (phaseDetector.magnitude()/frequencyDetector.magnitude());
		//The frequency mismatch is noisy and only useful when the phase mismatch is zero magnitude.
		final double frequencyMismatchPhase = frequencyDetector.phase();
		
		// Fast adjustments to the phase. This adjustment is consumed to prevent bouncing.
		phaseAft= phaseMismatchPhase;
		frequencyAft+= (0.00004 * phaseMismatchPhase + 0.0001d * frequencyMismatchPhase);
		
		phaseDetector.rotate(-phaseAft*0.001);	//Debounce phase correction with forward feedback
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
			vis.markCenter();
			vis.drawIQ(Color.red, src);
			vis.drawIQ(Color.blue, out);

			vis.drawAnalog(Color.gray, 0);
			vis.drawAnalog(Color.cyan,  frequencyMismatchPhase/tauCyclesPerSample);
			vis.drawAnalog(Color.orange, 10*phaseMismatchPhase/tauCyclesPerSample);
			vis.drawAnalog(Color.pink, 0.5*frequencyAft/tauCyclesPerSample);
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
		return (float) previous.phase();
	}

}
