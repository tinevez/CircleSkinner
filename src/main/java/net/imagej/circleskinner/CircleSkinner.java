package net.imagej.circleskinner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;
import org.scijava.ui.UIService;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.circleskinner.analyze.CircleAnalyzerOp;
import net.imagej.circleskinner.hough.HoughCircle;
import net.imagej.circleskinner.hough.HoughCircle.Stats;
import net.imagej.circleskinner.hough.HoughDetectorOp;
import net.imagej.circleskinner.hough.HoughTransformOp;
import net.imagej.ops.OpService;
import net.imagej.ops.special.computer.AbstractUnaryComputerOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.inplace.Inplaces;
import net.imagej.table.DefaultGenericTable;
import net.imagej.table.DoubleColumn;
import net.imagej.table.GenericColumn;
import net.imagej.table.GenericTable;
import net.imagej.table.IntColumn;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

@Plugin( type = CircleSkinner.class )
public class CircleSkinner< T extends RealType< T > > extends AbstractUnaryComputerOp< Dataset, GenericTable >
{
	private static final String SOURCE_NAME_COLUMN = "Image";
	private static final String CHANEL_COLUMN = "Channel";
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
	private static final String CIRCLE_SENSITIVITY_COLUMN = "Sensitivity";

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

	/*
	 * INPUT PARAMETERS.
	 */

	/**
	 * The circle thickness (crown thickness), in pixel units.
	 */
	@Parameter( label = "Circle thickness", min = "1", type = ItemIO.INPUT )
	private double circleThickness = 9.;

	@Parameter( label = "Threshold adjustment", min = "1", type = ItemIO.INPUT, description = "By how much (in percent) to adjust the automatic Otsu threshold after segmentation of the filtered image." )
	private double thresholdFactor = 100.;

	@Parameter( label = "Circle detection sensitivity", min = "1", type = ItemIO.INPUT )
	private double sensitivity = 100.;

	@Parameter( label = "Min circle radius (pixels)", min = "1", type = ItemIO.INPUT )
	private double minRadius = 50.;

	@Parameter( label = "Max circle radius (pixels)", min = "1", type = ItemIO.INPUT )
	private double maxRadius = 100.;

	@Parameter( label = "Radius step (pixels)", min = "1", type = ItemIO.INPUT )
	private double stepRadius = 2.;

	/*
	 * OUTPUT PARAMETERS.
	 */

	@Parameter( label = "Detected circles", type = ItemIO.OUTPUT, description = "The map of detected circles per channel in the source image." )
	private Map< Integer, List< HoughCircle > > circles;

	/*
	 * METHODS.
	 */

	public Map< Integer, List< HoughCircle > > getCircles()
	{
		return circles;
	}

	public static final DefaultGenericTable createResulsTable()
	{
		final DefaultGenericTable table = new DefaultGenericTable();
		// Input
		table.add( new GenericColumn( SOURCE_NAME_COLUMN ) );
		table.add( new IntColumn( CHANEL_COLUMN ) );
		table.add( new IntColumn( CIRCLE_ID_COLUMN ) );
		// Detection results.
		table.add( new DoubleColumn( CIRCLE_X_COLUMN ) );
		table.add( new DoubleColumn( CIRCLE_Y_COLUMN ) );
		table.add( new DoubleColumn( CIRCLE_RADIUS_COLUMN ) );
		table.add( new DoubleColumn( CIRCLE_MEAN_COLUMN ) );
		table.add( new DoubleColumn( CIRCLE_STD_COLUMN ) );
		table.add( new IntColumn( CIRCLE_N_COLUMN ) );
		table.add( new DoubleColumn( CIRCLE_MEDIAN_COLUMN ) );
		// Detection parameters.
		table.add( new DoubleColumn( CIRCLE_THICKNESS_COLUMN ) );
		table.add( new DoubleColumn( CIRCLE_THRESHOLD_COLUMN ) );
		table.add( new DoubleColumn( CIRCLE_SENSITIVITY_COLUMN ) );
		return table;
	}

