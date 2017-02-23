package net.imagej.circleskinner;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;
import org.scijava.ui.UIService;

import ij.ImagePlus;
import ij.gui.Overlay;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.circleskinner.hough.HoughCircle;
import net.imagej.circleskinner.hough.HoughDetectorOp;
import net.imagej.circleskinner.hough.HoughTransformOp;
import net.imagej.circleskinner.util.HoughCircleOverlay;
import net.imagej.ops.OpService;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
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

		final ImagePlus imp = ImageJFunctions.show( img, "Results" );
		Overlay overlay = imp.getOverlay();
		if ( null == overlay )
		{
			overlay = new Overlay();
			imp.setOverlay( overlay );
		}
		final HoughCircleOverlay circleOverlay = new HoughCircleOverlay( imp );
		overlay.add( circleOverlay, "Hough circles" );

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
			final List< HoughCircle > circles = processChannel( img.getImg() );
			circleOverlay.setCircles( circles, 0 );
		}
		else
		{
			for ( int c = 0; c < source.getChannels(); c++ )
			{
				@SuppressWarnings( "unchecked" )
				final IntervalView< T > channel = ( IntervalView< T > ) Views.hyperSlice( source.getImgPlus().getImg(), cId, c );
				final List< HoughCircle > circles = processChannel( channel );
				circleOverlay.setCircles( circles, c );
			}
		}

	}

	private List< HoughCircle > processChannel( final RandomAccessibleInterval< T > channel )
	{
		final double sigma = circleThickness / 2. / Math.sqrt( channel.numDimensions() );

		final double minRadius = 50.;
		final double maxRadius = 100.;
		final double stepRadius = 2.;

		/*
		 * Filter using tubeness.
		 */

		@SuppressWarnings( "rawtypes" )
		final UnaryFunctionOp< RandomAccessibleInterval< T >, RandomAccessibleInterval > tubenessOp =
				Functions.unary( ops, TubenessOp.class, RandomAccessibleInterval.class,
						channel, sigma, Util.getArrayFromValue( 1., channel.numDimensions() ) );
		@SuppressWarnings( "unchecked" )
		final Img< DoubleType > H = ( Img< DoubleType > ) tubenessOp.compute1( channel );

		/*
		 * Threshold with Otsu.
		 */

		final IterableInterval< BitType > thresholded = ops.threshold().otsu( H );

		/*
		 * Hough transform
		 */

		@SuppressWarnings( "rawtypes" )
		final UnaryFunctionOp< IterableInterval< BitType >, RandomAccessibleInterval > houghTransformOp =
				Functions.unary( ops, HoughTransformOp.class, RandomAccessibleInterval.class,
						thresholded, minRadius, maxRadius, stepRadius );
		@SuppressWarnings( "unchecked" )
		final Img< DoubleType > voteImg = ( Img< DoubleType > ) houghTransformOp.compute1( thresholded );

		/*
		 * Detect maxima on vote image.
		 */

		@SuppressWarnings( "rawtypes" )
		final UnaryFunctionOp< Img< DoubleType >, List > houghDetectOp =
				Functions.unary( ops, HoughDetectorOp.class, List.class,
						voteImg, circleThickness, minRadius, stepRadius, sensitivity );
		@SuppressWarnings( "unchecked" )
		final List< HoughCircle > circles = houghDetectOp.compute1( voteImg );
		return circles;
	}

	public static void main( final String[] args ) throws IOException, InterruptedException, ExecutionException
	{
		final ImageJ ij = new net.imagej.ImageJ();
		ij.launch( args );
		final Object dataset = ij.io().open( "samples/ca-01.lsm" );
		ij.ui().show( dataset );
		ij.command().run( CircleSkinner.class, true ).get();
	}
}
