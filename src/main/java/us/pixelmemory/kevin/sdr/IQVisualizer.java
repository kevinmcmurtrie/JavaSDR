package us.pixelmemory.kevin.sdr;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.HashMap;
import java.util.HashSet;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

public class IQVisualizer extends JPanel {
	private static final long serialVersionUID = -2563504248168923189L;
	private final HashMap<Color, Point2D.Float> previousPoints = new HashMap<>();
	private final HashSet<Color> syncColor = new HashSet<>();

	private final BufferedImage img;
	private final byte pixels[];
	private final float analogXScale;

	private final Graphics imgG;

	private final JFrame frame;
	private boolean doClear;
	private boolean doFade;

	public IQVisualizer() {
		this(1);
	}

	public IQVisualizer(final float analogXScale) {
		super(false);
		this.analogXScale = analogXScale;

		img = new BufferedImage(1000, 1000, BufferedImage.TYPE_3BYTE_BGR);
		pixels = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		imgG = img.createGraphics();

		frame = new JFrame();
		frame.setSize(1000, 1000);
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		frame.setTitle("Test Frame");
		frame.add(this);
		setBounds(0, 0, 1000, 1000);
		frame.setVisible(true);
	}

	@Override
	public void paint(final Graphics g) {
		super.paint(g);
		if (doClear) {
			doClear = false;
			for (int i = pixels.length - 1; i >= 0; --i) {
				pixels[i] = 0;
			}
			previousPoints.clear();
		}
		if (doFade) {
			doFade = false;
			fadeStrong();
		}
	}

	public void close() {
		frame.setVisible(false);
	}

	@Override
	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		g.drawImage(img, 0, 0, null);
	}

	public void fadeLight() {
		for (int i = pixels.length - 1; i >= 0; --i) {
			int v = pixels[i] & 0xFF;
			v -= 1;
			if (v > 0) {
				pixels[i] = (byte) v;
			} else {
				pixels[i] = (byte) 0;
			}
		}
	}

	public void fade() {
		for (int i = pixels.length - 1; i >= 0; --i) {
			int v = pixels[i] & 0xFF;
			v -= 1 + v / 64;
			if (v > 0) {
				pixels[i] = (byte) v;
			} else {
				pixels[i] = (byte) 0;
			}
		}
	}

	public void fadeStrong() {
		for (int i = pixels.length - 1; i >= 0; --i) {
			int v = pixels[i] & 0xFF;
			v -= 1 + v / 2;
			if (v > 0) {
				pixels[i] = (byte) v;
			} else {
				pixels[i] = (byte) 0;
			}
		}
	}

	public void clear() {
		doClear = true;
	}

	private static IQSample phase0 = new IQSample();
	private static IQSample phase1 = new IQSample(Math.PI / 2);
	private static IQSample phase2 = new IQSample(Math.PI);
	private static IQSample phase3 = new IQSample(Math.PI + Math.PI / 2);

	private static Color marker0 = new Color(254, 254, 254);
	private static Color markerQuads = new Color(50, 50, 50);

	public void markCenter() {
		imgG.setColor(marker0);
		imgG.fillOval(500, 500, 9, 9);
		drawIQ(marker0, phase0);
		previousPoints.remove(marker0);
		drawIQ(markerQuads, phase1);
		previousPoints.remove(markerQuads);
		drawIQ(markerQuads, phase2);
		previousPoints.remove(markerQuads);
		drawIQ(markerQuads, phase3);
		previousPoints.remove(markerQuads);
	}

	public void syncOnColor(final Color c) {
		syncColor.add(c);
	}

	public void drawAnalog(final Color c, final double v) {
		final Point2D.Float previous = previousPoints.computeIfAbsent(c, x -> new Point2D.Float(0, 800));
		boolean doSync = false;

		float x = previous.x + analogXScale;
		if (x >= 1000) {
			x = 0;
			doSync = syncColor.contains(c);
		}
		final int y = (int) (800 - (v * 100));

		imgG.setColor(c);
		if (previous.x < x) {
			imgG.drawLine(Math.round(previous.x), Math.round(previous.y), Math.round(x), y);
		}
		previous.x = x;
		previous.y = y;

		if (doSync) {
			doClear = true;
			repaint();
		}
	}

	public void reset(final Color c) {
		previousPoints.remove(c);
	}

	// Vertical = in, horizontal= quadrature
	public void drawIQ(final Color c, final IQSample s) {
		if (s != null) {
			final int x = 500 + (int) (400 * s.quad);
			final int y = 500 - (int) (400 * s.in);
			imgG.setColor(c);
			imgG.fillOval(x, y, 9, 9);
			final Point2D.Float previous = previousPoints.get(c);
			if (previous != null) {
				imgG.setColor(Color.gray);
				imgG.drawLine(Math.round(previous.x), Math.round(previous.y), x, y);

				if ((y > 500) && (previous.y <= 500) && syncColor.contains(c)) {
					doFade = true;
					repaint();
				}

				previous.x = x;
				previous.y = y;
			} else {
				previousPoints.put(c, new Point2D.Float(x, y));
			}
		} else {
			previousPoints.remove(c);
		}
	}
}
