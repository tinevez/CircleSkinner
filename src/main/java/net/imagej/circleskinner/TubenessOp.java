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
package net.imagej.circleskinner;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;

import org.scijava.Cancelable;
import org.scijava.app.StatusService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;

import net.imagej.circleskinner.hessian.HessianMatrix;
import net.imagej.circleskinner.hessian.TensorEigenValues;
import net.imagej.ops.special.computer.AbstractUnaryComputerOp;
import net.imagej.ops.special.hybrid.AbstractUnaryHybridCF;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.outofbounds.OutOfBoundsBorderFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
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
		final long[] dims = new long[ numDimensions + 1 ];
		for ( int d = 0; d < numDimensions; d++ )
			dims[ d ] = input.dimension( d );
		dims[ numDimensions ] = numDimensions * ( numDimensions + 1 ) / 2;
		final Dimensions dimensions = FinalDimensions.wrap( dims );
		final ImgFactory< DoubleType > factory = Util.getArrayOrCellImgFactory( dimensions, new DoubleType() );
		
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
					factory,
					nThreads, es );

			statusService.showProgress( 1, 3 );
			if ( isCanceled() )
				return;

			// Hessian eigenvalues.
			final Img< DoubleType > evs = TensorEigenValues.calculateEigenValuesSymmetric(
					hessian,
					factory,
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
				break;
			default:
				System.err.println( "Cannot compute tubeness for " + numDimensions + "D images." );
				return;
			}
			ops().transform().project( tubeness, evs, method, numDimensions );

			statusService.showProgress( 3, 3 );

			return;
		}
		catch ( final IncompatibleTypeException e )
		{
			e.printStackTrace();
			return;
		}
	}

	private static final class Tubeness2D extends AbstractUnaryComputerOp< Iterable< DoubleType >, DoubleType >
	{

		private final double sigma;

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

		private final double sigma;

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
