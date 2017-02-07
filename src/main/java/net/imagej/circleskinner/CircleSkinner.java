package net.imagej.circleskinner;

import java.io.IOException;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import features.TubenessProcessor;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.axis.Axes;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.RealComposite;


@Plugin( type = Command.class, menuPath = "Plugins > Circle skinner" )
public class CircleSkinner< T extends RealType< T > > implements Command
{
	@Parameter
	private Dataset source;

	@Parameter
	private UIService uiService;

	@Parameter
	private OpService ops;

	@Parameter
	private LogService log;

	/**
	 * The sigma for Tubeness filter, in pixel units.
	 */
	@Parameter( label = "Sigma tubeness" )
	private double sigmaTubeness = 4.;

	private TubenessProcessor tubenessProcessor;


	@Override
	public void run()
	{
		tubenessProcessor = new TubenessProcessor( sigmaTubeness, false );

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

				break; // DEBUG
			}
		}

	}

	private void processChannel( final RandomAccessibleInterval< T > channel )
	{
		@SuppressWarnings( "unchecked" )
		final TubenessOp< T > op = ops.op( TubenessOp.class, channel, sigmaTubeness, Util.getArrayFromValue( 1., channel.numDimensions() ) );
		final RandomAccessibleInterval< RealComposite< DoubleType > > H = op.compute1( channel );
		uiService.show( H );
	}

	public static void main( final String[] args ) throws IOException
	{
		final ImageJ ij = new net.imagej.ImageJ();
		ij.launch( args );
		final Object dataset = ij.io().open( "samples/ca-01.lsm" );
		ij.ui().show( dataset );
	}

}
