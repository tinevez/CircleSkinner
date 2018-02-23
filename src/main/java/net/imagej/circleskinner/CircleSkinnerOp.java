package net.imagej.circleskinner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.scijava.Cancelable;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;
import org.scijava.ui.UIService;

import ij.measure.ResultsTable;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.circleskinner.analyze.CircleAnalyzerOp;
import net.imagej.circleskinner.gui.CircleSkinnerGUI;
import net.imagej.circleskinner.hough.HoughCircle;
import net.imagej.circleskinner.hough.HoughCircle.Stats;
import net.imagej.circleskinner.hough.HoughCircleDetectorOp;
import net.imagej.circleskinner.hough.HoughCircleDogDetectorOp;
import net.imagej.circleskinner.hough.HoughCircleLocalMaxDetectorOp;
import net.imagej.circleskinner.hough.HoughTransformOp;
import net.imagej.ops.OpService;
import net.imagej.ops.special.computer.AbstractUnaryComputerOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.inplace.Inplaces;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

@Plugin( type = CircleSkinnerOp.class )
public class CircleSkinnerOp< T extends RealType< T > > extends AbstractUnaryComputerOp< Dataset, ResultsTable > implements Cancelable
{
	private static final String SOURCE_NAME_COLUMN = "Image";
	private static final String CHANNEL_TEXT = "Ch";
	private static final String CIRCLE_ID_COLUMN = "Circle #";
	private static final String CIRCLE_X_COLUMN = "X (pixels)";
	private static final String CIRCLE_Y_COLUMN = "Y (pixels)";
	private static final String CIRCLE_RADIUS_COLUMN = "R (pixels)";
	private static final String CIRCLE_MEAN_COLUMN = "Mean";
	private static final String CIRCLE_STD_COLUMN = "Std";
	private static final String CIRCLE_N_COLUMN = "N";
	private static final String CIRCLE_MEDIAN_COLUMN = "Median";
	private static final String CIRCLE_THICKNESS_COLUMN = "Thickness (pixels)";
	private static final String CIRCLE_THRESHOLD_COLUMN = "Threshold adj.";
	public static final String CIRCLE_SENSITIVITY_COLUMN = "Sensitivity";

	/*
	 * SERVICES.
	 */

	@Parameter
	private UIService uiService;

	@Parameter
	private OpService ops;

	@Parameter
	private ThreadService threadService;

	@Parameter
	private StatusService statusService;

	/**
	 * This field will be set to be the op running the current (long)
	 * calculation, so that it can be canceled when the user calls
	 * {@link #cancel(String)}.
	 */
	private Cancelable cancelableOp;

	/*
	 * INPUT PARAMETERS.
	 */

	/**
	 * The segmentation channel index, 1-based (first is 0).
	 */
	@Parameter( label = "Segmentation channel", min = "0", type = ItemIO.INPUT )
	private long segmentationChannel = 1;

	/**
	 * The circle thickness (crown thickness), in pixel units.
	 */
	@Parameter( label = "Circle thickness", min = "1", type = ItemIO.INPUT )
	private int circleThickness = 9;

	@Parameter( label = "Threshold adjustment", min = "1", type = ItemIO.INPUT, description = "By how much (in percent) to adjust the automatic Otsu threshold after segmentation of the filtered image." )
	private double thresholdFactor = 100.;

	@Parameter( label = "Circle detection sensitivity", min = "1", type = ItemIO.INPUT )
	private double sensitivity = 100.;

	@Parameter( label = "Min circle radius (pixels)", min = "1", type = ItemIO.INPUT )
	private int minRadius = 50;

	@Parameter( label = "Max circle radius (pixels)", min = "1", type = ItemIO.INPUT )
	private int maxRadius = 100;

	@Parameter( label = "Radius step (pixels)", min = "1", type = ItemIO.INPUT )
	private int stepRadius = 2;

	@Parameter( label = "Max number of detections", min = "0", type = ItemIO.INPUT )
	private int maxNDetections = Integer.MAX_VALUE;

	@Parameter( label = "Detection method", required = false, type = ItemIO.INPUT )
	private DetectionMethod detectionMethod = DetectionMethod.FAST;

	@Parameter( label = "Show results table", required = false, type = ItemIO.INPUT )
	private boolean showResultsTable = false;

	@Parameter( label = "Keep last vote image", required = false, type = ItemIO.INPUT )
	private boolean doKeepVoteImg = false;


	/*
	 * OUTPUT PARAMETERS.
	 */

	@Parameter( label = "Detected circles", type = ItemIO.OUTPUT, description = "The list of detected circles in the source image." )
	private List< HoughCircle > circles;

