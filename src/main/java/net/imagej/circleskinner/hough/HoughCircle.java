package net.imagej.circleskinner.hough;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;

public class HoughCircle extends RealPoint implements Comparable< HoughCircle >
{

	private final double radius;

	private final double thickness;

	private final double sensitivity;

	private Stats stats;

	public HoughCircle( final RealLocalizable pos, final double radius, final double thickness, final double sensitivity )
	{
		super( pos );
		this.radius = radius;
		this.thickness = thickness;
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
		return String.format( "%s\tR=%.1f\t±\t %.1f\tSensitivity=%.1f", sb.toString(), radius, thickness / 2., sensitivity );
	}

	public HoughCircle copy()
	{
		final HoughCircle c = new HoughCircle( this, radius, thickness, sensitivity );
		if ( null != stats )
			c.setStats( stats.mean, stats.std, stats.N, stats.median );
		return c;
	}

	public double getRadius()
	{
		return radius;
	}

	public double getThickness()
	{
		return thickness;
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

	public boolean contains( final RealLocalizable point )
	{
		final double dx = getDoublePosition( 0 ) - point.getDoublePosition( 0 );
		final double dy = getDoublePosition( 1 ) - point.getDoublePosition( 1 );
		final double dr2 = dx * dx + dy * dy;

		final double radMin = radius - thickness / 2.;
		if ( dr2 < radMin * radMin )
			return false;

		final double radMax = radius + thickness / 2.;
		if ( dr2 > radMax * radMax )
			return false;

		return true;
	}

	public double area()
	{
		final double radMin = radius - thickness / 2.;
		final double radMax = radius + thickness / 2.;
		return Math.PI * ( radMax * radMax - radMin * radMin );
	}

	public void setStats( final double mean, final double std, final int N, final double median )
	{
		this.stats = new Stats( mean, std, N, median );
	}

	public Stats getStats()
	{
		return stats;
	}

	public static final class Stats
	{

		public final double mean;

		public final double std;

		public final int N;

		public final double median;

		public Stats( final double mean, final double std, final int n, final double median )
		{
			this.mean = mean;
			this.std = std;
			this.N = n;
			this.median = median;
		}
	}
}
