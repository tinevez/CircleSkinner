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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;

import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.dog.DogDetection;
import net.imglib2.algorithm.localextrema.RefinedPeak;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

@Plugin( type = HoughCircleDetectorOp.class )
public class HoughCircleDogDetectorOp< T extends RealType< T > & NativeType< T > >
		extends AbstractUnaryFunctionOp< RandomAccessibleInterval< T >, List< HoughCircle > >
		implements HoughCircleDetectorOp< T >
{

	private static final double K = 1.6;

	@Parameter
	private ThreadService threadService;

	@Parameter( required = true, min = "1" )
	private double circleThickness;

	@Parameter( required = true, min = "1" )
	private double minRadius;

	@Parameter( required = true, min = "1" )
	private double stepRadius;

	@Parameter( required = false, min = "0.1" )
	private double sensitivity = 20.;

	protected RandomAccessibleInterval< T > out;

	@Override
	public List< HoughCircle > calculate( final RandomAccessibleInterval< T > input )
	{
		final int numDimensions = input.numDimensions();
		final ExecutorService es = threadService.getExecutorService();

		final double threshold = 2. * Math.PI * minRadius * circleThickness / sensitivity;
		final double sigma = circleThickness / Math.sqrt( numDimensions );
		final double[] calibration = Util.getArrayFromValue( 1., numDimensions );
		final DogDetection< T > dog = new DogDetection<>(
				Views.extendZero( input ),
				input,
				calibration,
				sigma / K,
				sigma,
				DogDetection.ExtremaType.MINIMA,
				threshold,
				false );
		dog.setExecutorService( es );
		final ArrayList< RefinedPeak< Point > > refined = dog.getSubpixelPeaks();

		if ( isCanceled() )
			return Collections.emptyList();

		/*
		 * Create circles.
		 */

		final ArrayList< HoughCircle > circles = new ArrayList<>( refined.size() );
		for ( final RefinedPeak< Point > peak : refined )
		{
			// Minima are negative.
			final double radius = minRadius + ( peak.getDoublePosition( numDimensions - 1 ) ) * stepRadius;
			final double ls = -2. * Math.PI * radius * circleThickness / peak.getValue();
			if ( ls > sensitivity || ls <= 0 )
				continue;

			final RealPoint center = new RealPoint( numDimensions - 1 );
			for ( int d = 0; d < numDimensions - 1; d++ )
				center.setPosition( peak.getDoublePosition( d ), d );

			circles.add( new HoughCircle( center, radius, circleThickness, ls ) );
		}

		Collections.sort( circles );
		return circles;
	}

	// -- Cancelable methods --

	/** Reason for cancelation, or null if not canceled. */
	private String cancelReason;

	@Override
	public boolean isCanceled()
	{
		return cancelReason != null;
	}

	/** Cancels the command execution, with the given reason for doing so. */
	@Override
	public void cancel( final String reason )
	{
		cancelReason = reason == null ? "" : reason;
	}

	@Override
	public String getCancelReason()
	{
		return cancelReason;
	}

}
