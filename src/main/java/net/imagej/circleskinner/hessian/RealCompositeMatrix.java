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

import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.linear.AbstractRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.composite.RealComposite;

/**
 *
 * @author Philipp Hanslovsky
 *
 *         {@link RealMatrix} that reads data from {@link RealComposite}
 *         (non-copy).
 *
 * @param <T>
 */
public class RealCompositeMatrix< T extends RealType< T > > extends AbstractRealMatrix
{

	protected final RealComposite< T > data;

	protected final int nRows;

	protected final int nCols;

	protected final int length;

	public RealCompositeMatrix( final RealComposite< T > data, final int nRows, final int nCols )
	{
		this( data, nRows, nCols, nRows * nCols );
	}

	public RealCompositeMatrix( final RealComposite< T > data, final int nRows, final int nCols, final int length )
	{
		super();

		assert length == expectedLength( nRows, nCols );

		this.data = data;
		this.nRows = nRows;
		this.nCols = nCols;
		this.length = length;
	}

	@Override
	public RealMatrix copy()
	{
		// Supposed to be a deep copy, cf apache docs:
		// http://commons.apache.org/proper/commons-math/apidocs/org/apache/commons/math3/linear/RealMatrix.html#copy()
		@SuppressWarnings( "unchecked" )
		final RealCompositeMatrix< T > result = ( RealCompositeMatrix< T > ) createMatrix( nRows, nCols );
		for ( int i = 0; i < length; ++i )
		{
			result.data.get( i ).set( this.data.get( i ) );
		}
		return result;
	}

	@Override
	public RealMatrix createMatrix( final int aNRows, final int aNCols ) throws NotStrictlyPositiveException
	{
		final T t = this.data.get( 0 );
		final int aLength = expectedLength( aNRows, aNCols );
		final Dimensions dims = new FinalDimensions( aLength );
		final Img< T > img = Util.getSuitableImgFactory( dims, t ).create( dims );

		final RealComposite< T > aData = new RealComposite<>( img.randomAccess(), aLength );
		return createMatrix( aData, aNRows, aNCols, aLength );
	}

	public < U extends RealType< U > > RealCompositeMatrix< U > createMatrix( final RealComposite< U > aData, final int aNRows, final int aNCols, final int aLength )
	{
		return new RealCompositeMatrix<>( aData, aNRows, aNCols, aLength );
	}

	@Override
	public int getColumnDimension()
	{
		return this.nCols;
	}

	@Override
	public double getEntry( final int row, final int col ) throws OutOfRangeException
	{
		if ( row < 0 || row >= this.nRows )
		{
			throw new OutOfRangeException( row, 0, this.nRows );
		}
		else if ( col < 0 || col >= this.nCols ) {
			throw new OutOfRangeException( col, 0, this.nCols );
		}
		final double val = data.get( rowAndColumnToLinear( row, col ) ).getRealDouble();

		return val;
	}

	@Override
	public int getRowDimension()
	{
		return this.nRows;
	}

	@Override
	public void setEntry( final int row, final int col, final double val ) throws OutOfRangeException
	{
		if ( row < 0 || row >= this.nRows )
		{
			throw new OutOfRangeException( row, 0, this.nRows );
		}
		else if ( col < 0 || col >= this.nCols ) { throw new OutOfRangeException( col, 0, this.nCols ); }

		data.get( rowAndColumnToLinear( row, col ) ).setReal( val );

	}

	public int rowAndColumnToLinear( final int row, final int col )
	{
		return row * nCols + col;
	}

	public int expectedLength( final int aNRows, final int aNCols )
	{
		return aNRows * aNCols;
	}
}
