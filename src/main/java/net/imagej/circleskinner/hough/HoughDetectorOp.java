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

@Plugin( type = HoughDetectorOp.class )
public class HoughDetectorOp< T extends RealType< T > & NativeType< T > >
		extends AbstractUnaryFunctionOp< RandomAccessibleInterval< T >, List< HoughCircle > >
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
		final DogDetection< T > dog = new DogDetection< T >(
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

		/*
		 * Create circles.
		 */

		final ArrayList< HoughCircle > circles = new ArrayList<>( refined.size() );
		for ( final RefinedPeak< Point > peak : refined )
		{
			// Minima are negative.
			final RealPoint center = new RealPoint( numDimensions - 1 );
			for ( int d = 0; d < numDimensions - 1; d++ )
				center.setPosition( peak.getDoublePosition( d ), d );

			final double radius = minRadius + ( peak.getDoublePosition( numDimensions - 1 ) ) * stepRadius;
			final double ls = -2. * Math.PI * minRadius * circleThickness / peak.getValue();
			circles.add( new HoughCircle( center, radius, circleThickness, ls ) );
		}

		Collections.sort( circles );
		return circles;
	}
}
