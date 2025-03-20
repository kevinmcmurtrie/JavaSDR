package us.pixelmemory.kevin.sdr.resamplers;

import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;

abstract class AccumulatorBase {
	protected final LanczosTable lanczos;
	protected float inPosition;
	protected final float step;

	public AccumulatorBase(final LanczosTable lanczos, final float inPosition, final float inToOut) {
		this.lanczos = lanczos;
		this.inPosition = inPosition;
		this.step = 1 / inToOut;
	}

	final boolean step() {
		inPosition += step;
		if (inPosition >= lanczos.A) {
			inPosition -= 2 * lanczos.A;
			return true;
		}
		return false;
	}

	final float lz() {
		return lanczos.apply(inPosition);
	}

}