package net.imagej.circleskinner.hough;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;

public class HoughCircle extends RealPoint implements Comparable< HoughCircle >
{

	private final double radius;

	private final double sensitivity;

	public HoughCircle( final RealLocalizable pos, final double radius, final double sensitivity )
	{
		super( pos );
		this.radius = radius;
		this.sensitivity = sensitivity;
	}

	@Override
	public String toString()
	{
		final StringBuilder sb = new StringBuilder();
		char c = '(';
		for ( int i = 0; i < numDimensions(); i++ )
		{
			sb.append( c );
			sb.append( String.format( "%.1f", position[ i ] ) );
			c = ',';
		}
		sb.append( ")" );
		return String.format( "%s\tR=%.1f\tSensitivity=%.1f", sb.toString(), radius, sensitivity );
	}

	public double getRadius()
	{
		return radius;
	}

	public double getSensitivity()
	{
		return sensitivity;
	}

	@Override
	public int compareTo( final HoughCircle o )
	{
		return sensitivity < o.sensitivity ? -1 : sensitivity > o.sensitivity ? +1 : 0;
	}
}
