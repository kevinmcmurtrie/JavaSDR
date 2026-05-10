package us.pixelmemory.kevin.sdr.rds;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.SimplerMath;
import us.pixelmemory.kevin.sdr.firfilters.BandPass;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;
import us.pixelmemory.kevin.sdr.firfilters.QuarterWaveDelay;
import us.pixelmemory.kevin.sdr.firfilters.SingleFilter;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPass;
import us.pixelmemory.kevin.sdr.resamplers.DownsamplerArray;
import us.pixelmemory.kevin.sdr.tuners.ScaledClock;


public final class RDSDecoder {
	private static final boolean debugVisualEnable = true;
	private static final boolean debugDecodeEnable = true;
	private static final boolean liveDebug = true;
	
	//Parameters to recover as much signal as possible.
	//If you think 38kHz modulated stereo sounds bad, wait until you see the RDS signal at 57kHz.
	public static final float pilotFrequency = 19000f;
	public static final float rdsBandFrequency = pilotFrequency * 3f;
	public static final float rdsClockRate = 1187.5f;
	public static final float rdsLowLimit = 200;
	public static final float rdsHighLimit = 2200; // 2400Hz is the specification but the high end is noisy.
	public static final float ifSampleRate = pilotFrequency;//pilotFrequency;
	private static final double gainRC= 0.0001;				//Gain AGC low pass.  Used for dynamic range compression.
	private static final double clockUpDownSelectRC= 0.01;	//Low-pass on selecting up or down clock edge

	
	private final DownsamplerArray<RuntimeException> ifDownsampler;	//Intermediate frequency downsampler
	private final float[] downsamplerParametersArray= new float[3];
	
	private final QuarterWaveDelay loopNotchFilter1;
	private final QuarterWaveDelay loopNotchFilter2;
	private final ScaledClock rdsDataClock;		//RDS synced to pilot clock

	private final SingleFilter rdsBandpassFilter;
	private final DifferentialManchesterDecoder<RuntimeException> diffManchesterDecoder;
	private final RCLowPass gainLowPass;
	private double clockUpIntegration;
	private double clockDownIntegration;
	private final RCLowPass clockUpDownLowPass;
	
	private double lastRdsClock = 0d;	//For edge detection
	private double rdsPhaseAdj = 0d;	//Phase alignment adjustments

	private int register = 0; // RDS input register.  26 bits, 16 data + 10 CRC
	private int registerSize = 0;
	
	private RDS_CRC.Block currentBlock = null;
	private int lockCount = 0;
	private static final int lockThreshold = 4;		//Ignore bad syndrome offsets (block indicators) while above this success count.
	private static final int lockCountLimit = 47;	//Maximum success count.

	//Block stuff
	private boolean versionA = true; // Block Ca if true, Cb if false. Applies only to the current group.
	private int blockA; // Program identification code
	private int blockB; // Group type, A/B version, traffic, program type, and data
	private int blockC; // data (Ca or Cb, depending on version)
	private int blockD; // data
	
	//Data accumulator
	private final ProgramInformation programInfo = new ProgramInformation();

	private final IQVisualizer vis;

	public RDSDecoder(final float sampleRate) {
		rdsDataClock = new ScaledClock(rdsClockRate / pilotFrequency);
		
		ifDownsampler = new DownsamplerArray<>(LanczosTable.of(4), sampleRate, ifSampleRate, 3, this::acceptIfSamples);
		
		rdsBandpassFilter = new SingleFilter(ifSampleRate, new BandPass(LanczosTable.of(8), rdsLowLimit, rdsHighLimit));

		// These time delays cancel out strong the 2x and 4x frequency harmonics in the phase error calculation.
		// This is much more efficient than a low pass filter.
		loopNotchFilter1 = new QuarterWaveDelay(ifSampleRate, rdsClockRate);
		loopNotchFilter2 = new QuarterWaveDelay(ifSampleRate, rdsClockRate * 2f);

		// Step 7. Differential Manchester Decoding on the stream of 1 and 0. 01 and 10 -> true, 00 and 11 -> false.
		diffManchesterDecoder = new DifferentialManchesterDecoder<>(false);

		vis = debugVisualEnable ? new IQVisualizer(4) : null;
		if (debugVisualEnable && !liveDebug) {
			vis.syncOnColor(Color.red);
		}

		//AGC is somehow very sensitive.  Some stations have massive AM.
		gainLowPass = new RCLowPass(ifSampleRate, gainRC); //0.0002 for 98.5
		gainLowPass.setValue(0.01);
		clockUpDownLowPass= new RCLowPass(ifSampleRate, clockUpDownSelectRC);
	}

