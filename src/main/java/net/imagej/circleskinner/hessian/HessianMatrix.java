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
package net.imagej.circleskinner.hessian;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.outofbounds.OutOfBoundsFactory;
import net.imglib2.parallel.Parallelization;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 *
 * Compute entries of n-dimensional Hessian matrix.
 *
 * @author Philipp Hanslovsky
 *
 */
public class HessianMatrix
{

	/**
	 *
	 * @param source
	 *            n-dimensional {@link RandomAccessible}. Must provide data at
	 *            all locations of the input interval plus a one pixel border in
	 *            all dimensions.
	 * @param interval
	 *            {@link Interval} that specifies the positions for which
	 *            hessian matrices should be computed.
	 * @param sigma
	 *            width of Gaussian smoothing
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @param factory
	 *            {@link ImgFactory} used for creating the intermediate and
	 *            result image.
	 * @param u
	 *            Variable necessary for creation of intermediate and result
	 *            image.
	 * @return n+1-dimensional {@link Img} holding linear representation of
	 *         symmetric Hessian matrix in last dimension (size n * ( n + 1 ) /
	 *         2): [h11, h12, ... , h1n, h22, h23, ... , hnn]
	 * @throws IncompatibleTypeException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > Img< U > calculateMatrix(
			final RandomAccessible< T > source,
			final Interval interval,
			final double sigma,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
			final ImgFactory< U > factory,
			final U u ) throws IncompatibleTypeException
	{
		final double[] sigmas = new double[ source.numDimensions() ];
		Arrays.fill( sigmas, sigma );
		return calculateMatrix( source, interval, sigmas, outOfBounds, factory, u );
	}

	/**
	 *
	 * @param source
	 *            n-dimensional {@link RandomAccessible}. Must provide data at
	 *            all locations of the input interval plus a one pixel border in
	 *            all dimensions.
	 * @param interval
	 *            {@link Interval} that specifies the positions for which
	 *            hessian matrices should be computed.
	 * @param sigma
	 *            width of Gaussian smoothing
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @param factory
	 *            {@link ImgFactory} used for creating the intermediate and
	 *            result image.
	 * @param nThreads
	 *            number of threads to use.
	 * @return n+1-dimensional {@link Img} holding linear representation of
	 *         symmetric Hessian matrix in last dimension (size n * ( n + 1 ) /
	 *         2): [h11, h12, ... , h1n, h22, h23, ... , hnn]
	 * @throws IncompatibleTypeException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > Img< U > calculateMatrix(
			final RandomAccessible< T > source,
			final Interval interval,
			final double sigma,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
			final ImgFactory< U > factory,
			final U u,
			final int nThreads ) throws IncompatibleTypeException
	{
		final double[] sigmas = new double[ source.numDimensions() ];
		Arrays.fill( sigmas, sigma );
		return calculateMatrix( source, interval, sigmas, outOfBounds, factory, nThreads );
	}

	/**
	 *
	 * @param source
	 *            n-dimensional {@link RandomAccessible}. Must provide data at
	 *            all locations of the input interval plus a one pixel border in
	 *            all dimensions.
	 * @param interval
	 *            {@link Interval} that specifies the positions for which
	 *            hessian matrices should be computed.
	 * @param sigma
	 *            width of Gaussian smoothing
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @param factory
	 *            {@link ImgFactory} used for creating the intermediate and
	 *            result image.
	 * @param nThreads
	 *            Number of threads/workers used for parallel computation of
	 *            eigenvalues.
	 * @param es
	 *            {@link ExecutorService} providing workers for parallel
	 *            computation. Service is managed (created, shutdown) by caller.
	 * @return n+1-dimensional {@link Img} holding linear representation of
	 *         symmetric Hessian matrix in last dimension (size n * ( n + 1 ) /
	 *         2): [h11, h12, ... , h1n, h22, h23, ... , hnn]
	 * @throws IncompatibleTypeException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > Img< U > calculateMatrix(
			final RandomAccessible< T > source,
			final Interval interval,
			final double sigma,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
			final ImgFactory< U > factory,
			final int nThreads,
			final ExecutorService es ) throws IncompatibleTypeException
	{
		final double[] sigmas = new double[ source.numDimensions() ];
		Arrays.fill( sigmas, sigma );
		return calculateMatrix( source, interval, sigmas, outOfBounds, factory, nThreads, es );
	}

	/**
	 *
	 * @param source
	 *            n-dimensional {@link RandomAccessible}. Must provide data at
	 *            all locations of the input interval plus a one pixel border in
	 *            all dimensions.
	 * @param interval
	 *            {@link Interval} that specifies the positions for which
	 *            hessian matrices should be computed.
	 * @param sigma
	 *            width of Gaussian smoothing (isotropy not required)
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @param factory
	 *            {@link ImgFactory} used for creating the intermediate and
	 *            result image.
	 * @return n+1-dimensional {@link Img} holding linear representation of
	 *         symmetric Hessian matrix in last dimension (size n * ( n + 1 ) /
	 *         2): [h11, h12, ... , h1n, h22, h23, ... , hnn]
	 * @throws IncompatibleTypeException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > Img< U > calculateMatrix(
			final RandomAccessible< T > source,
			final Interval interval,
			final double[] sigma,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
			final ImgFactory< U > factory,
			final U u ) throws IncompatibleTypeException
	{
		final int nThreads = Runtime.getRuntime().availableProcessors();
		return calculateMatrix( source, interval, sigma, outOfBounds, factory, nThreads );
	}

	/**
	 *
	 * @param source
	 *            n-dimensional {@link RandomAccessible}. Must provide data at
	 *            all locations of the input interval plus a one pixel border in
	 *            all dimensions.
	 * @param interval
	 *            {@link Interval} that specifies the positions for which
	 *            hessian matrices should be computed.
	 * @param sigma
	 *            width of Gaussian smoothing (isotropy not required)
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @param factory
	 *            {@link ImgFactory} used for creating the intermediate and
	 *            result image.
	 * @param nThreads
	 *            Number of threads/workers used for parallel computation of
	 *            eigenvalues.
	 * @return n+1-dimensional {@link Img} holding linear representation of
	 *         symmetric Hessian matrix in last dimension (size n * ( n + 1 ) /
	 *         2): [h11, h12, ... , h1n, h22, h23, ... , hnn]
	 * @throws IncompatibleTypeException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > Img< U > calculateMatrix(
			final RandomAccessible< T > source,
			final Interval interval,
			final double[] sigma,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
			final ImgFactory< U > factory,
			final int nThreads ) throws IncompatibleTypeException
	{
		final ExecutorService es = Executors.newFixedThreadPool( nThreads );
		final Img< U > hessianMatrix = calculateMatrix( source, interval, sigma, outOfBounds, factory, nThreads, es );
		es.shutdown();
		return hessianMatrix;
	}

	/**
	 *
	 * @param source
	 *            n-dimensional {@link RandomAccessible}. Must provide data at
	 *            all locations of the input interval plus a one pixel border in
	 *            all dimensions.
	 * @param interval
	 *            {@link Interval} that specifies the positions for which
	 *            hessian matrices should be computed.
	 * @param sigma
	 *            width of Gaussian smoothing (isotropy not required)
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @param factory
	 *            {@link ImgFactory} used for creating the intermediate and
	 *            result image.
	 * @param nThreads
	 *            Number of threads/workers used for parallel computation of
	 *            eigenvalues.
	 * @param es
	 *            {@link ExecutorService} providing workers for parallel
	 *            computation. Service is managed (created, shutdown) by caller.
	 * @return n+1-dimensional {@link Img} holding linear representation of
	 *         symmetric Hessian matrix in last dimension (size n * ( n + 1 ) /
	 *         2): [h11, h12, ... , h1n, h22, h23, ... , hnn]
	 * @throws IncompatibleTypeException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > Img< U > calculateMatrix(
			final RandomAccessible< T > source,
			final Interval interval,
			final double[] sigma,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
			final ImgFactory< U > factory,
			final int nThreads,
			final ExecutorService es ) throws IncompatibleTypeException
	{
		final int nDim = interval.numDimensions();
		final int nTargetDim = nDim + 1;
		final long[] dimensions = new long[ nTargetDim ];
		final long[] min = new long[ nTargetDim ];
		final long[] max = new long[ nTargetDim ];

		for ( int d = 0; d < nDim; ++d )
		{
			dimensions[ d ] = interval.dimension( d );
			min[ d ] = interval.min( d );
			max[ d ] = interval.max( d );
		}
		dimensions[ nDim ] = nDim * ( nDim + 1 ) / 2;
		min[ nDim ] = 0;
		max[ nDim ] = dimensions[ nDim ] - 1;

		final long[] gradientDim = dimensions.clone();
		gradientDim[ nDim ] = nDim;

		final Img< U > gaussianConvolved = factory.create( interval );
		final Img< U > gradient = factory.create( gradientDim );
		final Img< U > hessianMatrix = factory.create( dimensions );

		calculateMatrix( source, gaussianConvolved, gradient, hessianMatrix, sigma, outOfBounds, nThreads, es );

		return hessianMatrix;
	}

	/**
	 *
	 * @param source
	 *            n-dimensional {@link RandomAccessible}. Must provide data at
	 *            all locations of result/intermediate images plus a one pixel
	 *            border in all dimensions.
	 * @param gaussianConvolved
	 *            n-dimensional {@link RandomAccessibleInterval} for storing the
	 *            smoothed source
	 * @param gradient
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            the gradients along all axes of the smoothed source (size of
	 *            last dimension is n)
	 * @param hessianMatrix
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            all second partial derivatives (size of last dimension is n *
	 *            ( n + 1 ) / 2)
	 * @param sigma
	 *            width of Gaussian smoothing
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @throws IncompatibleTypeException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > void calculateMatrix(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< U > gaussianConvolved,
			final RandomAccessibleInterval< U > gradient,
			final RandomAccessibleInterval< U > hessianMatrix,
			final double sigma,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds ) throws IncompatibleTypeException
	{
		final double[] sigmas = new double[ source.numDimensions() ];
		Arrays.fill( sigmas, sigma );
		calculateMatrix( source, gaussianConvolved, gradient, hessianMatrix, sigmas, outOfBounds );
	}

	/**
	 *
	 * @param source
	 *            n-dimensional {@link RandomAccessible}. Must provide data at
	 *            all locations of result/intermediate images plus a one pixel
	 *            border in all dimensions.
	 * @param gaussianConvolved
	 *            n-dimensional {@link RandomAccessibleInterval} for storing the
	 *            smoothed source
	 * @param gradient
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            the gradients along all axes of the smoothed source (size of
	 *            last dimension is n)
	 * @param hessianMatrix
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            all second partial derivatives (size of last dimension is n *
	 *            ( n + 1 ) / 2)
	 * @param sigma
	 *            width of Gaussian smoothing
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @param nThreads
	 *            Number of threads/workers used for parallel computation of
	 *            eigenvalues.
	 * @throws IncompatibleTypeException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > void calculateMatrix(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< U > gaussianConvolved,
			final RandomAccessibleInterval< U > gradient,
			final RandomAccessibleInterval< U > hessianMatrix,
			final double sigma,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
			final int nThreads ) throws IncompatibleTypeException
	{
		final double[] sigmas = new double[ source.numDimensions() ];
		Arrays.fill( sigmas, sigma );
		calculateMatrix( source, gaussianConvolved, gradient, hessianMatrix, sigmas, outOfBounds, nThreads );
	}

	/**
	 *
	 * @param source
	 *            n-dimensional {@link RandomAccessible}. Must provide data at
	 *            all locations of result/intermediate images plus a one pixel
	 *            border in all dimensions.
	 * @param gaussianConvolved
	 *            n-dimensional {@link RandomAccessibleInterval} for storing the
	 *            smoothed source
	 * @param gradient
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            the gradients along all axes of the smoothed source (size of
	 *            last dimension is n)
	 * @param hessianMatrix
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            all second partial derivatives (size of last dimension is n *
	 *            ( n + 1 ) / 2)
	 * @param sigma
	 *            width of Gaussian smoothing
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @param nThreads
	 *            Number of threads/workers used for parallel computation of
	 *            eigenvalues.
	 * @param es
	 *            {@link ExecutorService} providing workers for parallel
	 *            computation. Service is managed (created, shutdown) by caller.
	 * @throws IncompatibleTypeException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > void calculateMatrix(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< U > gaussianConvolved,
			final RandomAccessibleInterval< U > gradient,
			final RandomAccessibleInterval< U > hessianMatrix,
			final double sigma,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
			final int nThreads,
			final ExecutorService es ) throws IncompatibleTypeException
	{
		final double[] sigmas = new double[ source.numDimensions() ];
		Arrays.fill( sigmas, sigma );
		calculateMatrix( source, gaussianConvolved, gradient, hessianMatrix, sigmas, outOfBounds, nThreads, es );
	}

	/**
	 *
	 * @param source
	 *            n-dimensional {@link RandomAccessible}. Must provide data at
	 *            all locations of result/intermediate images plus a one pixel
	 *            border in all dimensions.
	 * @param gaussianConvolved
	 *            n-dimensional {@link RandomAccessibleInterval} for storing the
	 *            smoothed source
	 * @param gradient
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            the gradients along all axes of the smoothed source (size of
	 *            last dimension is n)
	 * @param hessianMatrix
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            all second partial derivatives (size of last dimension is n *
	 *            ( n + 1 ) / 2)
	 * @param sigma
	 *            width of Gaussian smoothing (isotropy not required)
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @throws IncompatibleTypeException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > void calculateMatrix(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< U > gaussianConvolved,
			final RandomAccessibleInterval< U > gradient,
			final RandomAccessibleInterval< U > hessianMatrix,
			final double[] sigma,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds ) throws IncompatibleTypeException
	{
		final int nThreads = Runtime.getRuntime().availableProcessors();
		calculateMatrix( source, gaussianConvolved, gradient, hessianMatrix, sigma, outOfBounds, nThreads );
	}

	/**
	 *
	 * @param source
	 *            n-dimensional {@link RandomAccessible}. Must provide data at
	 *            all locations of result/intermediate images plus a one pixel
	 *            border in all dimensions.
	 * @param gaussianConvolved
	 *            n-dimensional {@link RandomAccessibleInterval} for storing the
	 *            smoothed source
	 * @param gradient
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            the gradients along all axes of the smoothed source (size of
	 *            last dimension is n)
	 * @param hessianMatrix
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            all second partial derivatives (size of last dimension is n *
	 *            ( n + 1 ) / 2)
	 * @param sigma
	 *            width of Gaussian smoothing (isotropy not required)
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @param nThreads
	 *            Number of threads/workers used for parallel computation of
	 *            eigenvalues.
	 * @throws IncompatibleTypeException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > void calculateMatrix(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< U > gaussianConvolved,
			final RandomAccessibleInterval< U > gradient,
			final RandomAccessibleInterval< U > hessianMatrix,
			final double[] sigma,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
			final int nThreads ) throws IncompatibleTypeException
	{
		final ExecutorService es = Executors.newFixedThreadPool( nThreads );
		calculateMatrix( source, gaussianConvolved, gradient, hessianMatrix, sigma, outOfBounds, nThreads, es );
		es.shutdown();
	}

	/**
	 *
	 * @param source
	 *            n-dimensional {@link RandomAccessible}. Must provide data at
	 *            all locations of result/intermediate images plus a one pixel
	 *            border in all dimensions.
	 * @param gaussianConvolved
	 *            n-dimensional {@link RandomAccessibleInterval} for storing the
	 *            smoothed source
	 * @param gradient
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            the gradients along all axes of the smoothed source (size of
	 *            last dimension is n)
	 * @param hessianMatrix
	 *            n+1-dimensional {@link RandomAccessibleInterval} for storing
	 *            all second partial derivatives (size of last dimension is n *
	 *            ( n + 1 ) / 2)
	 * @param sigma
	 *            width of Gaussian smoothing (isotropy not required)
	 * @param outOfBounds
	 *            {@link OutOfBoundsFactory} that specifies how out of bound
	 *            pixels of intermediate results should be handled (necessary
	 *            for gradient computation).
	 * @param nThreads
	 *            Number of threads/workers used for parallel computation of
	 *            eigenvalues.
	 * @param es
	 *            {@link ExecutorService} providing workers for parallel
	 *            computation. Service is managed (created, shutdown) by caller.
	 * @throws IncompatibleTypeException
	 */
	public static < T extends RealType< T >, U extends RealType< U > > void calculateMatrix(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< U > gaussianConvolved,
			final RandomAccessibleInterval< U > gradient,
			final RandomAccessibleInterval< U > hessianMatrix,
			final double[] sigma,
			final OutOfBoundsFactory< U, ? super RandomAccessibleInterval< U > > outOfBounds,
			final int nThreads,
			final ExecutorService es ) throws IncompatibleTypeException
	{

		final int nDim = source.numDimensions();

		Parallelization.runWithExecutor( es, () -> Gauss3.gauss( sigma, source, gaussianConvolved ) );

		for ( long d = 0; d < nDim; ++d )
		{
			try
			{
				PartialDerivative.gradientCentralDifferenceParallel( Views.extend( gaussianConvolved, outOfBounds ), Views.hyperSlice( gradient, nDim, d ), ( int ) d, nThreads, es );
			}
			catch ( final InterruptedException e )
			{
				e.printStackTrace();
			}
			catch ( final ExecutionException e )
			{
				e.printStackTrace();
			}
		}

		int count = 0;
		for ( long d1 = 0; d1 < nDim; ++d1 )
		{
			final IntervalView< U > hs1 = Views.hyperSlice( gradient, nDim, d1 );
			for ( long d2 = d1; d2 < nDim; ++d2 )
			{
				final IntervalView< U > hs2 = Views.hyperSlice( hessianMatrix, nDim, count );
				try
				{
					PartialDerivative.gradientCentralDifferenceParallel( Views.extend( hs1, outOfBounds ), hs2, ( int ) d2, nThreads, es );
				}
				catch ( final InterruptedException e )
				{
					e.printStackTrace();
				}
				catch ( final ExecutionException e )
				{
					e.printStackTrace();
				}
				++count;
			}
		}
	}

}
