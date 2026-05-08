package us.pixelmemory.kevin.sdr.rds;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.SimplerMath;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;
import us.pixelmemory.kevin.sdr.firfilters.LowPass;
import us.pixelmemory.kevin.sdr.firfilters.QuarterWaveDelay;
import us.pixelmemory.kevin.sdr.firfilters.SingleFilter;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPass;
import us.pixelmemory.kevin.sdr.resamplers.DownsamplerArray;
import us.pixelmemory.kevin.sdr.tuners.ScaledClock;


public final class RDSDecoder {
	public static final float pilotFrequency = 19000f;
	public static final float rdsBandFrequency = pilotFrequency * 3f;
	public static final float rdsClockRate = 1187.5f;
	public static final float rdsLowPass = 2100; // 2400Hz is the specification
	public static final float ifSampleRate = 14250f;//pilotFrequency;
	private static final boolean debugVisualEnable = true;
	private static final boolean debugDecodeEnable = true;
	private static final boolean liveDebug = false;

	private static final float rdsRate = 1187.5f; // This is the data rate. The encoding is double-rate.
	
	private final DownsamplerArray<RuntimeException> ifDownsampler;
	private final float[] downsamplerArray= new float[3];
	private final QuarterWaveDelay loopNotchFilter1;
	private final QuarterWaveDelay loopNotchFilter2;
	private final ScaledClock rdsDataClock;

	private final SingleFilter rdsLowPassFilter;
	private final DifferentialManchesterDecoder<RuntimeException> diffManchesterDecoder;
	private final RCLowPass gainLowPass;

	private double lastRdsClock = 0d;
	private double rdsPhaseAdj = 0d;
	private double integrator = 0d;

	private int register = 0; // 26 bits, 16 data + 10 CRC
	private int registerSize = 0;
	private RDS_CRC.Offset offsetBlock = null;
	private int lockCount = 0;
	private static final int lockThreshold = 4;
	private static final int lockCountLimit = 47;

	private boolean versionA = true; // Block Ca if true, Cb if false. Applies only to the current group.
	private int blockA; // Program identification code
	private int blockB; // Group type, A/B version, traffic, program type, and data
	private int blockC; // data (Ca or Cb, depending on version)
	private int blockD; // data
	private final ProgramInformation programInfo = new ProgramInformation();

	private final IQVisualizer vis;

	public RDSDecoder(final float sampleRate) {
		rdsDataClock = new ScaledClock(rdsClockRate / pilotFrequency);
		
		ifDownsampler = new DownsamplerArray<>(LanczosTable.of(4), sampleRate, ifSampleRate, 3, this::acceptIfSamples);
		
		rdsLowPassFilter = new SingleFilter(ifSampleRate, new LowPass(LanczosTable.of(4), rdsLowPass));

		// These time delays cancel out strong the 2x and 4x frequency harmonics in the phase error calculation.
		// This is much more efficient than a low pass filter.
		loopNotchFilter1 = new QuarterWaveDelay(ifSampleRate, rdsRate);
		loopNotchFilter2 = new QuarterWaveDelay(ifSampleRate, rdsRate * 2f);

		// Step 7. Differential Manchester Decoding on the stream of 1 and 0. 01 and 10 -> true, 00 and 11 -> false.
		diffManchesterDecoder = new DifferentialManchesterDecoder<>(false);

		vis = debugVisualEnable ? new IQVisualizer(4) : null;
		if (debugVisualEnable && !liveDebug) {
			vis.syncOnColor(Color.red);
		}

		//AGC is somehow very sensitive.  Some stations have massive AM.
		gainLowPass = new RCLowPass(ifSampleRate, 0.001); //0.0002 for 98.5
		gainLowPass.setValue(0.01);
	}

	/**
	 * Make some clocks, tune RDS from the 57kHz modulation, and then downsample everything.  The source sample rate is potentially extremely high.
	 * @param f
	 * @param pilotClock
	 */
	public void accept(final float f, final double pilotClock) {
		final double demodClock= rdsDataClock.tickAndGet(pilotClock, rdsPhaseAdj);
		downsamplerArray[0]=  (float) (f * Math.sin(3 * pilotClock)); //Raw demodulation
		downsamplerArray[1]=(float)Math.sin(demodClock); 		//Demod carrier
		downsamplerArray[2]=(float)Math.sin(2 * demodClock);	//Phase lock double carrier
		ifDownsampler.accept(downsamplerArray);
	}
	
