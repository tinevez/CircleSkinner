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
package net.imagej.circleskinner.util;

import java.io.File;
import java.util.List;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import net.imagej.circleskinner.hough.HoughCircle;

public class PngExporter
{

	public static final void exportToPng( ImagePlus imp, final String saveFolder )
	{
		if ( !imp.isComposite() )
			imp = new CompositeImage( imp, CompositeImage.GRAYSCALE );
		else
			imp.setDisplayMode( CompositeImage.GRAYSCALE );

		final int nChannels = imp.getNChannels();
		for ( int c = 0; c < nChannels; c++ )
		{
			imp.setC( c + 1 );
			IJ.run( imp, "Enhance Contrast", "saturated=0.1" );

			final String name = imp.getTitle().substring( 0, imp.getTitle().lastIndexOf( '.' ) ) + "-channel_" + ( c + 1 ) + ".png";
			IJ.save( imp, new File( saveFolder, name ).getAbsolutePath() );
		}
	}

	public static final void exportToPng( ImagePlus imp, final String saveFolder, final List< HoughCircle > circles )
	{
		double maxSensitivity = Double.NEGATIVE_INFINITY;
		for ( final HoughCircle circle : circles )
		{
			if ( circle.getSensitivity() > maxSensitivity )
				maxSensitivity = circle.getSensitivity();
		}

		final Overlay overlay = new Overlay();
		imp.setOverlay( overlay );
		final HoughCircleOverlay circleOverlay = new HoughCircleOverlay( imp, maxSensitivity );
		overlay.add( circleOverlay, "Hough circles" );

		if ( !imp.isComposite() )
			imp = new CompositeImage( imp, CompositeImage.GRAYSCALE );
		else
			imp.setDisplayMode( CompositeImage.GRAYSCALE );

		final int nChannels = imp.getNChannels();
		for ( int c = 0; c < nChannels; c++ )
		{
			imp.setC( c + 1 );
			IJ.run( imp, "Enhance Contrast", "saturated=0.1" );

			// Add it to channel 0 to have it saved as PNG properly.
			circleOverlay.setCircles( circles );
			imp.updateAndDraw();

			final String name = imp.getTitle().substring( 0, imp.getTitle().lastIndexOf( '.' ) ) + "-channel_" + ( c + 1 ) + ".png";
			IJ.save( imp, new File( saveFolder, name ).getAbsolutePath() );
		}
	}
}
