package us.pixelmemory.kevin.sdr.rds;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.FloatConsumer;
import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;
import us.pixelmemory.kevin.sdr.firfilters.LowPass;
import us.pixelmemory.kevin.sdr.firfilters.SingleFilterIQ;
import us.pixelmemory.kevin.sdr.resamplers.DownsamplerIQ;
import us.pixelmemory.kevin.sdr.resamplers.DownsamplerIdentifier;
import us.pixelmemory.kevin.sdr.tuners.PhaseShiftKeyingLock;
import us.pixelmemory.kevin.sdr.tuners.Tuner;
import us.pixelmemory.kevin.sdr.tuners.TunerLock;

/**
 * The success rate of binary data extraction sucks.
 */
public final class RDSDecoder implements FloatConsumer<RuntimeException> {
	private static final boolean debugEnable = true;
	private static final float rdsFrequency = 57000f;
	private static final float rdsRate = 1187.5f; // This is the data rate. The encoding is double-rate.
	private static final float intermediateFrequency1 = rdsRate * 16;
	private static final int digitalOversample = 6;
	private static final float intermediateFrequency2 = rdsRate * digitalOversample;
	private static final float inputGain = 20f;
	private final DownsamplerIdentifier<RuntimeException> secondDownsample;

	private final Tuner<?> frequencyShiftTuner;
	private final IQSample frequencyShiftIQ = new IQSample();

	private final DownsamplerIQ<RuntimeException> firstDownsample;
	private final SingleFilterIQ bandpass;
	private final Tuner<PhaseShiftKeyingLock> pskTuner;
	private final IQSample bandpassIQ = new IQSample();
	private final IQSample tunerIQ = new IQSample();
	private final DifferentialManchesterDecoder<RuntimeException> diffManchesterDecoder;

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
		// Step 1. Shift 57kHz +/- 2kHz to 0Hz +/- 2KHz. There's no phase lock yet.
		frequencyShiftTuner = new Tuner<>(TunerLock.getNoLock(false), sampleRate, rdsFrequency); // 57kHz -> 0Hz

		// Step 2. Downsample to the first intermediate frequency.
		firstDownsample = new DownsamplerIQ<>(LanczosTable.of(3), sampleRate, intermediateFrequency1, this::acceptIntermediateFrequency);

		// Step 3. Narrow lowpass the first intermediate frequency to preserve slight +/- deviations from 0Hz.
		bandpass = new SingleFilterIQ(intermediateFrequency1, new LowPass(LanczosTable.of(64), 1.8f * rdsRate));

		// Step 4. Decode the binary phase shift keying (BPSK) into 0 and 1.
		pskTuner = new Tuner<>(new PhaseShiftKeyingLock(2, intermediateFrequency1, 0.0005d, 0.01, true), sampleRate);

		// Step 5. Downsample the 0 and 1 stream to debounce edge transitions.
		secondDownsample = new DownsamplerIdentifier<>(LanczosTable.of(2), intermediateFrequency1, intermediateFrequency2, this::acceptDownsampled);

		// Step 6. Differential Manchester Decoding on the stream of 1 and 0. 01 and 10 -> true, 00 and 11 -> false.
		diffManchesterDecoder = new DifferentialManchesterDecoder<>(digitalOversample, true, this::acceptBinaryBit);

		vis = debugEnable ? new IQVisualizer() : null;
		if (debugEnable) {
			vis.syncOnColor(Color.green);
		}
	}

	/**
	 * Accept the raw signal from FMBroadcast.
	 * Frequency shift and downsample.
	 */
	@Override
	public void accept(final float f) {
		// This isn't quite legit because it's going to fake the quadrature phase from a phase shift signal.
		// A high sample rate helps make the phase shift signal smaller.
		frequencyShiftTuner.accept(f * inputGain, frequencyShiftIQ);
		firstDownsample.accept(frequencyShiftIQ);
	}

	/**
	 * Accept the downsampled intermediate signal.
	 * Bandpass, tune, and then downsample the tuned data to de-bounce.
	 *
	 * @param iq
	 */
	void acceptIntermediateFrequency(final IQSample iq) {
		bandpass.accept(iq, bandpassIQ);
		secondDownsample.accept(pskTuner.accept(bandpassIQ, tunerIQ).getValue());
	}

	/**
	 * Accept the downsampled PSK constellation code (0, 1)
	 * Send it to the DifferentialManchesterDecoder
	 *
	 * @param value constellation code (0, 1)
	 */
	private void acceptDownsampled(final int value) {
		diffManchesterDecoder.accept(value == 1);
		if (debugEnable) {
			vis.drawAnalog(Color.red, 2 + value);
		}
	}

	/**
	 * Accept binary bit from the DifferentialManchesterDecoder
	 *
	 * @param value bit
	 */
	private void acceptBinaryBit(final boolean value) {
		if (debugEnable) {
			for (int i = 0; i < digitalOversample; ++i) {
				vis.drawAnalog(Color.green, value ? 4.5 : 4);
			}
		}
		// Load up the register
		register = (register << 1) | (value ? 0 : 1);
		registerSize++;

		if (registerSize == 26) {
			// Figure out ABCD block sync using the injected CRC error.
			// An error-free signal is needed to get started.
			final var currentOffset = nextOffset();
			final int syndrome = RDS_CRC.extractSyndrome(register);
			if (currentOffset.syndrome == syndrome) {
				// A good register
				lockCount = Math.min(lockCount + 1, lockCountLimit);
				offsetBlock = currentOffset;
				registerSize = 0; // Consume
				// System.out.println("RDS good");
			} else {
				// A bad register
				lockCount = Math.max(lockCount - 1, 0);
				if (lockCount >= lockThreshold) {
					// System.out.println("RDS bad");
					// Keep going. Maybe it will correct.
					offsetBlock = currentOffset;
					registerSize = 0; // Consume
				} else {
					// System.out.println("RDS search");
					// Search again
					registerSize = 25;
					offsetBlock = null;
				}
			}

			if (offsetBlock != null) {
				if (debugEnable) {
					System.out.println(offsetBlock);
				}
				// FIXME - check that error correction worked.
				// Remove the injected CRC error and error correct.
				acceptBlock(RDS_CRC.decode(register, offsetBlock.offsetWord));
			}
		}
	}

	/**
	 * Process a block register
	 *
	 * @param word Error corrected data for the current block
	 */
	private void acceptBlock(final int word) {
		switch (offsetBlock) {
			case A -> {
				if ((word & RDS_CRC.possibleErrorFlag) == 0) {
					//Use last value if there may be an error
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
				if (debugEnable) {
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
