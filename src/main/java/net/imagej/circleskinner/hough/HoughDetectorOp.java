package net.imagej.circleskinner.hough;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;

import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Sampler;
import net.imglib2.algorithm.localextrema.LocalExtrema;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

@Plugin( type = HoughDetectorOp.class )
public class HoughDetectorOp< T extends RealType< T > >
		extends AbstractUnaryFunctionOp< RandomAccessibleInterval< T >, Collection< Point > >
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

	@Override
	public Collection< Point > compute1( final RandomAccessibleInterval< T > input )
	{
		final int numDimensions = input.numDimensions();
		final double sigma = circleThickness / 2./ Math.sqrt( numDimensions -1 );
		final int radiusDim = numDimensions;

		/*
		 * Filter input as a collection of 2D slices.
		 */

		final Img< DoubleType > filtered = ops().create().img( input );
		for ( int i = 0; i < input.dimension( radiusDim ); i++ )
		{
			final IntervalView< T > sliceInput = Views.hyperSlice( input, radiusDim, i );
			final IntervalView< DoubleType > sliceFiltered = Views.hyperSlice( filtered, radiusDim, i );
			ops().filter().dog( sliceFiltered, sliceInput, sigma, sigma / K );
		}
		ImageJFunctions.show( filtered, "DoG filtered" ); // DEBUG

		/*
		 * Detect maxima with a threshold that depends on candidate radius.
		 */
		final MyLocalExtremaCheck check = new MyLocalExtremaCheck( input.randomAccess( input ) );
		final ExecutorService es = threadService.getExecutorService();
		final ArrayList< Point > maxima = LocalExtrema.findLocalExtrema( filtered, check, es );
		
		return maxima;
	}

	private class MyLocalExtremaCheck implements LocalExtrema.LocalNeighborhoodCheck< Point, DoubleType >
	{

		private final RandomAccess< T > ra;

		public MyLocalExtremaCheck( final RandomAccess< T > ra )
		{
			this.ra = ra;
		}

		@Override
		public < C extends Localizable & Sampler< DoubleType > > Point check( final C center, final Neighborhood< DoubleType > neighborhood )
		{
			// What radius is the center on?
			final double i = center.getDoublePosition( center.numDimensions() - 1 );
			// Determine sensible threshold, assuming that the center has been
			// voted a certain number of times.
			final double radius = minRadius + i * stepRadius;
			final double threshold = 2. * Math.PI * radius * circleThickness / sensitivity;

			ra.setPosition( center );
			if ( ra.get().getRealDouble() < threshold )
				return null;

			for ( final DoubleType d : neighborhood )
				if ( d.compareTo( center.get() ) >= 0 )
					return null;

			return new Point( center );
		}

	}

}
