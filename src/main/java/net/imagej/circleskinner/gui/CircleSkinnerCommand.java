package net.imagej.circleskinner.gui;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.scijava.Cancelable;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.command.Interactive;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.thread.ThreadService;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.FileWidget;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.measure.ResultsTable;
import io.scif.FormatException;
import io.scif.config.SCIFIOConfig;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.circleskinner.CircleSkinner;
import net.imagej.circleskinner.hough.HoughCircle;
import net.imagej.circleskinner.util.HoughCircleOverlay;
import net.imagej.circleskinner.util.PngExporter;
import net.imagej.display.ImageDisplayService;
import net.imagej.legacy.LegacyService;
import net.imagej.ops.OpService;
import net.imagej.ops.special.computer.Computers;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

@Plugin( type = Command.class, menuPath = "Plugins > Circle Skinner", headless = false )
public class CircleSkinnerCommand< T extends RealType< T > & NativeType< T > > implements Command, Interactive, Cancelable
{
	private static final String CHOICE1 = "Current image";

	private static final String CHOICE2 = "Folder";

	private static final String PNG_OUTPUT_FOLDER = "PNGs";

	public static final String PLUGIN_NAME = "CircleSkinner";

	public static final String PLUGIN_VERSION = "1.0.1-SNAPSHOT";

	/*
	 * SERVICES.
	 */

	@Parameter
	private DisplayService displayService;

	@Parameter
	private FormatService formatService;

	@Parameter
	private DatasetIOService datasetIOService;

	@Parameter
	private ImageDisplayService imageDisplayService;

	@Parameter
	private OpService opService;

	@Parameter
	private LegacyService legacyService;

	@Parameter
	private CommandService commandService;

	@Parameter
	private ThreadService threadService;

	/*
	 * PARAMETERS.
	 */

	@Parameter( label = "<html><b>Parameters:</b></html>", visibility = ItemVisibility.MESSAGE, persist = false )
	private String headerParameters = "";

	/**
	 * The circle thickness (crown thickness), in pixel units.
	 */
	@Parameter( label = "Circle thickness (pixels)", min = "1", type = ItemIO.INPUT )
	private int circleThickness = 10;

	@Parameter( label = "Threshold adjustment", min = "0.1", max = "200", type = ItemIO.INPUT )
	private double thresholdFactor = 100.;

	@Parameter( label = "Circle detection sensitivity", min = "1" )
	private double sensitivity = 100.;

	@Parameter( label = "Min circle radius (pixels)", min = "1", type = ItemIO.INPUT )
	private double minRadius = 50.;

	@Parameter( label = "Max circle radius (pixels)", min = "1", type = ItemIO.INPUT )
	private double maxRadius = 100.;

	@Parameter( label = "Radius step (pixels)", min = "1", type = ItemIO.INPUT )
	private double stepRadius = 2.;

	@Parameter( label = "<html><b>Target:</b></html>", visibility = ItemVisibility.MESSAGE, persist = false )
	private String headerTarget = " ";

	@Parameter( label = "Operate on:",
			style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = { CHOICE1, CHOICE2 }, callback = "targetChanged" )
	private String analysisTarget = CHOICE1;

	@Parameter( label = "Folder for batch processing", type = ItemIO.INPUT,
			required = false, style = FileWidget.DIRECTORY_STYLE, callback = "folderChanged" )
	private File folder;

	@Parameter( visibility = ItemVisibility.MESSAGE, persist = false )
	private String infoFiles = " ";

	@Parameter( label = "Save PNG snapshot?", description = "If true, then a PNG capture of the color "
			+ "image plus an overlay showing detected circles will be saved in a subolder if the input folder.",
			required = false, type = ItemIO.INPUT )
	private boolean saveSnapshot = false;

	@Parameter( label = "<html><b>Manual adjustments:</b></html>", visibility = ItemVisibility.MESSAGE, persist = false )
	private String headerAdjustments = " ";

	@Parameter( label = "Adjust threshold", description = "Will show a separate window on which the user will "
			+ "be able to tune the threshold value.",
			callback = "adjustThreshold" )
	private Button adjustThresholdButton;

	@Parameter( label = "Adjust sensitivity" )
	private Button adjustSensitivityButton;

