package net.imagej.circleskinner;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import features.TubenessProcessor;
import ij.ImageJ;
import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.axis.Axes;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;


@Plugin( type = Command.class, menuPath = "Plugins > Circle skinner" )
public class CircleSkinner< T extends RealType< T > > implements Command // ,
																			// Initializable
{
	@Parameter
	private Dataset source;

	@Parameter
	private UIService uiService;

//	@Parameter
//	private OpService ops;

	@Parameter
	private LogService log;

	/**
	 * The sigma for Tubeness filter, in pixel units.
	 */
	@Parameter( label = "Sigma tubeness" )
	private final double sigmaTubeness = 4.;

	private TubenessProcessor tubenessProcessor;

//	@Override
	public void initialize()
	{
		System.out.println( "I was initialized" ); // DEBUG
		tubenessProcessor = new TubenessProcessor( sigmaTubeness, false );
	}

	@Override
	public void run()
	{
		@SuppressWarnings( "unchecked" )
		final Img< T > img = ( Img< T > ) source.getImgPlus().getImg();

		// Find channel axis index.
		int cId = -1;
		for ( int d = 0; d < source.numDimensions(); d++ )
		{
			if (source.axis( d ) ==  Axes.CHANNEL )
			{
				cId =  d;
				break;
			}
		}

		if (cId < 0)
		{
			processChannel( img );
		}
		else
		{
			for ( int c = 0; c < source.getChannels(); c++ )
			{
				@SuppressWarnings( "unchecked" )
				final IntervalView< T > channel = ( IntervalView< T > ) Views.hyperSlice( source.getImgPlus().getImg(), cId, c );
				processChannel( channel );
			}
		}

	}

	private void processChannel( final RandomAccessibleInterval< T > channel )
	{
		final ImagePlus cimp = tubenessProcessor.generateImage( ImageJFunctions.wrap( channel, "Channel " + channel ) );

	}

	public static void main( final String[] args )
	{
		System.out.println( "Launching ImageJ" );
		final GraphicsEnvironment localGraphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
		System.out.println( localGraphicsEnvironment ); // DEBUG
		final GraphicsDevice defaultScreenDevice = localGraphicsEnvironment.getDefaultScreenDevice();
		System.out.println( defaultScreenDevice ); // DEBUG
		final GraphicsConfiguration defaultConfiguration = defaultScreenDevice.getDefaultConfiguration();
		System.out.println( defaultConfiguration ); // DEBUG

		ImageJ.main( args );
		System.out.println( "ImageJ launched" );
	}

}