	/**
	 * RDS recommends an "integrate and dump" to fix artifacts from BPSK with fast transitions. The fast
	 * transitions create frequencies that are not sent by all stations. Integration recovers them.
	 * The "dump" is sending the integration to the output and resetting it to zero.
	 * A simplified Costas loop finds the pilot phase offset needed for perfect "dump" timing.
	 * The 57 kHz BPSK modulation is 3 times the 19kHz pilot, and the 1187.5 Hz BPSK clock is 1/16 the pilot.
	 * Demodulate the 57kHz (19kHz * 3) signal, low pass filter, and AGC.
	 */
	private void acceptIfSamples (final float f[]) {
		final float rawDemodulation= f[0];
		final float demodCarrier= f[1];
		final float phaseLockDoubleCarrier= f[2];
		
		final float modulationNoAGC = rdsLowPassFilter.apply(rawDemodulation);
		final float modulation = 0.5f * modulationNoAGC / SimplerMath.clamp(gainLowPass.apply(Math.abs(modulationNoAGC)), 0.000001f, 100f);
		
		//Remove the modulation from the encoded signal with the 1187.5 Hz clock.
		final float signal = modulation * demodCarrier;

		// The bounce after a phase shift may be weak, missing, or even slightly inverted on some stations.
		// Integration fixes it.
		integrator += signal;
		if ((demodCarrier <= 0) && (lastRdsClock > 0)) {
			// Rising tick. Send integrated signal and reset.
			acceptBinaryBit(diffManchesterDecoder.apply(integrator >= 0));
			integrator = 0;
		}
		lastRdsClock = demodCarrier;

		// Costas loop. Phase shift the carrier-follower clock.
		// This loop is simplified because intermediate high frequencies are OK with so much oversampling.
		// This phase locks at 2x frequency (2x clock and signal^2) so that 180 degree phase inversions at 1x frequency become irrelevant 360 degree inversions.
		final float feedback = modulation * modulation * phaseLockDoubleCarrier;
		final float notchedFeedback1 = feedback + loopNotchFilter1.delay(feedback);
		final float notchedFeedback2 = notchedFeedback1 + loopNotchFilter2.delay(notchedFeedback1);
		rdsPhaseAdj = 0.00007d * notchedFeedback2 / (lockCount +1);
		
		if (debugVisualEnable) {
			if (liveDebug) {
				vis.fadeLight();
			}
			
			vis.drawAnalog(Color.cyan, lockCount / 40f);

			vis.drawAnalog(Color.red, modulation);
			vis.drawAnalog(Color.orange, 2f + signal);
			vis.drawAnalog(Color.yellow, 5f + 0.2f*(float) integrator);
			vis.drawAnalog(Color.blue, 5f + (float) (4000 * rdsPhaseAdj));
			vis.drawAnalog(Color.darkGray, 5f);

			vis.drawAnalog(Color.lightGray, demodCarrier);

			if (liveDebug) {
				vis.repaint();
			}
		}
	}

	/**
	 * Accept a binary bit of the demodulated RDS data stream
	 *
	 * @param value bit
	 */
	private void acceptBinaryBit(final boolean value) {
		// Load up the register
		register = (register << 1) | (value ? 0 : 1);
		registerSize++;

		if (registerSize >= 26) {
			// Figure out ABCD block sync using the injected CRC error.
			// An error-free signal is needed to get started.
			final var currentOffset = nextOffset();
			final int syndrome = RDS_CRC.extractSyndrome(register);
			if (currentOffset.syndrome == syndrome) {
				// A good register
				lockCount = Math.min(lockCount + 1, lockCountLimit);
				offsetBlock = currentOffset;
				registerSize = 0; // Consume
				if (debugVisualEnable) {
					System.out.println("RDS good");
				}
			} else {
				// A bad register
				lockCount = Math.max(lockCount - 1, 0);
				if (lockCount >= lockThreshold) {
					// System.out.println("RDS bad");
					// Keep going. Maybe it will correct.
					offsetBlock = currentOffset;
					registerSize = 0; // Consume
					if (debugVisualEnable) {
						System.out.println("RDS bad, continue");
					}
				} else {
					if (debugVisualEnable) {
						System.out.println("RDS search");
					}
					// Search again
					offsetBlock = null;
				}
			}

			if (offsetBlock != null) {
				if (debugDecodeEnable) {
					System.out.println(offsetBlock);
				}
				
				acceptBlock(RDS_CRC.errorCorrect(register, offsetBlock.offsetWord));
			}
		}
	}

	/**
	 * Process a block register
	 *
	 * @param word Error corrected data for the current block
	 */
	private void acceptBlock(final int word) {
		if (debugVisualEnable && (word & RDS_CRC.possibleErrorFlag) != 0) {
			System.out.println("Possible error");
		}

		switch (offsetBlock) {
			case A -> {
				if ((word & RDS_CRC.possibleErrorFlag) != 0) {
					// Use last value if there may be an error
					blockA = word;
				}
			}
			case B -> {
				blockB = word;
				versionA = (word & (1 << 11)) == 0; // Need this to decode the next block
			}
			case Ca, Cb -> blockC = word;
			case D -> {
				blockD = word;
				programInfo.accept(blockA, blockB, blockC, blockD);
				if (debugDecodeEnable) {
					System.out.println(programInfo);
				}
			}
			default -> {
			}
		}
	}

	private RDS_CRC.Offset nextOffset() {
		if (offsetBlock == RDS_CRC.Offset.A) {
			return RDS_CRC.Offset.B;
		}
		if (offsetBlock == RDS_CRC.Offset.B) {
			return versionA ? RDS_CRC.Offset.Ca : RDS_CRC.Offset.Cb;
		}
		if ((offsetBlock == RDS_CRC.Offset.Ca) || (offsetBlock == RDS_CRC.Offset.Cb)) {
			return RDS_CRC.Offset.D;
		}
		return RDS_CRC.Offset.A;
	}

}
