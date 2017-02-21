package net.imagej.circleskinner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;
import org.scijava.ui.UIService;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.circleskinner.hough.HoughTransformOp;
import net.imagej.ops.OpService;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imglib2.IterableInterval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Sampler;
import net.imglib2.algorithm.localextrema.LocalExtrema;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
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

	@Parameter
	private ThreadService threadService;

	/**
	 * The circle thickness (crown thickness), in pixel units.
	 */
	@Parameter( label = "Circle thickness", min = "1", type = ItemIO.INPUT )
	private double circleThickness = 4.;

	@Parameter( label = "Circle detection sensitivity", min = "1", type = ItemIO.INPUT )
	private double sensitivity = 10.;

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
				break; // DEBUG
			}
		}

	}

	private void processChannel( final RandomAccessibleInterval< T > channel )
	{
		final double sigma = circleThickness / 2. / Math.sqrt( channel.numDimensions() );

		final int minRadius = 50;
		final int maxRadius = 100;
		final int stepRadius = 4;

		/*
		 * Filter using tubeness.
		 */

		@SuppressWarnings( "rawtypes" )
		final UnaryFunctionOp< RandomAccessibleInterval< T >, RandomAccessibleInterval > tubenessOp =
				Functions.unary( ops, TubenessOp.class, RandomAccessibleInterval.class,
						channel, sigma, Util.getArrayFromValue( 1., channel.numDimensions() ) );
		@SuppressWarnings( "unchecked" )
		final Img< DoubleType > H = ( Img< DoubleType > ) tubenessOp.compute1( channel );
		uiService.show( "Tubeness", H ); // DEBUG

		/*
		 * Threshold with Otsu.
		 */

		final IterableInterval< BitType > thresholded = ops.threshold().otsu( H );
		uiService.show( "Thresholded", thresholded ); // DEBUG

		/*
		 * Hough transform
		 */

		System.out.print( "Computing Hough transform...." ); // DEBUG
		@SuppressWarnings( "rawtypes" )
		final UnaryFunctionOp< IterableInterval< BitType >, RandomAccessibleInterval > houghTransformOp =
				Functions.unary( ops, HoughTransformOp.class, RandomAccessibleInterval.class,
						thresholded, minRadius, maxRadius, stepRadius );
		@SuppressWarnings( "unchecked" )
		final Img< DoubleType > voteImg = ( Img< DoubleType > ) houghTransformOp.compute1( thresholded );
		uiService.show( "VoteImg", voteImg ); // DEBUG
		System.out.println( " Done." ); // DEBUG

		/*
		 * Detect maxima on vote image.
		 */

//		final Img< DoubleType > dog = ops.create().img( voteImg );
//		ops.filter().dog( dog, voteImg, sigma, 0.95 * sigma ); // Give max.
//		uiService.show( "DoG", dog );

		final MyLocalExtremaCheck check = new MyLocalExtremaCheck( voteImg.randomAccess( voteImg ), minRadius, stepRadius, circleThickness, sensitivity );
		final ExecutorService es = threadService.getExecutorService();
		final ArrayList< Point > maxima = LocalExtrema.findLocalExtrema( voteImg, check, es );
		System.out.println( maxima ); // DEBUG

		/*
		 * Localize maxima.
		 */


	}

	public static void main( final String[] args ) throws IOException
	{
		final ImageJ ij = new net.imagej.ImageJ();
		ij.launch( args );
		final Object dataset = ij.io().open( "samples/ca-01.lsm" );
		ij.ui().show( dataset );
	}

	private static class MyLocalExtremaCheck implements LocalExtrema.LocalNeighborhoodCheck< Point, DoubleType >
	{

		private RandomAccess< DoubleType > reference;

		private int minRadius;

		private int stepRadius;

		private double thickness;

		private double sensitivity;

		public MyLocalExtremaCheck( final RandomAccess< DoubleType > reference, final int minRadius, final int stepRadius, final double thickness, final double sensitivity )
		{
			this.minRadius = minRadius;
			this.stepRadius = stepRadius;
			this.reference = reference;
			this.thickness = thickness;
			this.sensitivity = sensitivity;
		}

		@Override
		public < C extends Localizable & Sampler< DoubleType > > Point check( final C center, final Neighborhood< DoubleType > neighborhood )
		{
			// What radius is the center on?
			final double i = center.getDoublePosition( center.numDimensions() - 1 );
			// Determine sensible threshold, assuming that the center has been
			// voted a certain number of times.
			final double radius = minRadius + i * stepRadius;
			final double threshold = 2. * Math.PI * radius * thickness / sensitivity;

			reference.setPosition( center );
			if ( reference.get().get() < threshold )
				return null;

			for ( final DoubleType d : neighborhood )
				if ( d.compareTo( center.get() ) >= 0 )
					return null;

			return new Point( center );
		}

	}
}