	/**
	 * Storage for the vote image.
	 */
	private Img< DoubleType > voteImg;

	/**
	 * Stores the percentage (from 0 to 100) of pixels retained in the
	 * thresholded image.
	 */
	private double percentPixelsInThresholded;

	/*
	 * METHODS.
	 */

	public List< HoughCircle > getCircles()
	{
		return circles;
	}

	public Img< DoubleType > getVoteImg()
	{
		return voteImg;
	}

	public double getPercentPixelsInThresholded()
	{
		return percentPixelsInThresholded;
	}

	public static final ResultsTable createResulsTable()
	{
		final ResultsTable table = new ResultsTable();
		return table;
	}

	@Override
	public void compute( final Dataset source, ResultsTable table )
	{
		cancelReason = null;
		voteImg = null;

		if ( null == table )
			table = createResulsTable();

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


		// Process proper channel.
		if (cId < 0)
		{
			circles = segmentCircles( img.getImg() );
			analyzeCircles( Collections.singletonList( img.getImg() ), circles );
			appendResults( circles, table, source.getName() );
		}
		else
		{
			final long nChannels = img.dimension( cId );
			final long targetChannel = ( segmentationChannel > nChannels ) ? 0 : segmentationChannel;

			/*
			 * Detect circles in target channel.
			 */

			@SuppressWarnings( "unchecked" )
			final IntervalView< T > aSegmentationChannel = ( IntervalView< T > ) Views.hyperSlice( source.getImgPlus().getImg(), cId, targetChannel );
			circles = segmentCircles( aSegmentationChannel );

			/*
			 * Measure in all channels.
			 */

			final List< RandomAccessibleInterval< T > > channels = new ArrayList<>( ( int ) source.getChannels() );
			for ( int c = 0; c < source.getChannels(); c++ )
			{
				@SuppressWarnings( "unchecked" )
				final IntervalView< T > analysisChannel = ( IntervalView< T > ) Views.hyperSlice( source.getImgPlus().getImg(), cId, c );
				channels.add( analysisChannel );
			}

			// Analyze.
			analyzeCircles( channels, circles );

			appendResults( circles, table, source.getName() );
		}

		statusService.clearStatus();
		if ( !doKeepVoteImg )
			voteImg = null;
	}

	private void appendResults( final List< HoughCircle > aCircles, final ResultsTable table, final String name )
	{
		int circleId = 0;
		for ( final HoughCircle circle : aCircles )
		{
			table.incrementCounter();

			table.addValue( SOURCE_NAME_COLUMN, name );

			table.addValue( CIRCLE_ID_COLUMN, ++circleId );
			table.addValue( CIRCLE_X_COLUMN, circle.getDoublePosition( 0 ) );
			table.addValue( CIRCLE_Y_COLUMN, circle.getDoublePosition( 1 ) );
			table.addValue( CIRCLE_RADIUS_COLUMN, circle.getRadius() );

			final Stats stats = circle.getStats();
			if ( null != stats )
			{
				final int nChannels = stats.mean.length;
				for ( int channel = 0; channel < nChannels; channel++ )
				{
					final String prefix = CHANNEL_TEXT + ( channel + 1 ) + " ";
					table.addValue( prefix + CIRCLE_MEAN_COLUMN, stats.mean[ channel ] );
					table.addValue( prefix + CIRCLE_STD_COLUMN, stats.std[ channel ] );
					table.addValue( prefix + CIRCLE_MEDIAN_COLUMN, stats.median[ channel ] );
				}
				table.addValue( CIRCLE_N_COLUMN, stats.N );
			}

			table.addValue( CIRCLE_SENSITIVITY_COLUMN, circle.getSensitivity() );
			table.addValue( CIRCLE_THICKNESS_COLUMN, circle.getThickness() );
			table.addValue( CIRCLE_THRESHOLD_COLUMN, thresholdFactor );

			if ( showResultsTable )
				table.show( CircleSkinnerGUI.PLUGIN_NAME + " Results" );
		}
	}

