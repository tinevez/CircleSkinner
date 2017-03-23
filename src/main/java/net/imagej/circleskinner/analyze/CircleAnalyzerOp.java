package net.imagej.circleskinner.analyze;

import java.util.Collection;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.util.ResizableDoubleArray;
import org.scijava.plugin.Plugin;

import net.imagej.circleskinner.hough.HoughCircle;
import net.imagej.ops.special.inplace.AbstractBinaryInplace1Op;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

@Plugin( type = CircleAnalyzerOp.class )
public class CircleAnalyzerOp< T extends RealType< T > > extends AbstractBinaryInplace1Op< Collection< HoughCircle >, RandomAccessibleInterval< T > >
{

	@Override
	public Collection< HoughCircle > run( final Collection< HoughCircle > input, final Collection< HoughCircle > output )
	{
		mutate1( input, in2() );
		return input;
	}

	@Override
	public void mutate1( final Collection< HoughCircle > arg, final RandomAccessibleInterval< T > in )
	{
		for ( final HoughCircle circle : arg )
			processCircle( circle );

	}

	private void processCircle( final HoughCircle circle )
	{
		checkOverlap( circle );

		final Interval interval = toInterval( circle );
		final IntervalView< T > roi = Views.interval( in2(), interval );
		final Cursor< T > cursor = roi.localizingCursor();

		final int initCapacity = ( int ) ( 2. * circle.area() ); // Conservative
		final ResizableDoubleArray arr = new ResizableDoubleArray(initCapacity);

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			if ( circle.contains( cursor ) )
				arr.addElement( cursor.get().getRealDouble() );
		}
		final double[] vals = arr.getElements();

		final Mean meanCal = new Mean();
		final double mean = meanCal.evaluate( vals );
		final StandardDeviation stdCalc = new StandardDeviation();
		final double std = stdCalc.evaluate( vals, mean );
		final Median medianCalc = new Median();
		final double median = medianCalc.evaluate( vals );
		final int N = vals.length;

		circle.setStats( mean, std, N, median );
	}

	/**
	 * Returns the smallest interval that contains the full circle, included in
	 * the source image.
	 * 
	 * @param circle
	 *            the circle to be included in the interval.
	 * @return a new interval.
	 */
	private final Interval toInterval( final HoughCircle circle )
	{
		final double x = circle.getDoublePosition( 0 );
		final double y = circle.getDoublePosition( 1 );
		final double radius = circle.getRadius();
		final double thickness = circle.getThickness();

		final long minX = Math.max( 0l,
				Double.valueOf( Math.floor( x - radius - thickness / 2. ) ).longValue() );
		final long maxX = Math.min( in2().dimension( 0 ) - 1l,
				Double.valueOf( Math.floor( x + radius + thickness / 2. ) ).longValue() );
		final long minY = Math.max( 0l,
				Double.valueOf( Math.floor( y - radius - thickness / 2. ) ).longValue() );
		final long maxY = Math.min( in2().dimension( 1 ) - 1l,
				Double.valueOf( Math.floor( y + radius + thickness / 2. ) ).longValue() );

		return Intervals.createMinMax( minX, minY, maxX, maxY );
	}

	/**
	 * Checks that the specified circle does not overlap with the other circles
	 * processed by this call. If yes, returns a range over which the circle
	 * does not overlap with other circles.
	 * 
	 * @param circle
	 *            the circle to check.
	 */
	private void checkOverlap( final HoughCircle circle )
	{
		// TODO
	}

}
