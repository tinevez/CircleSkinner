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
import org.apache.commons.math3.linear.RealMatrix;

import net.imglib2.type.numeric.RealType;
import net.imglib2.view.composite.RealComposite;

/**
 *
 * @author Philipp Hanslovsky
 *
 *         Square {@link RealMatrix} that reads data from {@link RealComposite}
 *         (non-copy).
 *
 * @param <T>
 */
public class RealCompositeSquareMatrix< T extends RealType< T > > extends RealCompositeMatrix< T >
{

	public RealCompositeSquareMatrix( final RealComposite< T > data, final int nRowsOrCols )
	{
		this( data, nRowsOrCols, nRowsOrCols * nRowsOrCols );
	}

	public RealCompositeSquareMatrix( final RealComposite< T > data, final int nRowsOrCols, final int length )
	{
		super( data, nRowsOrCols, nRowsOrCols, length );
	}

	@Override
	public RealMatrix createMatrix( final int aNRows, final int aNCols ) throws NotStrictlyPositiveException
	{
		if ( aNRows != aNCols ) { throw new UnsupportedOperationException( "Number of colums and number of rows must be the same!" ); }
		return super.createMatrix( aNRows, aNCols );
	}

	@Override
	public < U extends RealType< U > > RealCompositeMatrix< U > createMatrix( final RealComposite< U > aData, final int aNRows, final int aNCols, final int aLength )
	{
		return new RealCompositeSymmetricMatrix<>( aData, aNRows, aLength );
	}

}
