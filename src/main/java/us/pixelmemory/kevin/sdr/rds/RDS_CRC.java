package us.pixelmemory.kevin.sdr.rds;

public class RDS_CRC {
	// MessagebitsCheckbits

	// From RDS specification
	private static final int G = 0x5b9; // bits (10 8 7 5 4 3) + 1
	private static final int checkBits = 10;
	private static final int checkMask = (1 << checkBits) - 1;
	private static final int messageBits = 16;
	private static final int totalBits = checkBits + messageBits;
	public static final int registerMask = (1 << totalBits) - 1;
	public static final int messageMask = (1 << messageBits) - 1;
	public static final int possibleErrorFlag = 1 << 30; // The corrected message may have damage if this bit is set.

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

	public enum Block { /* */
		A(0b0011111100, 0b1111011000), /* */
		B(0b0110011000, 0b1111010100), /* */
		Ca(0b0101101000, 0b1001011100), /* */
		Cb(0b1101010000, 0b1111001100), /* */
		D(0b0110110100, 0b1001011000), /* */
		E(0b0000000000, 0b0000000000); /* */

		public final int offsetWord;
		public final int syndrome;

		private Block(final int offsetWord, final int syndrome) {
			this.offsetWord = offsetWord;
			this.syndrome = syndrome;
		}

		public static Block ofWord(final int word) {
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

		public static Block ofSyndrome(final int word) {
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
		for (int oc = 0; oc < checkBits; ++oc) {
			final int masked = codeword & H16[oc];
			acc |= (Integer.bitCount(masked) & 1) << oc;
		}
		return acc;
	}

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
		return ((message & messageMask) << checkBits) | ((syndrome ^ offset) & checkMask);
	}

	public static void main(final String args[]) {
		// 1938241001

		// for (int i= 0; i < (1 << 26); ++i) {
		// int c= extractSyndromeFromC(i);
		// int j= extractSyndrome(i);
		//
		// if (c != j) {
		// System.out.println(i);
		// }
		// }
		//

		final int registerOrig = 1937978857;

		System.out.println("Orig: " + messageBitsOfRegister(registerOrig) + ":" + checkBitsOfRegister(registerOrig));
		System.out.println("Orig decode : " + errorCorrect(registerOrig, Block.Ca.offsetWord));

		for (int bit = 0; bit < 26; ++bit) {

			final int register = registerMask & (registerOrig ^ (0x1f << bit));

			final int codeIndicator = extractSyndrome(register);

			Block code = Block.ofSyndrome(codeIndicator);
			if (code == null) {
				code = Block.Ca;
			}

			System.out.println("Java Bit :" + bit + " Test: " + (messageMask & errorCorrect(register, Block.Ca.offsetWord)));
		}

	}

	static int checkBitsOfRegister(final int register) {
		return register & checkMask;
	}

	static int messageBitsOfRegister(final int register) {
		return (register >>> checkBits) & messageMask;
	}

	/**
	 * Correct up to 5 consecutive errors in received RDS register.
	 *
	 * @param register
	 * @param offset
	 * @return
	 */
	static int errorCorrect(final int register, final int offset) {
		/*
		 * The following coded with help from <a href='https://the-art-of-ecc.com/3_Cyclic_BCH/RBDS.c'>The Art of Error Correcting Coding</a>
		 * <code>ISBN 0471 49581 6</code>
		 * <br> Original Copyright (c) 2002. Robert H. Morelos-Zaragoza. All rights reserved.<br>
		 * The RDS spec references books that aren't online.
		 */

		int message = messageBitsOfRegister(register);
		int syndrome = extractSyndrome(register ^ offset);

		// Correct for error bursts
		for (int i = 15; i >= 0; --i) {
			// When the five left-most stages in the syndrome-register are all zero a possible error burst with
			// a maximum length of five bits must lie in the five right-hand stages of the message.
			if ((syndrome & 0x1f) == 0) {
				message ^= ((syndrome & 0x200) >>> 9) << i;
				syndrome <<= 1;
			} else {
				syndrome <<= 1;
				if ((syndrome & 0x400) != 0) {
					syndrome ^= G;
				}
			}
		}

		if (syndrome != 0) {
			// Made corrections
			// If the syndrome spans more than 5 bits it probably failed
			if (Integer.numberOfLeadingZeros(syndrome) + Integer.numberOfTrailingZeros(syndrome) < (32 - 5)) {
				return (message & messageMask) | possibleErrorFlag;
			}
		}

		return message & messageMask;
	}
}
