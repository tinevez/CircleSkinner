package net.imagej.circleskinner.util;

import java.io.File;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;

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
}
