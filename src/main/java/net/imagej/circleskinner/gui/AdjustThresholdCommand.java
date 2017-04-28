package net.imagej.circleskinner.gui;

import java.awt.Dimension;
import java.awt.Toolkit;

import org.scijava.Initializable;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import net.imagej.DatasetService;
import net.imagej.circleskinner.TubenessOp;
import net.imagej.display.ImageDisplay;
import net.imagej.display.ImageDisplayService;
import net.imagej.legacy.LegacyService;
import net.imagej.ops.OpService;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;

@Plugin( type = AdjustThresholdCommand.class, label = "Circle Skinner Threshold Adjuster", headless = false )
public class AdjustThresholdCommand< T extends RealType< T > & NativeType< T > > extends InteractiveCommand implements Initializable
{

	/*
	 * SERVICES.
	 */

	@Parameter
	private LegacyService legacyService;

	@Parameter 
	private StatusService statusService;

	@Parameter
	private OpService opService;

	@Parameter
	private UIService uiService;

	@Parameter
	private ImageDisplayService imageDisplayService;

	@Parameter
	private DatasetService datasetService;

	/*
	 * INPUT.
	 */

	/**
	 * Will take the current display regardless of the input.
	 */
	@Parameter
	private ImageDisplay source;

	/**
	 * The circle thickness (crown thickness), in pixel units.
	 */
	@Parameter( label = "Circle thickness (pixels)", min = "1", type = ItemIO.INPUT )
	private double circleThickness = 10.;

	@Parameter( label = "Threshold adjustment", min = "0.01", max = "20", type = ItemIO.INPUT )
	private double thresholdFactor = 1.;

	/*
	 * FIELDS.
	 */

	private Img< T > slice;

	private Img< DoubleType > filtered;

	private ImagePlus filteredImp;

	private ImagePlus thresholdedImp;

	private IterableInterval< BitType > thresholded;

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	@Override
	public void initialize()
	{
		System.out.println( "initialize()" ); // DEBUG
		/*
		 * We have to go through IJ to get a slice because the IJ2 display does
		 * not report what slice it is currently displaying.
		 */

		final ImagePlus imp = legacyService.getImageMap().lookupImagePlus( source );
		this.slice = ImageJFunctions.wrap( new ImagePlus( "Filtered - " + imp.getShortTitle(),
				imp.getProcessor().duplicate() ) );

		final double sigma = circleThickness / 2. / Math.sqrt( slice.numDimensions() );
		final TubenessOp< T > tubeness = ( TubenessOp ) Hybrids.unaryCF( opService, TubenessOp.class, Img.class,
				slice, sigma, Util.getArrayFromValue( 1., slice.numDimensions() ) );
		this.filtered = tubeness.createOutput( slice );
		this.filteredImp = ImageJFunctions.wrap( filtered, "Filtered - " + imp.getShortTitle() );

		final Histogram1d< DoubleType > histo = opService.image().histogram( filtered );
		final DoubleType otsuThreshold = opService.threshold().otsu( histo );
		otsuThreshold.mul( thresholdFactor );

		this.thresholded = opService.threshold().apply( filtered, otsuThreshold );
		this.thresholdedImp = ImageJFunctions.wrapBit( ( RandomAccessibleInterval< BitType > ) thresholded,
				"Thresholded - " + imp.getShortTitle() );

		filteredImp.show();
		thresholdedImp.show();
	}

	@Override
	public void run()
	{
		System.out.println( "run()" ); // DEBUG

	}

	@Override
	public void preview()
	{
		System.out.println( "preview()" ); // DEBUG
		/*
		 * Tubeness filter.
		 */

		statusService.showStatus( "Filtering..." );
		final double sigma = circleThickness / 2. / Math.sqrt( slice.numDimensions() );
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final TubenessOp< T > tubeness = ( TubenessOp ) Hybrids.unaryCF( opService, TubenessOp.class, Img.class,
				slice, sigma, Util.getArrayFromValue( 1., slice.numDimensions() ) );
		tubeness.compute( slice, filtered );

		final ImageProcessor processor = ImageJFunctions.wrapFloat( filtered, "" ).getProcessor();
		processor.resetMinAndMax();
		filteredImp.setProcessor( processor );
		filteredImp.updateAndDraw();
		
		statusService.showStatus( "Thresholding..." );

		final Histogram1d< DoubleType > histo = opService.image().histogram( filtered );
		final DoubleType otsuThreshold = opService.threshold().otsu( histo );
		otsuThreshold.mul( thresholdFactor );

		opService.threshold().apply( thresholded, filtered, otsuThreshold );
		@SuppressWarnings( "unchecked" )
		final ImageProcessor processor2 = ImageJFunctions.wrapBit( ( RandomAccessibleInterval< BitType > ) thresholded,
				"Thresholded - " ).getProcessor();
		thresholdedImp.setProcessor( processor2 );
		thresholdedImp.updateAndDraw();
		
		if (!filteredImp.isVisible())
			setupWindows();
	}

	private void setupWindows()
	{
		final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		final double ws = screenSize.getWidth();
		final double hs = screenSize.getHeight();

		final ImagePlus imp = legacyService.getImageMap().lookupImagePlus( source );
		final int x = imp.getWindow().getX();
		final int y = imp.getWindow().getY();

		filteredImp.show();
		int w1 = filteredImp.getWindow().getWidth();
		int h1 = filteredImp.getWindow().getHeight();
		int nZoomOut = 0;
		while ( w1 + x >= ws / 2 || h1 + y > hs / 2 )
		{
			nZoomOut++;
			filteredImp.getCanvas().zoomOut( x + w1 / 2, y + h1 / 2 );
			w1 = filteredImp.getWindow().getWidth();
			h1 = filteredImp.getWindow().getHeight();
		}
		
		thresholdedImp.show();
		int w2 = thresholdedImp.getWindow().getWidth();
		int h2 = thresholdedImp.getWindow().getHeight();
		for ( int i = 0; i < nZoomOut; i++ )
		{
			thresholdedImp.getCanvas().zoomOut( x + w2 / 2, y + h2 / 2 + h1 );
			w2 = thresholdedImp.getWindow().getWidth();
			h2 = thresholdedImp.getWindow().getHeight();
		}

	}
}
