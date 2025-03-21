package us.pixelmemory.kevin.sdr.receivers;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.FloatConsumer;
import us.pixelmemory.kevin.sdr.FloatPairConsumer;
import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.SimplerMath;
import us.pixelmemory.kevin.sdr.firfilters.BandPass;
import us.pixelmemory.kevin.sdr.firfilters.FilterBuilder;
import us.pixelmemory.kevin.sdr.firfilters.IdentityPass;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;
import us.pixelmemory.kevin.sdr.firfilters.LowPass;
import us.pixelmemory.kevin.sdr.firfilters.MultiFilter;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPass;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPassStereo;
import us.pixelmemory.kevin.sdr.tuners.PhaseLock;
import us.pixelmemory.kevin.sdr.tuners.Tuner;

public class FMBroadcast<T extends Throwable> implements FloatConsumer<T> {
	private static final boolean enableDebug = false;

	private static final float pilotFrequency = 19000f;

	private final float pilotStrengthRc = 1f;

	private final Tuner<PhaseLock> pilotTuner;
	private final IQSample pilotIQ = new IQSample();
	private final MultiFilter<T> multifilter;
	private final FloatConsumer<T> rdsOut;

	/**
	 * A higher Lanczos level on the pilot BandPass filter helps with weaker stations. If too high, will lock
	 * on a pilot even if the stereo band is noise.
	 */
	private static final FilterBuilder stereoPilotFilter = new BandPass(LanczosTable.of(5), 18500f, 19500f);
	private static final FilterBuilder monoBandFilter = new LowPass(LanczosTable.of(3), 16000f);
	private static final FilterBuilder stereoBandFilter = new BandPass(LanczosTable.of(3), 23000f, 53000f);
	private static final FilterBuilder rdsBandFilter = new IdentityPass();// RDS has its own filter

	private final RCLowPass pilotStrengthFilter;
	private final RCLowPassStereo<T> deEmphasis;
	private final IQVisualizer vis = enableDebug ? new IQVisualizer(0.1f) : null;

	public FMBroadcast(final float sampleRate, final FloatPairConsumer<T> stereoOut, final FloatConsumer<T> rdsOut) {
		deEmphasis = new RCLowPassStereo<>(sampleRate, 0.000060d, stereoOut);
		pilotTuner = new Tuner<>(new PhaseLock(sampleRate, pilotFrequency, 0.000005d, 0.001, enableDebug), sampleRate, pilotFrequency);
		multifilter = new MultiFilter<>(sampleRate, f -> bandpassIn(f[0], f[1], f[2], f[3]), stereoPilotFilter, monoBandFilter, stereoBandFilter, rdsBandFilter);
		pilotStrengthFilter = new RCLowPass(sampleRate, pilotStrengthRc);
		this.rdsOut = rdsOut;
		if (enableDebug) {
			vis.syncOnColor(Color.gray);
		}
	}

	private void bandpassIn(final float pilot, final float monoBand, final float stereoBand, final float rdsBand) throws T {
		final float pllClock = pilotTuner.accept(12 * pilot, pilotFrequency, pilotIQ).getValue();
		// The tuned pilot should be positive in the quadrature and zero in the in-phase.
		// Calculate the strength of the stereo and scale it gradually. It will naturally fade in and out on weak signals.
		final float pilotStrength = pilotStrengthFilter.apply((float) (4.5 * pilotIQ.quad - 2 * Math.abs(pilotIQ.in)));
		final float stereoStrength = SimplerMath.clamp(pilotStrength - 0.1f, 0, 1);
		final float doubleClock = (float) Math.sin(2 * pllClock);
		final float stereo = stereoStrength * 2 * stereoBand * doubleClock; // L-R

		if (rdsOut != null) {
			// It would be nice to use the 3*pilot to demodulate RDS, but stations violate the requirement that it be in sync.
			rdsOut.accept(rdsBand);
		}

		if (enableDebug) {
			vis.drawAnalog(Color.gray, 0);
			vis.drawAnalog(Color.DARK_GRAY, 1);
			vis.drawAnalog(Color.green, stereoStrength);
			vis.drawAnalog(Color.red, pilotStrength);
			vis.drawAnalog(Color.blue, 2 + monoBand);
			vis.drawAnalog(Color.cyan, 3 + stereoBand);
		}

		deEmphasis.accept(monoBand + stereo, monoBand - stereo);
	}

	@Override
	public void accept(final float f) throws T {
		multifilter.accept(f);
	}
}
