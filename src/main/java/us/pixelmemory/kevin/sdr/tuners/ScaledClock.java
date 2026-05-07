package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;

public final class ScaledClock {
	public final double scale;
	private double clock= 0;
	private double lastSuperClock= 0;

	public ScaledClock(final double scale) {
		this.scale = scale;
	}
	
	public double tickAndGet(double superClock) {
		clockTick(superClock);
		return clock;
	}
	
	public double tickAndGet(double superClock, final double adjustment) {
		clockTick(superClock, adjustment);
		return clock;
	}
	
	public double getClock() {
		return clock;
	}
	
	public void clockTick(double superClock) {
		clock= Clock.wrapClock(clock + scaledDiff(superClock));
	}
	
	public void clockTick(double superClock, final double adjustment) {
		clock= Clock.wrapClock(clock + adjustment + scaledDiff(superClock));
	}
	
	private double scaledDiff (double superClock) {
		double diff= superClock - lastSuperClock;
		if (diff > Math.PI) {
			diff -= Math.TAU;
		} else if (diff < -Math.PI) {
			diff += Math.TAU;
		}
		lastSuperClock= superClock;
		return diff * scale;
	}
	
	public static void main(String[] args) throws InterruptedException {
		IQVisualizer vis= new IQVisualizer();
		final float sampleRateHz= 200000;
		final float superHz= 400;
		final Clock superClock= new Clock(sampleRateHz, superHz);
		final ScaledClock scaledClock= new ScaledClock(1d/4d);
		
		IQSample superIQ= new IQSample();
		IQSample scaledIQ= new IQSample();
		
		for (int i= 0; i < 10000000; ++i) {
			
			if (i % 4 == 0) {
				vis.fadeLight();
			}
			
			superIQ.setMoment(superClock.tickAndGet());
			scaledIQ.setMoment(scaledClock.tickAndGet(superClock.getClock()));
			
			vis.drawIQ(Color.red, superIQ);
			vis.drawIQ(Color.blue, scaledIQ);
			vis.repaint();
			
			Thread.sleep(40);
		}
	}
}
