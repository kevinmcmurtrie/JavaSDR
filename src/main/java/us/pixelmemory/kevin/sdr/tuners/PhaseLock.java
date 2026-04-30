package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.SimplerMath;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPassIQ;

public class PhaseLock implements PhaseTunerLock {
	private static final boolean enableDebug = true;
	private final boolean debug;
	private final IQVisualizer vis;

	// Previous - current is the frequency offset
	private final IQSample previous = new IQSample();

	// Need two AFT rates to dampen cycling (the parallel C and RC in every PLL)
	private final float aftLimit;
	private final float tauCyclesPerSample;
	private final int points;

	private float frequencyAft = 0;
	private float phaseAft = 0;
	private int currentPoint = 0;
	private final Clock clock;

	private final RCLowPassIQ errorFastLowPass;
	private final RCLowPassIQ errorSlowLowPass;

	private final IQSample phaseDetectorSlow = new IQSample(0,0);//Normal running phase detector.  Let accumulate.
	private final IQSample lockQuality = new IQSample(0,0); //Normal running phase detector, but without debounce
	private final IQSample phaseDetectorFast = new IQSample(1, 0);	//Used when the slow one doesn't catch. Start off strong.
	private final IQSample frequencyDetector = new IQSample(0.5f, 0);		//Used when no phase detector catches.  Start off hopeful of an easy lock.

	
	public PhaseLock(final float sampleRate, final float frequency, final float aftFractionalSpeed, final float frequencyLimit, int points, final boolean debug) {
		clock= new Clock(sampleRate, frequency);
		this.points = Math.max(points, 1);
		float samplesPerCycle = sampleRate / frequency;
		if (samplesPerCycle < 2) {
			throw new IllegalArgumentException("sampleRate is too low");
		}
		tauCyclesPerSample = (float)(Math.TAU / samplesPerCycle);
		aftLimit = (float) (frequencyLimit * Math.TAU / sampleRate);

		this.debug = debug;
		errorSlowLowPass = new RCLowPassIQ(sampleRate, 2 / aftFractionalSpeed);
		errorFastLowPass = new RCLowPassIQ(sampleRate, 0.5 / aftFractionalSpeed);
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.orange);
		}
	}
	
	public PhaseLock(final float sampleRate, final float aftFractionalSpeed, final float frequencyLimit, int points, final boolean debug) {
		clock= new Clock(sampleRate, 0);
		this.points = Math.max(points, 1);
		tauCyclesPerSample = (float)(Math.TAU / sampleRate);
		aftLimit = (float) (frequencyLimit * Math.TAU / sampleRate);
		this.debug = debug;
		errorSlowLowPass = new RCLowPassIQ(sampleRate, 1 / aftFractionalSpeed);
		errorFastLowPass = new RCLowPassIQ(sampleRate, 0.1 / aftFractionalSpeed);
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.orange);
		}
	}
	
	public static void main (String args[]) throws Exception {
		float sampleRate= 200000;
		float frequency = 10000;
		
		PhaseLock fl= new PhaseLock(sampleRate, frequency, 10f, 1000f, 1, true);
		fl.vis.syncOnColor(new Color(1,2,3));
		Clock c = new Clock(sampleRate, frequency-400);
		final IQSample src= new IQSample();
		final IQSample out= new IQSample();
		
		for (int i= 0; i < 10000000; ++i) {
			src.setMoment((float)c.getAndTick(0.01d * (Math.random()-0.5)));
			fl.accept(src, out);
			if (i % 10 == 0) {
				fl.vis.repaint();
				//Thread.sleep(1);
				fl.vis.fadeLight();
			}
		}
		
	}

	@Override
	public float getClockRateAdjustment() {
		return frequencyAft/tauCyclesPerSample;
	}

	@Override
	public void accept(final IQSample src, final IQSample out) {
		out.set(src);
		out.rotate((float)(-clock.getAndTick(frequencyAft + phaseAft)));
		
		if (enableDebug && debug) {
			vis.drawIQ(Color.blue, out);
		}
		
		if (points > 1) {
			final float phase = out.phase();
			currentPoint = (int) Math.round((phase / Math.TAU) * points);
			if (currentPoint < 0) {
				currentPoint += points;
			}	
			out.rotate((float)((currentPoint * Math.TAU) / points));
		}
		
		previous.conjugate();
		previous.multiply(out);

		// Average in two dimensions using an IQ sample. This tolerates high levels of noise and moments of negative
		// amplitude. If phase detection comes before averaging, noise dominates so much that it doesn't average out.
		errorSlowLowPass.accept(out, phaseDetectorSlow);
		errorFastLowPass.accept(out, lockQuality);
		errorFastLowPass.accept(out, phaseDetectorFast);
		errorSlowLowPass.accept(previous, frequencyDetector);
				
		final float slowLockStrength= SimplerMath.clamp((1 - points) + points * 2*phaseDetectorSlow.magnitude(), 0, 1);
		final float fastLockStrength= SimplerMath.clamp((1 - points) + points * 2*phaseDetectorFast.magnitude(), 0.2f, 1);
		
		final float slowPhaseError= phaseDetectorSlow.phase();
		final float fastPhaseError= phaseDetectorFast.phase();
		
		final float phaseError = (slowLockStrength * slowPhaseError) + (1-slowLockStrength)*(fastLockStrength*fastPhaseError);
		final float frequencyError = (1-slowLockStrength)*(1-fastLockStrength)*frequencyDetector.phase();

		
		// Fast adjustments to the phase. This adjustment is consumed to prevent bouncing.
		phaseAft= phaseError;
		frequencyAft+= (0.000001f * frequencyError + 0.0001f * phaseError);
		
		//Debounce
		phaseDetectorFast.rotate(-fastPhaseError*0.005f);//0.008f
		phaseDetectorSlow.rotate(-slowPhaseError*0.004f);//0.0004f
		frequencyDetector.rotate(-frequencyError*0.0001f);//0.00001f
		phaseDetectorFast.rotate(frequencyError* 0.01f);//0.05f
		
//		phaseDetectorFast.rotate(-phaseError*0.008f);//0.008f
//		phaseDetectorSlow.rotate(-phaseError*0.0004f);//0.0004f
//		frequencyDetector.rotate(-frequencyError*0.00001f);//0.00001f
//		phaseDetectorFast.rotate(frequencyError* 0.05f);//0.05f
		
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

			vis.drawAnalog(Color.cyan, currentPoint);
			
			vis.drawAnalog(Color.gray, 0);
			//vis.drawAnalog(Color.cyan, 10*frequencyError/tauCyclesPerSample);
			vis.drawAnalog(Color.orange, 100*phaseAft/tauCyclesPerSample);
			vis.drawAnalog(Color.pink, 1*frequencyAft/tauCyclesPerSample);
			
			vis.drawIQ(Color.magenta, frequencyDetector);
			vis.drawIQ(Color.green, phaseDetectorSlow);
			vis.drawIQ(Color.yellow, phaseDetectorFast);
		}
		previous.set(out);
	}

	@Override
	public double getClock() {
		return clock.getClock();
	}
	
	/**
	 * @return Constellation point of the last tuned currentPoint
	 */
	public int getLastPoint() {
		return currentPoint;
	}

	@Override
	public float getPhase() {
		return previous.phase();
	}
	
	public float getLockQuality () {
		return SimplerMath.clamp(2f * lockQuality.in - 10f * Math.abs(lockQuality.quad), 0f, 1f);
	}

}
