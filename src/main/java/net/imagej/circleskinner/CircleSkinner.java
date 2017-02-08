package net.imagej.circleskinner;

import java.io.IOException;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.ops.OpService;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;


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

	@Override
	public void run()
	{
		@SuppressWarnings( "unchecked" )
		final ImgPlus< T > img = ( ImgPlus< T > ) source.getImgPlus();
		// Find channel axis index.
		int cId = -1;
		for ( int d = 0; d < img.numDimensions(); d++ )
		{
			if ( img.axis( d ).type().equals( Axes.CHANNEL ) )
			{
				cId =  d;
				break;
			}
		}

		if (cId < 0)
		{
			processChannel( img.getImg() );
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
		@SuppressWarnings( "rawtypes" )
		final UnaryFunctionOp< RandomAccessibleInterval< T >, RandomAccessibleInterval > op =
				Functions.unary( ops, TubenessOp.class, RandomAccessibleInterval.class, channel, sigmaTubeness, Util.getArrayFromValue( 1., channel.numDimensions() ) );
		@SuppressWarnings( "unchecked" )
		final Img< DoubleType > H = ( Img< DoubleType > ) op.compute1( channel );
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
