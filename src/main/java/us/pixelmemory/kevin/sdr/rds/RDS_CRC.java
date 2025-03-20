package us.pixelmemory.kevin.sdr.rds;

public class RDS_CRC {
	// MSB first

	// From RDS specification
	private static final int G = 0x5b9; // bits (10 8 7 5 4 3) + 1
	// private static final int moduloG = 0x31b; // bits (9 8 4 3 2 1) + 1
	private static final int errorMask = 0x1f;
	private static final int syndromeBits = 10;
	private static final int syndromeMask = (1 << syndromeBits) - 1;
	private static final int messageBits = 16;
	private static final int messageMask = (1 << messageBits) - 1;

	private static final int G16[] = { /**/
			0b00000000000000010110111001, /**/
			0b00000000000000101101110010, /**/
			0b00000000000001001101011101, /**/
			0b00000000000010001100000011, /**/
			0b00000000000100001110111111, /**/
			0b00000000001000001011000111, /**/
			0b00000000010000000000110111, /**/
			0b00000000100000000001101110, /**/
			0b00000001000000000011011100, /**/
			0b00000010000000000110111000, /**/
			0b00000100000000001101110000, /**/
			0b00001000000000001101011001, /**/
			0b00010000000000001100001011, /**/
			0b00100000000000001110101111, /**/
			0b01000000000000001011100111, /**/
			0b10000000000000000001110111 /**/
	};

	/**
	 * Syndrome extractor table from the RDS specification.
	 * Rotated from trigonometry to computer orientation.
	 */
	private static final int H16[] = { /**/
			0b00000000010011111011001111, /**/
			0b00000000100111110110011111, /**/
			0b00000001001111101100111110, /**/
			0b00000010001100100010110011, /**/
			0b00000100001010111110101001, /**/
			0b00001000000110000110011100, /**/
			0b00010000001100001100111000, /**/
			0b00100000001011100010111110, /**/
			0b01000000000100111110110011, /**/
			0b10000000001001111101100111 /**/
	};

	public enum Offset { /* */
		A(0b0011111100, 0b1111011000), /* */
		B(0b0110011000, 0b1111010100), /* */
		Ca(0b0101101000, 0b1001011100), /* */
		Cb(0b1101010000, 0b1111001100), /* */
		D(0b0110110100, 0b1001011000), /* */
		E(0b0000000000, 0b0000000000); /* */

		public final int offsetWord;
		public final int syndrome;

		private Offset(final int offsetWord, final int syndrome) {
			this.offsetWord = offsetWord;
			this.syndrome = syndrome;
		}

		public static Offset ofWord(final int word) {
			return switch (word) {
				case 0b0011111100 -> A;
				case 0b0110011000 -> B;
				case 0b0101101000 -> Ca;
				case 0b1101010000 -> Cb;
				case 0b0110110100 -> D;
				case 0b0000000000 -> E;
				default -> null;
			};
		}

		public static Offset ofSyndrome(final int word) {
			return switch (word) {
				case 0b1111011000 -> A;
				case 0b1111010100 -> B;
				case 0b1001011100 -> Ca;
				case 0b1111001100 -> Cb;
				case 0b1001011000 -> D;
				case 0b0000000000 -> E;
				default -> null;
			};
		}
	}

	public static final void main(final String args[]) {
		System.out.println(extractSyndrome(encode(5, 0b0000000000)));
	}

	/**
	 * Fast syndrome extractor from the RDS specification. Syndrome codes are used to indicate the stream position.
	 * The position indicates which error correction offset to use.
	 * <br>
	 * This has no error correction.
	 *
	 * @param codeword
	 * @return Syndrome offset (not error corrected)
	 */
	public static final int extractSyndrome(final int codeword) {
		int acc = 0;
		for (int oc = 0; oc < syndromeBits; ++oc) {
			final int masked = codeword & H16[oc];
			acc |= (Integer.bitCount(masked) & 1) << oc;
		}
		return acc;
	}

	// Decode syndrome
	// Sync syndrome to offset A,B, C/C', or D pattern
	// XOR away offset once synced to pattern
	// use syndrome to correct future errors

	/**
	 * Multiply by 2^syndromeBits (0x400) and divide by G
	 *
	 * @param message message (up to 16 bits)
	 * @param offset 10 bits of syndrome XOR used to synchronize the bitstream.
	 * @return Encoded value
	 */
	public static int encode(final int message, final int offset) {
		int syndrome = 0;
		for (int i = 0; i < 16; ++i) {
			final int bitmask = 1 << i;
			if ((message & bitmask) != 0) {
				syndrome ^= G16[i];
			}
		}
		return ((message & messageMask) << syndromeBits) | ((syndrome ^ offset) & syndromeMask);
	}

	/**
	 * Correct for bursts (consecutive) errors. The offset must be known so it may be removed.
	 * Don't work well for non-adjacent errors.
	 *
	 * @param message Up to 16 bits of message plus 10 bits of error correction
	 * @param offset 10 bits of syndrome XOR used to synchronize the bitstream.
	 * @return Decoded and corrected (as much as possible) value
	 */
	public static int decode(final int encodedMessage, final int offset) {
		return decodeMessage((encodedMessage >>> syndromeBits) & messageMask, (extractSyndrome(encodedMessage) ^ offset) & syndromeMask);
	}

	public static int decodeMessage(int message, int syndrome) {
		/*
		 * The following coded with help from <a href='https://the-art-of-ecc.com/3_Cyclic_BCH/RBDS.c'>The Art of Error Correcting Coding</a>
		 * <code>ISBN 0471 49581 6</code>
		 * <br> Original Copyright (c) 2002. Robert H. Morelos-Zaragoza. All rights reserved.<br>
		 * The RDS spec references books that aren't online.
		 */

		// Correct for error bursts
		for (int i = 15; i >= 0; --i) {
			// When the five left-most stages in the syndrome-register are all zero a possible error burst with
			// a maximum length of five bits must lie in the five right-hand stages of the message.
			if ((syndrome & errorMask) == 0) {
				message ^= ((syndrome & 0x200) >>> 9) << i;
				syndrome <<= 1;
			} else {
				syndrome <<= 1;
				if ((syndrome & 0x400) != 0) {
					syndrome ^= G;
				}
			}
		}
		return message;
	}
}
