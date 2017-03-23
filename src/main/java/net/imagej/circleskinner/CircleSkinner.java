package net.imagej.circleskinner;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;
import org.scijava.ui.UIService;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.measure.ResultsTable;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.circleskinner.analyze.CircleAnalyzerOp;
import net.imagej.circleskinner.hough.HoughCircle;
import net.imagej.circleskinner.hough.HoughCircle.Stats;
import net.imagej.circleskinner.hough.HoughDetectorOp;
import net.imagej.circleskinner.hough.HoughTransformOp;
import net.imagej.circleskinner.util.HoughCircleOverlay;
import net.imagej.ops.OpService;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.inplace.Inplaces;
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
	private static final String SOURCE_NAME_COLUMN = "Image";
	private static final String CHANEL_COLUMN = "Channel";
	private static final String CIRCLE_ID_COLUMN = "Circle #";
	private static final String CIRCLE_X_COLUMN = "X (pixels)";
	private static final String CIRCLE_Y_COLUMN = "Y (pixels)";
	private static final String CIRCLE_RADIUS_COLUMN = "R (pixels)";
	private static final String CIRCLE_THICKNESS_COLUMN = "Thickness (pixels)";
	private static final String CIRCLE_SENSITIVITY_COLUMN = "Sensitivity";
	private static final String CIRCLE_MEAN_COLUMN = "Mean";
	private static final String CIRCLE_STD_COLUMN = "Std";
	private static final String CIRCLE_N_COLUMN = "N";
	private static final String CIRCLE_MEDIAN_COLUMN = "Median";

	@Parameter
	private Dataset source;

	@Parameter
	private UIService uiService;

	@Parameter
	private OpService ops;

	@Parameter
	private ThreadService threadService;

	@Parameter
	private StatusService statusService;

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

		// Prepare results table.
		final ResultsTable table = new ResultsTable();

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

		// Process each channel.
		if (cId < 0)
		{
			final List< HoughCircle > circles = processChannel( img.getImg() );
			circleOverlay.setCircles( circles, 0 );
			appendResults( circles, table, 1 );
		}
		else
		{
			for ( int c = 0; c < source.getChannels(); c++ )
			{
				@SuppressWarnings( "unchecked" )
				final IntervalView< T > channel = ( IntervalView< T > ) Views.hyperSlice( source.getImgPlus().getImg(), cId, c );
				final List< HoughCircle > circles = processChannel( channel );
				circleOverlay.setCircles( circles, c );
				appendResults( circles, table, c + 1 );
			}
		}

		table.show( "Circle statistics for " + source.getName() );
	}

	private void appendResults( final List< HoughCircle > circles, final ResultsTable table, final int channelNumber )
	{
		int circleId = 0;
		for ( final HoughCircle circle : circles )
		{
			table.incrementCounter();

			table.addValue( CHANEL_COLUMN, channelNumber );
			table.addValue( SOURCE_NAME_COLUMN, source.getName() );

			table.addValue( CIRCLE_ID_COLUMN, ++circleId );
			table.addValue( CIRCLE_X_COLUMN, circle.getDoublePosition( 0 ) );
			table.addValue( CIRCLE_Y_COLUMN, circle.getDoublePosition( 1 ) );
			table.addValue( CIRCLE_RADIUS_COLUMN, circle.getRadius() );
			table.addValue( CIRCLE_THICKNESS_COLUMN, circle.getThickness() );
			table.addValue( CIRCLE_SENSITIVITY_COLUMN, circle.getSensitivity() );

			final Stats stats = circle.getStats();
			if (null == stats)
				continue;

			table.addValue( CIRCLE_MEAN_COLUMN, stats.mean );
			table.addValue( CIRCLE_STD_COLUMN, stats.std );
			table.addValue( CIRCLE_N_COLUMN, stats.N );
			table.addValue( CIRCLE_MEDIAN_COLUMN, stats.median );

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

		statusService.showStatus( "Filtering..." );

		@SuppressWarnings( { "rawtypes", "unchecked" } )
		final TubenessOp< T > tubenessOp =
				( TubenessOp ) Functions.unary( ops, TubenessOp.class, RandomAccessibleInterval.class,
						channel, sigma, Util.getArrayFromValue( 1., channel.numDimensions() ) );
		final Img< DoubleType > H = tubenessOp.calculate( channel );

		/*
		 * Threshold with Otsu.
		 */

		statusService.showStatus( "Thresholding..." );

		final IterableInterval< BitType > thresholded = ops.threshold().otsu( H );

		/*
		 * Hough transform
		 */

		statusService.showStatus( "Computing Hough transform..." );

		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final HoughTransformOp< BitType > houghTransformOp =
				( HoughTransformOp ) Functions.unary( ops, HoughTransformOp.class, RandomAccessibleInterval.class,
						thresholded, minRadius, maxRadius, stepRadius );
		final Img< DoubleType > voteImg = houghTransformOp.calculate( thresholded );

		/*
		 * Detect maxima on vote image.
		 */

		statusService.showStatus( "Detecting circles..." );

		@SuppressWarnings( "rawtypes" )
		final HoughDetectorOp houghDetectOp =
				( HoughDetectorOp ) Functions.unary( ops, HoughDetectorOp.class, List.class,
						voteImg, circleThickness, minRadius, stepRadius, sensitivity );
		@SuppressWarnings( "unchecked" )
		final List< HoughCircle > circles = houghDetectOp.calculate( voteImg );

		/*
		 * Analyze circles.
		 */
		
		@SuppressWarnings( { "rawtypes", "unchecked" } )
		final CircleAnalyzerOp< T > circleAnalyzerOp =
				( CircleAnalyzerOp ) Inplaces.binary1( ops, CircleAnalyzerOp.class, circles, channel );
		circleAnalyzerOp.run();

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
