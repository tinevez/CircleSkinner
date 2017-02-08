package net.imagej.circleskinner;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;

import net.imagej.circleskinner.hessian.HessianMatrix;
import net.imagej.circleskinner.hessian.TensorEigenValues;
import net.imagej.ops.special.computer.AbstractUnaryComputerOp;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.outofbounds.OutOfBoundsBorderFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

@Plugin( type = TubenessOp.class )
public class TubenessOp< T extends RealType< T > >
		extends AbstractUnaryFunctionOp< RandomAccessibleInterval< T >, Img< DoubleType > >
{

	@Parameter
	private ThreadService threadService;
	

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

	@Override
	public Img< DoubleType > compute1( final RandomAccessibleInterval< T > input )
	{
		
		final int numDimensions = input.numDimensions();
		// Sigmas in pixel units.
		final double[] sigmas = new double[ numDimensions ];
		for ( int d = 0; d < sigmas.length; d++ )
			sigmas[ d ] = sigma / calibration[ d ];
		
		/*
		 * Hessian.
		 */

		// Get a suitable image factory.
		final long[] dims = new long[ numDimensions + 1 ];
		for ( int d = 0; d < numDimensions; d++ )
			dims[ d ] = input.dimension( d );
		dims[ numDimensions ] = numDimensions * ( numDimensions + 1 ) / 2;
		final Dimensions dimensions = FinalDimensions.wrap( dims );
		final ImgFactory< DoubleType > factory = ops().create().imgFactory( dimensions );
		
		// Handle multithreading.
		final int nThreads = Runtime.getRuntime().availableProcessors();
		final ExecutorService es = threadService.getExecutorService();

		try
		{
			// Hessian calculation.
			final Img< DoubleType > hessian = HessianMatrix.calculateMatrix(
					Views.extendBorder( input ),
					input, 
					sigmas, 
					new OutOfBoundsBorderFactory<>(), 
					factory, new DoubleType(), 
					nThreads, es );

			// Hessian eigenvalues.
			final Img< DoubleType > evs = TensorEigenValues.calculateEigenValuesSymmetric(
					hessian,
					factory, new DoubleType(),
					nThreads, es );

			// Tubeness is derived from largest eignevalues.
			final Img< DoubleType > tubeness = ops().create().img( input, new DoubleType() );
			final AbstractUnaryComputerOp< Iterable< DoubleType >, DoubleType > method;
			switch ( numDimensions )
			{
			case 2:
				method = new Tubeness2D();
				break;
			case 3:
				method = new Tubeness3D();
			default:
				System.err.println( "Cannot compute tubeness for " + numDimensions + "D images." );
				return null;
			}
			ops().transform().project( tubeness, evs, method, numDimensions );
			return tubeness;
		}
		catch ( final IncompatibleTypeException e )
		{
			e.printStackTrace();
			return null;
		}
	}

	private static final class Tubeness2D extends AbstractUnaryComputerOp< Iterable< DoubleType >, DoubleType >
	{

		@Override
		public void compute1( final Iterable< DoubleType > input, final DoubleType output )
		{
			// Use just the largest one.
			final Iterator< DoubleType > it = input.iterator();
			it.next();
			final double val = it.next().get();
			if ( val >= 0. )
				output.setZero();
			else
				output.set( Math.abs( val ) );

		}
	}

	private static final class Tubeness3D extends AbstractUnaryComputerOp< Iterable< DoubleType >, DoubleType >
	{

		@Override
		public void compute1( final Iterable< DoubleType > input, final DoubleType output )
		{
			// Use the two largest ones.
			final Iterator< DoubleType > it = input.iterator();
			it.next();
			final double val1 = it.next().get();
			final double val2 = it.next().get();
			if ( val1 >= 0. || val2 >= 0. )
				output.setZero();
			else
				output.set( Math.sqrt( val1 * val2 ) );

		}
	}

}
