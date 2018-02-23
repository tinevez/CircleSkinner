package net.imagej.circleskinner.hough;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.scijava.Priority;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;

import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.Sampler;
import net.imglib2.algorithm.localextrema.LocalExtrema;
import net.imglib2.algorithm.localextrema.LocalExtrema.LocalNeighborhoodCheck;
import net.imglib2.algorithm.localextrema.RefinedPeak;
import net.imglib2.algorithm.localextrema.SubpixelLocalization;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;

@Plugin( type = HoughCircleDetectorOp.class, priority = Priority.HIGH )
public class HoughCircleLocalMaxDetectorOp< T extends RealType< T > & NativeType< T > >
		extends AbstractUnaryFunctionOp< RandomAccessibleInterval< T >, List< HoughCircle > >
		implements HoughCircleDetectorOp< T >
{

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

		/*
		 * Find local extrema.
		 */

		final double threshold = 2. * Math.PI * minRadius * circleThickness / sensitivity;
		final T t = Util.getTypeFromInterval( input ).createVariable();
		t.setReal( threshold );
		final LocalNeighborhoodCheck< Circle, T > maximumCheck = new LocalNeighborhoodCheck< Circle, T >()
		{
			@Override
			public < C extends Localizable & Sampler< T > > Circle check( final C center, final Neighborhood< T > neighborhood )
			{
				final T c = center.get();
				if ( t.compareTo( c ) > 0 )
					return null;

				for ( final T tt : neighborhood )
					if ( tt.compareTo( c ) > 0 )
						return null;

				final double val = c.getRealDouble();
				final double radius = minRadius + ( center.getDoublePosition( numDimensions - 1 ) ) * stepRadius;
				final double ls = 2. * Math.PI * radius * circleThickness / val;
				return new Circle( center, radius, ls );
			}
		};
		final ArrayList< Circle > peaks = LocalExtrema.findLocalExtrema( input, maximumCheck, threadService.getExecutorService() );

		if ( isCanceled() )
			return Collections.emptyList();

		/*
		 * Non-maxima suppression.
		 * 
		 * Rule: when one circle has a center inside one another, we discard the
		 * one with the highest sensitivity.
		 */

		// Sort by ascending sensitivity.
		Collections.sort( peaks );

		final List< Circle > retained = new ArrayList<>();
		NEXT_CIRCLE: for ( final Circle tested : peaks )
		{
			for ( final Circle kept : retained )
			{
				if ( kept.contains( tested ) )
					continue NEXT_CIRCLE;
			}

			// Was not found in any circle, so we keep it.
			retained.add( tested );
		}

		if ( isCanceled() )
			return Collections.emptyList();

		/*
		 * Refine local extrema.
		 */

		final SubpixelLocalization< Circle, T > spl = new SubpixelLocalization<>( numDimensions );
		spl.setAllowMaximaTolerance( true );
		spl.setMaxNumMoves( 10 );
		final ArrayList< RefinedPeak< Circle > > refined = spl.process( retained, input, input );

		if ( isCanceled() )
			return Collections.emptyList();

		/*
		 * Create circles.
		 */

		final ArrayList< HoughCircle > circles = new ArrayList<>( refined.size() );
		for ( final RefinedPeak< Circle > peak : refined )
		{
			final double radius = minRadius + ( peak.getDoublePosition( numDimensions - 1 ) ) * stepRadius;
			final double ls = 2. * Math.PI * radius * circleThickness / peak.getValue();
			if (ls < 0 || ls > sensitivity)
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

	/**
	 * A circle class defined on integer position with a sensitivity value.
	 * 
	 * @author Jean-Yves Tinevez
	 *
	 */
	private final class Circle extends Point implements Comparable< Circle >
	{
		private final double radius;

		private final double lSensitivity;

		public Circle( final Localizable pos, final double radius, final double sensitivity )
		{
			super( pos );
			this.radius = radius;
			this.lSensitivity = sensitivity;
		}

		@Override
		public int compareTo( final Circle o )
		{
			return lSensitivity < o.lSensitivity ? -1 : lSensitivity > o.lSensitivity ? +1 : 0;
		}

		public boolean contains( final RealLocalizable point )
		{
			final double dx = getDoublePosition( 0 ) - point.getDoublePosition( 0 );
			final double dy = getDoublePosition( 1 ) - point.getDoublePosition( 1 );
			final double dr2 = dx * dx + dy * dy;
			return dr2 <= radius * radius;
		}

	}
}