	/**
	 * Make some clocks, tune RDS from the 57kHz modulation, and then downsample everything.  The source sample rate is potentially extremely high.
	 * @param f
	 * @param pilotClock
	 */
	public void accept(final float f, final double pilotClock) {
		final double demodClock= rdsDataClock.tickAndGet(pilotClock, rdsPhaseAdj);
		downsamplerParametersArray[0]=  (float) (f * Math.sin(3 * pilotClock)); //Raw demodulation
		downsamplerParametersArray[1]=(float)Math.sin(demodClock); 		//Demod carrier
		downsamplerParametersArray[2]=(float)Math.sin(2 * demodClock);	//Phase lock double carrier
		ifDownsampler.accept(downsamplerParametersArray);
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
		
		final float modulationNoAGC = rdsBandpassFilter.apply(rawDemodulation);
		final float modulation = 0.5f * modulationNoAGC / SimplerMath.clamp(gainLowPass.apply(Math.abs(modulationNoAGC)), 0.000001f, 100f);
		
		//Remove the modulation from the encoded signal with the 1187.5 Hz clock.
		final float signal = modulation * demodCarrier;

		// The bounce after a phase shift may be weak, missing, or even slightly inverted on some stations.
		// Integration fixes it.
		// Integration is performed on clock up and clock down independently.
		// One integration will be strong, the other weak gibberish.
		clockUpIntegration+= signal;
		clockDownIntegration+= signal;
		
		final boolean clockUp= (demodCarrier >= 0) && (lastRdsClock < 0);
		final boolean clockDown= (demodCarrier <= 0) && (lastRdsClock > 0);
		
		if (clockUp) {
			clockUpDownLowPass.apply((float)Math.abs(clockUpIntegration));
			if (clockUpDownLowPass.getLastValue() > 0.01d) {
				acceptBinaryBit(diffManchesterDecoder.apply(clockUpIntegration >= 0));
			}
			clockUpIntegration= 0d;
		}
		if (clockDown) {
			clockUpDownLowPass.apply(-(float)Math.abs(clockDownIntegration));
			if (clockUpDownLowPass.getLastValue() < 0.01d) {
				acceptBinaryBit(diffManchesterDecoder.apply(clockDownIntegration >= 0));
			}
			clockDownIntegration= 0;
		}


		lastRdsClock = demodCarrier;

		// Costas loop. Phase shift the carrier-follower clock.
		// This loop is simplified because intermediate high frequencies are OK with so much oversampling.
		// This phase locks at 2x frequency (2x clock and signal^2) so that 180 degree phase inversions at 1x frequency become irrelevant 360 degree inversions.
		final float feedback = modulation * modulation * phaseLockDoubleCarrier;
		final float notchedFeedback1 = feedback + loopNotchFilter1.delay(feedback);
		final float notchedFeedback2 = notchedFeedback1 + loopNotchFilter2.delay(notchedFeedback1);
		rdsPhaseAdj = 0.0005d * notchedFeedback2 / (lockCount +5);
		
		if (debugVisualEnable) {
			if (liveDebug) {
				vis.fadeLight();
			}
			
			vis.drawAnalog(Color.cyan, lockCount / 40f);

			vis.drawAnalog(Color.red, modulation);
			
			final boolean up= clockUpDownLowPass.getLastValue() >= 0;
			
			vis.drawAnalog(Color.green, (up ? 6.5f : 4.5f) + 0.2f*(float) clockUpIntegration);
			vis.drawAnalog(Color.orange, (up ? 4.5f : 6.5f) + 0.2f*(float) clockDownIntegration);

			
			vis.drawAnalog(Color.yellow, 2f + signal);
			
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
				currentBlock = currentOffset;
				registerSize = 0; // Consume
				if (debugVisualEnable) {
					System.out.println("RDS good");
				}
			} else {
				if (debugVisualEnable && (currentBlock != null)) {
					System.out.println("Unknown syndrome: " + Integer.toBinaryString(syndrome));
				}
				// A bad register
				lockCount = Math.max(lockCount - 1, 0);
				if (lockCount >= lockThreshold) {
					// System.out.println("RDS bad");
					// Keep going. Maybe it will correct.
					currentBlock = currentOffset;
					registerSize = 0; // Consume
					if (debugVisualEnable) {
						System.out.println("RDS bad, continue");
					}
				} else {
					if (debugVisualEnable && (currentBlock != null)) {
						System.out.println("RDS searching...");
					}
					// Search again
					currentBlock = null;
				}
			}

			if (currentBlock != null) {
				if (debugDecodeEnable) {
					System.out.println(currentBlock);
				}
				
				acceptBlock(RDS_CRC.errorCorrect(register, currentBlock.offsetWord));
			}
		}
	}

	/**
	 * Process a block register
	 *
	 * @param word Error corrected data for the current block
	 */
	private void acceptBlock(final int word) {
		if ((word & RDS_CRC.possibleErrorFlag) != 0) {
			if (debugVisualEnable) {
				System.out.println("Possible error");
			}
			return;	//Don't pollute data
		}

		switch (currentBlock) {
			case A -> {
				blockA = word;
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

	private RDS_CRC.Block nextOffset() {
		if (currentBlock == RDS_CRC.Block.A) {
			return RDS_CRC.Block.B;
		}
		if (currentBlock == RDS_CRC.Block.B) {
			return versionA ? RDS_CRC.Block.Ca : RDS_CRC.Block.Cb;
		}
		if ((currentBlock == RDS_CRC.Block.Ca) || (currentBlock == RDS_CRC.Block.Cb)) {
			return RDS_CRC.Block.D;
		}
		return RDS_CRC.Block.A;
	}

}
