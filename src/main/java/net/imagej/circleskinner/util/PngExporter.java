package net.imagej.circleskinner.util;

import java.io.File;
import java.util.List;
import java.util.Map;

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

	public static final void exportToPng( ImagePlus imp, final String saveFolder, final Map< Integer, List< HoughCircle > > circles )
	{
		double maxSensitivity = Double.NEGATIVE_INFINITY;
		for ( final Integer channelIndex : circles.keySet() )
		{
			for ( final HoughCircle circle : circles.get( channelIndex ) )
			{
				if ( circle.getSensitivity() > maxSensitivity )
					maxSensitivity = circle.getSensitivity();
			}
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
			circleOverlay.setCircles( circles.get( Integer.valueOf( c ) ), 0 );
			imp.updateAndDraw();

			final String name = imp.getTitle().substring( 0, imp.getTitle().lastIndexOf( '.' ) ) + "-channel_" + ( c + 1 ) + ".png";
			IJ.save( imp, new File( saveFolder, name ).getAbsolutePath() );
		}
	}
}