	@Override
	public void compute( final Dataset source, GenericTable table )
	{
		if ( null == table )
			table = createResulsTable();

		circles = new HashMap<>();

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

		// Process each channel.
		if (cId < 0)
		{
			final List< HoughCircle > circ = processChannel( img.getImg() );
			circles.put( Integer.valueOf( 0 ), circ );
			appendResults( circ, table, source.getName(), 1 );
		}
		else
		{
			for ( int c = 0; c < source.getChannels(); c++ )
			{
				@SuppressWarnings( "unchecked" )
				final IntervalView< T > channel = ( IntervalView< T > ) Views.hyperSlice( source.getImgPlus().getImg(), cId, c );
				final List< HoughCircle > circ = processChannel( channel );
				circles.put( Integer.valueOf( c ), circ );
				appendResults( circ, table, source.getName(), c + 1 );
			}
		}
	}

	private void appendResults( final List< HoughCircle > circles, final GenericTable table, final String name, final int channelNumber )
	{
		int circleId = 0;

		final GenericColumn nameColumn = ( GenericColumn ) table.get( SOURCE_NAME_COLUMN );
		final IntColumn channelColumn = ( IntColumn ) table.get( CHANEL_COLUMN );
		final IntColumn circleIdColumn = ( IntColumn ) table.get( CIRCLE_ID_COLUMN );
		final DoubleColumn circleXColumn = ( DoubleColumn ) table.get( CIRCLE_X_COLUMN );
		final DoubleColumn circleYColumn = ( DoubleColumn ) table.get( CIRCLE_Y_COLUMN );
		final DoubleColumn circleRadiusColumn = ( DoubleColumn ) table.get( CIRCLE_RADIUS_COLUMN );
		final DoubleColumn circleThicknessColumn = ( DoubleColumn ) table.get( CIRCLE_THICKNESS_COLUMN );
		final DoubleColumn circleTresholdColumn = ( DoubleColumn ) table.get( CIRCLE_THRESHOLD_COLUMN );
		final DoubleColumn circleSensitivityColumn = ( DoubleColumn ) table.get( CIRCLE_SENSITIVITY_COLUMN );
		final DoubleColumn circleMeanColumn = ( DoubleColumn ) table.get( CIRCLE_MEAN_COLUMN );
		final DoubleColumn circleStdColumn = ( DoubleColumn ) table.get( CIRCLE_STD_COLUMN );
		final IntColumn circleNColumn = ( IntColumn ) table.get( CIRCLE_N_COLUMN );
		final DoubleColumn circleMedianColumn = ( DoubleColumn ) table.get( CIRCLE_MEDIAN_COLUMN );

		final int rowCount = table.getRowCount();
		for ( final HoughCircle circle : circles )
		{
			nameColumn.add( name );
			channelColumn.add( channelNumber );

			circleIdColumn.add( ++circleId );
			circleXColumn.add( circle.getDoublePosition( 0 ) );
			circleYColumn.add( circle.getDoublePosition( 1 ) );
			circleRadiusColumn.add( circle.getRadius() );
			circleThicknessColumn.add( circle.getThickness() );
			circleTresholdColumn.add( thresholdFactor );
			circleSensitivityColumn.add( circle.getSensitivity() );

			final Stats stats = circle.getStats();
			if (null == stats)
			{
				circleMeanColumn.add( Double.NaN );
				circleStdColumn.add( Double.NaN );
				circleNColumn.add( null );
				circleMedianColumn.add( Double.NaN );
			}
			else
			{
				circleMeanColumn.add( stats.mean );
				circleStdColumn.add( stats.std );
				circleNColumn.add( stats.N );
				circleMedianColumn.add( stats.median );
			}
		}
		table.setRowCount( rowCount + circles.size() );
	}

	private List< HoughCircle > processChannel( final RandomAccessibleInterval< T > channel )
	{
		final double sigma = circleThickness / 2. / Math.sqrt( channel.numDimensions() );

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

		@SuppressWarnings( { "rawtypes", "unchecked" } )
		final HoughDetectorOp< DoubleType > houghDetectOp =
				( HoughDetectorOp ) Functions.unary( ops, HoughDetectorOp.class, List.class,
						voteImg, circleThickness, minRadius, stepRadius, sensitivity );
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
}