	private ResultsTable resultsTable;

	/*
	 * FIELDS
	 */

	private Display< String > messages;

	/*
	 * CONSTRUCTOR.
	 */

	/*
	 * METHODS.
	 */

	@Override
	public void run()
	{
		System.out.println( "unr!" ); // DEBUG
	}

	private void process()
	{
//		final DisplayService displayService = context().getService( DisplayService.class );

		@SuppressWarnings( "unchecked" )
		final Display< String > m = ( Display< String > ) displayService.createDisplay(
				"CircleSkinner log", PLUGIN_NAME + " v" + PLUGIN_VERSION );
		this.messages = m;

		messages.add( "" );
		messages.add( PLUGIN_NAME + " started on " + SimpleDateFormat.getInstance().format( new Date() ) );
		switch ( analysisTarget )
		{
		case CHOICE1:
			messages.add( " - Target: active image." );
			break;
		case CHOICE2:
			messages.add( " - Target folder: " + folder );
			break;
		default:
			messages.add( " - Unknown target: " + analysisTarget );
			break;
		}
		messages.add( String.format( " - Circle thickness (pixels): %.1f", circleThickness ) );
		messages.add( String.format( " - Threshold adjusment: %.1f %%", circleThickness ) );
		messages.add( String.format( " - Sensitivity: %.1f", sensitivity ) );
		messages.add( String.format( " - Min. radius (pixels): %.1f", minRadius ) );
		messages.add( String.format( " - Max. radius (pixels): %.1f", maxRadius ) );
		messages.add( String.format( " - Step radius (pixels): %.1f", stepRadius ) );
		messages.add( "" );

		final long start = System.currentTimeMillis();
		resultsTable = CircleSkinner.createResulsTable();

		switch ( analysisTarget )
		{
		case CHOICE1:
		default:

			/*
			 * Display Overlay if we can.
			 */

			final ImagePlus imp = legacyService.getImageMap().lookupImagePlus( imageDisplayService.getActiveImageDisplay() );

			/*
			 * Process the active image.
			 */

			final Dataset dataset = imageDisplayService.getActiveDataset();
			messages.add( "Processing " + dataset );
			messages.update();

			processImage( dataset, resultsTable, imp );

			break;

		case CHOICE2:

			processFolder( folder, resultsTable );

			break;
		}

		final long end = System.currentTimeMillis();

		resultsTable.show( PLUGIN_NAME + " Results" );
		messages.add( "" );
		messages.add( String.format( "CircleSkinner completed in %.1f min.", ( end - start ) / 60000. ) );
		messages.update();
	}

	@SuppressWarnings( "unchecked" )
	private void processFolder( final File sourceFolder, final ResultsTable resultsTable )
	{
		/*
		 * Inspect source folder.
		 */

		if ( sourceFolder == null || !sourceFolder.exists() || !sourceFolder.isDirectory() )
		{
			messages.add( "Invalid folder: " + sourceFolder + ". Exit." );
			messages.update();
			return;
		}

		/*
		 * Build target folder for PNG export.
		 */

		String saveFolder = sourceFolder.getAbsolutePath();
		if ( saveSnapshot )
		{
			final File sf = new File( sourceFolder, PNG_OUTPUT_FOLDER );
			if ( sf.exists() && !sf.isDirectory() )
			{
				messages.add( "Cannot ouput PNG shapshots. A file name " + sf.getAbsolutePath() + " exists in input folder." );
				messages.update();
				saveSnapshot = false;
			}
			else if ( !sf.exists() )
			{
				final boolean mkdirs = sf.mkdirs();
				if ( !mkdirs )
				{
					messages.add( "Cannot ouput PNG shapshots. Could not create folder " + sf.getAbsolutePath() + "." );
					messages.update();
					saveSnapshot = false;
				}
			}
			saveFolder = sf.getAbsolutePath();
		}

		/*
		 * Process file be file.
		 */

		final File[] files = folder.listFiles();
		int nImages = 0;
		messages.add( "" );
		for ( final File file : files )
		{
			if ( !file.exists() || !file.isFile() )
				continue;

			if ( !canOpen( file.getAbsolutePath() ) )
			{
				messages.add( "File " + file + " is not in a supported format." );
				messages.update();
				continue;
			}

			nImages++;

			messages.add( "Processing " + file );
			messages.update();
			try
			{
				final Dataset dataset = datasetIOService.open( file.getAbsolutePath() );
				ImagePlus imp = null;
				if ( saveSnapshot )
					imp = ImageJFunctions.wrap( ( Img< T > ) dataset.getImgPlus(), dataset.getName() );

				processImage( dataset, resultsTable, imp );

				if ( saveSnapshot )
				{
					imp.show();
					PngExporter.exportToPng( imp, saveFolder );
					imp.changes = false;
					imp.close();
				}
			}
			catch ( final IOException e )
			{
				messages.add( "Could not open file " + file + ":\n" + e.getMessage() );
				messages.update();
				continue;
			}

		}

		messages.add( String.format( "\nFinished processing %d images.", nImages ) );
		messages.update();
	}

