package net.imagej.circleskinner.hough;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;

public class HoughCircle extends RealPoint
{

	private final double radius;

	public HoughCircle( final RealLocalizable pos, final double radius )
	{
		super( pos );
		this.radius = radius;
	}

	@Override
	public String toString()
	{
		return super.toString() + " r = " + radius;
	}

	public double getRadius()
	{
		return radius;
	}
}
