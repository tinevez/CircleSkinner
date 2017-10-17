package net.imagej.circleskinner.util;

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.util.List;

import ij.ImagePlus;
import ij.gui.Roi;
import net.imagej.circleskinner.hough.HoughCircle;

public class HoughCircleOverlay extends Roi
{
	private static final long serialVersionUID = 1L;

	private static final ColorMap CM = ColorMap.jet();

	private List< HoughCircle > circles;

	private final double maxSensitivity;

	private final double minSensitivity;

	private double sensitivity;

	public HoughCircleOverlay( final ImagePlus imp, final double maxSensitivity )
	{
		super( 0, 0, imp );
		this.maxSensitivity = maxSensitivity;
		this.minSensitivity = maxSensitivity / 10.;
		this.sensitivity = maxSensitivity;
	}

	public void setCircles( final List< HoughCircle > circles )
	{
		this.circles = circles;
		imp.updateAndDraw();
	}

	@Override
	public void drawOverlay( final Graphics g )
	{
		final Graphics2D g2d = ( Graphics2D ) g;
		g2d.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		final int xcorner = ic.offScreenX( 0 );
		final int ycorner = ic.offScreenY( 0 );
		final double magnification = getMagnification();
		g2d.setStroke( new BasicStroke( ( float ) ( 1. / magnification ) ) );

		final AffineTransform transform = g2d.getTransform();
		g2d.scale( magnification, magnification );
		g2d.setFont( g2d.getFont().deriveFont( 18f ) );

		if ( null != circles && !circles.isEmpty() )
		{
			int circleIndex = 0;
			for ( final HoughCircle circle : circles )
			{

				// Do not paint circles with sensitivity higher than our bound.
				final double s = circle.getSensitivity();
				if ( s > sensitivity )
					continue;

				final double alpha = ( s - minSensitivity ) / ( maxSensitivity - minSensitivity );
				g.setColor( CM.get( 1. - alpha ) );

				final double x = circle.getDoublePosition( 0 ) - xcorner + 0.5;
				final double y = circle.getDoublePosition( 1 ) - ycorner + 0.5;
				g2d.translate( x, y );

				final double thickness = circle.getThickness();

				final double w1 = 2. * circle.getRadius() + thickness;
				final double h1 = w1;
				final Ellipse2D ellipse1 = new Ellipse2D.Double( -w1 / 2., -h1 / 2., w1, h1 );
				g2d.draw( ellipse1 );

				final double w2 = 2. * circle.getRadius() - thickness;
				final double h2 = w2;
				final Ellipse2D ellipse2 = new Ellipse2D.Double( -w2 / 2., -h2 / 2., w2, h2 );
				g2d.draw( ellipse2 );

				g2d.drawString( "" + ++circleIndex, 0, 0 );

				g2d.translate( -x, -y );
			}
		}

		g2d.setTransform( transform );
	}

	public void setSensitivity( final double sensitivity )
	{
		this.sensitivity = sensitivity;
	}
}
