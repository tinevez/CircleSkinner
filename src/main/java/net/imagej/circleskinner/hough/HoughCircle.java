/*-
 * #%L
 * A Fiji plugin for the automated detection and quantification of circular structure in images.
 * %%
 * Copyright (C) 2016 - 2022 My Company, Inc.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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

	public void setStats( final double[] means, final double[] stds, final int N, final double[] medians )
	{
		this.stats = new Stats( means, stds, N, medians );
	}

	public Stats getStats()
	{
		return stats;
	}

	public static final class Stats
	{

		public final double[] mean;

		public final double[] std;

		public final int N;

		public final double[] median;

		public Stats( final double[] means, final double[] stds, final int n, final double[] medians )
		{
			this.mean = means;
			this.std = stds;
			this.N = n;
			this.median = medians;
		}
	}
}