	/**
	 * Segments the specified channel and find the circles it contains according
	 * to the parameters of this Op. The {@link HoughCircle} objects have not
	 * been analyzed yet.
	 *
	 * @param aSegmentationChannel
	 *            the channel to segment as a RAI.
	 * @return the list of circles ordered by increasing sensitivity.
	 */
	private List< HoughCircle > segmentCircles( final RandomAccessibleInterval< T > aSegmentationChannel )
	{
		final double sigma = circleThickness / 2. / Math.sqrt( aSegmentationChannel.numDimensions() );

		/*
		 * Filter using tubeness.
		 */

		statusService.showStatus( "Filtering..." );

		@SuppressWarnings( { "rawtypes", "unchecked" } )
		final TubenessOp< T > tubenessOp =
				( TubenessOp ) Functions.unary( ops, TubenessOp.class, RandomAccessibleInterval.class,
						aSegmentationChannel, sigma, Util.getArrayFromValue( 1., aSegmentationChannel.numDimensions() ) );
		this.cancelableOp = tubenessOp;
		final Img< DoubleType > H = tubenessOp.calculate( aSegmentationChannel );
		if ( isCanceled() )
			return Collections.emptyList();

		/*
		 * Threshold with Otsu.
		 */

		statusService.showStatus( "Thresholding..." );

		final Histogram1d< DoubleType > histo = ops.image().histogram( H );
		final DoubleType otsuThreshold = ops.threshold().otsu( histo );
		otsuThreshold.mul( thresholdFactor / 100. );
		final IterableInterval< BitType > thresholded = ops.threshold().apply( H, otsuThreshold );

		percentPixelsInThresholded = 100. * ops().stats().sum( thresholded ).getRealDouble() / thresholded.size();

		/*
		 * Hough transform
		 */

		statusService.showStatus( "Computing Hough transform..." );

		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final HoughTransformOp< BitType > houghTransformOp =
				( HoughTransformOp ) Functions.unary( ops, HoughTransformOp.class, RandomAccessibleInterval.class,
						thresholded, minRadius, maxRadius, stepRadius );
		this.cancelableOp = houghTransformOp;

		if ( null == voteImg )
			voteImg = houghTransformOp.createOutput( thresholded );
		else
			for ( final DoubleType p : voteImg )
				p.setZero();

		houghTransformOp.compute( thresholded, voteImg );
		if ( isCanceled() )
			return Collections.emptyList();

		/*
		 * Detect maxima on vote image.
		 */

		statusService.showStatus( "Detecting circles..." );

		@SuppressWarnings( { "rawtypes", "unchecked" } )
		final HoughCircleDetectorOp< DoubleType > houghDetectOp =
				( HoughCircleDetectorOp ) Functions.unary( ops, detectionMethod.getOpClass(), List.class,
						voteImg, circleThickness, minRadius, stepRadius, sensitivity );
		this.cancelableOp = houghDetectOp;
		List< HoughCircle > aCircles = houghDetectOp.calculate( voteImg );
		if ( isCanceled() )
			return Collections.emptyList();

		/*
		 * Limit number of detections.
		 */

		if ( aCircles.size() > maxNDetections )
			aCircles = new ArrayList<>( aCircles.subList( 0, maxNDetections ) );

		return aCircles;
	}

	/**
	 * Gets measurements results for the specified circles on the specified
	 * channel. The {@link net.imagej.circleskinner.hough.HoughCircle.Stats} value of each circle is altered.
	 * Other fields are left untouched.
	 *
	 * @param channels
	 *            the list of channels to use for pixel value measurement.
	 * @param aCircles
	 *            the circles to analyse.
	 */
	private void analyzeCircles( final List< RandomAccessibleInterval< T > > channels, final Collection< HoughCircle > aCircles )
	{
		statusService.showStatus( "Analysing circles..." );

		@SuppressWarnings( { "rawtypes", "unchecked" } )
		final CircleAnalyzerOp< T > circleAnalyzerOp =
				( CircleAnalyzerOp ) Inplaces.binary1( ops, CircleAnalyzerOp.class, aCircles, channels );
		circleAnalyzerOp.run();
	}

	// -- Cancelable methods --

	/** Reason for cancelation, or null if not canceled. */
	private String cancelReason;

	@Override
	public boolean isCanceled()
	{
		return cancelReason != null;
	}

	/** Cancels the command execution, with the given reason for doing so. */
	@Override
	public void cancel( final String reason )
	{
		cancelReason = reason == null ? "" : reason;
		if ( reason != null && cancelableOp != null )
			cancelableOp.cancel( reason );
	}

	@Override
	public String getCancelReason()
	{
		return cancelReason;
	}

	public static enum DetectionMethod
	{
		FAST( HoughCircleLocalMaxDetectorOp.class, "Fast" ),
		ACCURATE( HoughCircleDogDetectorOp.class, "Accurate" );

		@SuppressWarnings( "rawtypes" )
		private final Class< ? extends HoughCircleDetectorOp > opClass;

		private final String name;

		@SuppressWarnings( "rawtypes" )
		private DetectionMethod( final Class< ? extends HoughCircleDetectorOp > opClass, final String name )
		{
			this.opClass = opClass;
			this.name = name;
		}

		@SuppressWarnings( "rawtypes" )
		public Class< ? extends HoughCircleDetectorOp > getOpClass()
		{
			return opClass;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
