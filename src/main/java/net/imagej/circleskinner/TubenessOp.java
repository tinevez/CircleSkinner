package net.imagej.circleskinner;

import org.scijava.plugin.Parameter;

import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.RealComposite;

public class TubenessOp< T extends RealType< T > >
		extends AbstractUnaryFunctionOp< RandomAccessibleInterval< T >, RandomAccessibleInterval< RealComposite< DoubleType > > >
{

	/**
	 * Desired scale in sigma in physical units.
	 */
	@Parameter
	private double sigma;

	/**
	 * Pixel sizes in all dimension.
	 */
	@Parameter
	private double[] calibration;

	public TubenessOp( final double sigma, final double[] calibration )
	{
		this.sigma = sigma;
		this.calibration = calibration;
	}

	@Override
	public RandomAccessibleInterval< RealComposite< DoubleType > > compute1( final RandomAccessibleInterval< T > input )
	{
		
		final int numDimensions = input.numDimensions();
		// Sigmas in pixel units.
		final double[] sigmas = new double[ numDimensions ];
		for ( int d = 0; d < sigmas.length; d++ )
			sigmas[ d ] = sigma / calibration[ d ];
		
		/*
		 * Filter with a gaussian.
		 */

		final Img< DoubleType > filtered = ops().create().img( input );
		ops().filter().gauss( filtered, input, sigmas );

		/*
		 * Hessian.
		 */

		final long[] dims = new long[ numDimensions + 1 ];
		for ( int d = 0; d < numDimensions; d++ )
			dims[ d ] = input.dimension( d );
		dims[ numDimensions ] = numDimensions * ( numDimensions + 1 ) / 2;

		/*
		 * The last dimension stores the upper-right corner of the symmetric
		 * hessian matrix.
		 */
		final Img< DoubleType > hessian = ops().create().img( dims );
		final ExtendedRandomAccessibleInterval< DoubleType, Img< DoubleType > > extended = Views.extendMirrorSingle( filtered );
		for ( int d1 = 0; d1 < numDimensions; d1++ )
		{
			final Img< DoubleType > gradient = ops().create().img( filtered );
			PartialDerivative.gradientCentralDifference( extended, gradient, d1 );

			final ExtendedRandomAccessibleInterval< DoubleType, Img< DoubleType > > extendGradient = Views.extendMirrorDouble( gradient );
			for ( int d2 = d1; d2 < numDimensions; d2++ )
			{
				final long pos = d1 * numDimensions + d2;
				final IntervalView< DoubleType > hessian12 = Views.hyperSlice( hessian, numDimensions, pos );
				PartialDerivative.gradientCentralDifference( extendGradient, hessian12, d2 );
			}
		}

		final RandomAccessibleInterval< RealComposite< DoubleType > > H = Views.collapseReal( hessian );
		return H;
	}


}