	/*
	 * CALLBACKS.
	 */

	protected void targetChanged()
	{
		switch ( analysisTarget )
		{
		case CHOICE1:
		default:
			printCurrentImage();
			return;
		case CHOICE2:
			folderChanged();
			return;
		}
	}

	protected void adjustThreshold()
	{
		new AdjustThresholdCommand< T >( imageDisplayService.getActiveImageDisplay(), circleThickness, thresholdFactor, opService.getContext() ).setVisible( true );

	}

	protected void folderChanged()
	{
		if ( folder == null || !folder.exists() || !folder.isDirectory() )
		{
			infoFiles = "Invalid folder: " + folder;
			return;
		}

		final File[] files = folder.listFiles();
		int nImages = 0;
		for ( final File file : files )
		{
			if ( !file.exists() || !file.isFile() )
				continue;

			if ( canOpen( file.getAbsolutePath() ) )
				nImages++;
		}

		infoFiles = String.format( "Found %d candidate %s.", nImages, ( nImages == 1 ? "image" : "images" ) );
	}

	/*
	 * PRIVATE METHODS
	 */

	private void processImage( final Dataset dataset, final ResultsTable resultsTable, final ImagePlus imp )
	{
		@SuppressWarnings( "unchecked" )
		final CircleSkinner< T > circleSkinner = ( CircleSkinner< T > ) Computers.unary( opService, CircleSkinner.class, resultsTable,
				dataset, circleThickness, thresholdFactor, sensitivity, minRadius, maxRadius, stepRadius );
		circleSkinner.compute( dataset, resultsTable );

		if ( null != imp )
		{
			Overlay overlay = imp.getOverlay();
			if ( null == overlay )
			{
				overlay = new Overlay();
				imp.setOverlay( overlay );
			}
			final HoughCircleOverlay circleOverlay = new HoughCircleOverlay( imp, sensitivity );
			overlay.add( circleOverlay, "Hough circles" );
			final Map< Integer, List< HoughCircle > > circles = circleSkinner.getCircles();
			circleOverlay.setCircles( circles );
		}
	}

	private void printCurrentImage()
	{
		final Dataset dataset = imageDisplayService.getActiveDataset();
		if ( null == dataset )
			infoFiles = String.format( "No active image." );
		else
			infoFiles = String.format( "Active image: %s.", dataset.getName() );
	}

	public boolean canOpen( final String source )
	{
		try
		{
			return formatService.getFormat( source, new SCIFIOConfig()
					.checkerSetOpen( true ) ) != null;
		}
		catch ( final FormatException exc )
		{
			// Do nothing.
		}
		return false;
	}

	public static void main( final String... args ) throws Exception
	{
		final ImageJ ij = net.imagej.Main.launch( args );
		final Object dataset = ij.io().open( "samples/ca-01.lsm" );
		ij.ui().show( dataset );
		ij.command().run( CircleSkinnerCommand.class, true );
	}

	@Override
	public boolean isCanceled()
	{
		System.out.println( "iscanceled" ); // DEBUG
		return false;
	}

	@Override
	public void cancel( final String reason )
	{
		System.out.println( "Cancel " + reason ); // DEBUG
	}

	@Override
	public String getCancelReason()
	{
		return "getcancelreadon";
	}
}
