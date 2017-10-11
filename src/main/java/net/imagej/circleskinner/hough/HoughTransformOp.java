package net.imagej.circleskinner.hough;

import org.scijava.Cancelable;
import org.scijava.app.StatusService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imagej.ops.special.hybrid.AbstractUnaryHybridCF;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.IterableInterval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.BooleanType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

@Plugin( type = HoughTransformOp.class )
public class HoughTransformOp< T extends BooleanType< T > >
		extends AbstractUnaryHybridCF< IterableInterval< T >, Img< DoubleType > >
		implements Cancelable
{

	@Parameter
	private StatusService statusService;

	@Parameter( min = "1" )
	private int minRadius = 1;

	@Parameter( min = "1" )
	private int maxRadius = 50;

	@Parameter( min = "1" )
	private int stepRadius = 2;

	@Override
	public Img< DoubleType > createOutput( final IterableInterval< T > input )
	{
		final int numDimensions = input.numDimensions();
		
		if ( input.numDimensions() != 2 ) { throw new IllegalArgumentException(
				"Cannot compute Hough transform non-2D images. Got " + numDimensions + "D image." ); }
		
		maxRadius = Math.max( minRadius, maxRadius );
		minRadius = Math.min( minRadius, maxRadius );
		final int nRadiuses = ( maxRadius - minRadius ) / stepRadius + 1;
		
		// Get a suitable image factory.
		final long[] dims = new long[ numDimensions + 1 ];
		for ( int d = 0; d < numDimensions; d++ )
			dims[ d ] = input.dimension( d );
		dims[ numDimensions ] = nRadiuses;
		final Dimensions dimensions = FinalDimensions.wrap( dims );
		final ImgFactory< DoubleType > factory = ops().create().imgFactory( dimensions );
		final Img< DoubleType > votes = factory.create( dimensions, new DoubleType() );
		return votes;
	}

	@Override
	public void compute( final IterableInterval< T > input, final Img< DoubleType > votes )
	{
		final int numDimensions = input.numDimensions();

		if ( input.numDimensions() != 2 ) { throw new IllegalArgumentException(
				"Cannot compute Hough transform non-2D images. Got " + numDimensions + "D image." ); }

		maxRadius = Math.max( minRadius, maxRadius );
		minRadius = Math.min( minRadius, maxRadius );
		final int nRadiuses = ( maxRadius - minRadius ) / stepRadius + 1;

		/*
		 * Hough transform.
		 */

		final double sum = ops().stats().sum( input ).getRealDouble();
		int progress = 0;

		final Cursor< T > cursor = input.localizingCursor();
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			if ( !cursor.get().get() )
				continue;

			for ( int i = 0; i < nRadiuses; i++ )
			{
				final IntervalView< DoubleType > slice = Views.hyperSlice( votes, numDimensions, i );
				final RandomAccess< DoubleType > ra = Views.extendZero( slice ).randomAccess();
				final int r = minRadius + i * stepRadius;
				midPointAlgorithm( cursor, r, ra );
			}

			statusService.showProgress( ++progress, ( int ) sum );
			if ( isCanceled() )
				return;
		}
	}

	private static final void midPointAlgorithm( final Localizable position, final int radius, final RandomAccess< DoubleType > ra )
	{
		final int x0 = position.getIntPosition( 0 );
		final int y0 = position.getIntPosition( 1 );

		/*
		 * We "zig-zag" through indices, so that we reconstruct a continuous set
		 * of of x,y coordinates, starting from the top of the circle.
		 */

		final int octantSize = ( int ) Math.floor( ( Math.sqrt( 2 ) * ( radius - 1 ) + 4 ) / 2 );

		int x = 0;
		int y = radius;
		int f = 1 - radius;
		int dx = 1;
		int dy = -2 * radius;

		for ( int i = 2; i < octantSize; i++ )
		{
			// We update x & y
			if ( f > 0 )
			{
				y = y - 1;
				dy = dy + 2;
				f = f + dy;
			}
			x = x + 1;
			dx = dx + 2;
			f = f + dx;

			// 1st octant.
			ra.setPosition( x0 + x, 0 );
			ra.setPosition( y0 + y, 1 );
			ra.get().inc();

			// 2nd octant.
			ra.setPosition( x0 - x, 0 );
//			ra.setPosition( y0 + y, 1 );
			ra.get().inc();

			// 3rd octant.
			ra.setPosition( x0 + x, 0 );
			ra.setPosition( y0 - y, 1 );
			ra.get().inc();

			// 4th octant.
			ra.setPosition( x0 - x, 0 );
//			ra.setPosition( y0 - y, 1 );
			ra.get().inc();

			// 5th octant.
			ra.setPosition( x0 + y, 0 );
			ra.setPosition( y0 + x, 1 );
			ra.get().inc();

			// 6th octant.
			ra.setPosition( x0 - y, 0 );
//			ra.setPosition( y0 + x, 1 );
			ra.get().inc();

			// 7th octant.
			ra.setPosition( x0 + y, 0 );
			ra.setPosition( y0 - x, 1 );
			ra.get().inc();

			// 8th octant.
			ra.setPosition( x0 - y, 0 );
//			ra.setPosition( y0 - x, 1 );
			ra.get().inc();
		}

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
