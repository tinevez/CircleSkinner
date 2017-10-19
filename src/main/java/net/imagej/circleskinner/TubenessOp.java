package net.imagej.circleskinner;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.scijava.Cancelable;
import org.scijava.app.StatusService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;

import net.imagej.ops.special.computer.AbstractUnaryComputerOp;
import net.imagej.ops.special.hybrid.AbstractUnaryHybridCF;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gradient.HessianMatrix;
import net.imglib2.algorithm.linalg.eigen.TensorEigenValues;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.outofbounds.OutOfBoundsBorderFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

@Plugin( type = TubenessOp.class )
public class TubenessOp< T extends RealType< T > >
		extends AbstractUnaryHybridCF< RandomAccessibleInterval< T >, Img< DoubleType > >
		implements Cancelable
{

	@Parameter
	private ThreadService threadService;
	
	@Parameter
	private StatusService statusService;

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
	public Img< DoubleType > createOutput( final RandomAccessibleInterval< T > input )
	{
		final Img< DoubleType > tubeness = ops().create().img( input, new DoubleType() );
		return tubeness;
	}

	@Override
	public void compute( final RandomAccessibleInterval< T > input, final Img< DoubleType > tubeness )
	{
		cancelReason = null;
		
		final int numDimensions = input.numDimensions();
		// Sigmas in pixel units.
		final double[] sigmas = new double[ numDimensions ];
		for ( int d = 0; d < sigmas.length; d++ )
			sigmas[ d ] = sigma / calibration[ d ];
		
		/*
		 * Hessian.
		 */

		// Get a suitable image factory.
		final long[] gradientDims = new long[ numDimensions + 1 ];
		final long[] hessianDims = new long[ numDimensions + 1 ];
		for ( int d = 0; d < numDimensions; d++ )
		{
			hessianDims[ d ] = input.dimension( d );
			gradientDims[ d ] = input.dimension( d );
		}
		hessianDims[ numDimensions ] = numDimensions * ( numDimensions + 1 ) / 2;
		gradientDims[ numDimensions ] = numDimensions;
		final Dimensions hessianDimensions = FinalDimensions.wrap( hessianDims );
		final FinalDimensions gradientDimensions = FinalDimensions.wrap( gradientDims );
		final ImgFactory< DoubleType > factory = ops().create().imgFactory( hessianDimensions );
		final Img< DoubleType > hessian = factory.create( hessianDimensions, new DoubleType() );
		final Img< DoubleType > gradient = factory.create( gradientDimensions, new DoubleType() );
		final Img< DoubleType > gaussian = factory.create( input, new DoubleType() );
		
		// Handle multithreading.
		final int nThreads = Runtime.getRuntime().availableProcessors();
		final ExecutorService es = threadService.getExecutorService();

		try
		{
			// Hessian calculation.
			HessianMatrix.calculateMatrix(
					Views.extendBorder( input ),
					gaussian,
					gradient,
					hessian,
					new OutOfBoundsBorderFactory<>(),
					nThreads, es, sigma );

			statusService.showProgress( 1, 3 );
			if ( isCanceled() )
				return;

			// Hessian eigenvalues.
			final RandomAccessibleInterval< DoubleType > evs = TensorEigenValues.calculateEigenValuesSymmetric(
					hessian,
					TensorEigenValues.createAppropriateResultImg( hessian, factory, new DoubleType() ),
					nThreads, es );

			statusService.showProgress( 2, 3 );
			if ( isCanceled() )
				return;

			final AbstractUnaryComputerOp< Iterable< DoubleType >, DoubleType > method;
			switch ( numDimensions )
			{
			case 2:
				method = new Tubeness2D( sigma );
				break;
			case 3:
				method = new Tubeness3D( sigma );
			default:
				System.err.println( "Cannot compute tubeness for " + numDimensions + "D images." );
				return;
			}
			ops().transform().project( tubeness, evs, method, numDimensions );

			statusService.showProgress( 3, 3 );

			return;
		}
		catch ( final IncompatibleTypeException | InterruptedException | ExecutionException e )
		{
			e.printStackTrace();
			return;
		}
	}

	private static final class Tubeness2D extends AbstractUnaryComputerOp< Iterable< DoubleType >, DoubleType >
	{

		private double sigma;

		public Tubeness2D( final double sigma )
		{
			this.sigma = sigma;
		}

		@Override
		public void compute( final Iterable< DoubleType > input, final DoubleType output )
		{
			// Use just the largest one.
			final Iterator< DoubleType > it = input.iterator();
			it.next();
			final double val = it.next().get();
			if ( val >= 0. )
				output.setZero();
			else
				output.set( sigma * sigma * Math.abs( val ) );

		}
	}

	private static final class Tubeness3D extends AbstractUnaryComputerOp< Iterable< DoubleType >, DoubleType >
	{

		private double sigma;

		public Tubeness3D( final double sigma )
		{
			this.sigma = sigma;
		}

		@Override
		public void compute( final Iterable< DoubleType > input, final DoubleType output )
		{
			// Use the two largest ones.
			final Iterator< DoubleType > it = input.iterator();
			it.next();
			final double val1 = it.next().get();
			final double val2 = it.next().get();
			if ( val1 >= 0. || val2 >= 0. )
				output.setZero();
			else
				output.set( sigma * sigma * Math.sqrt( val1 * val2 ) );

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
