package net.imagej.circleskinner.gui;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
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
import net.imglib2.type.numeric.RealType;

@Plugin( type = Command.class, menuPath = "Plugins > Circle Skinner GUI" )
public class CircleSkinnerCommand< T extends RealType< T > > implements Command
{
	private static final String CHOICE1 = "Current image";
	private static final String CHOICE2 = "Folder";
	private static final String PNG_OUTPUT_FOLDER = "PNGs";
	public static final String PLUGIN_NAME = "CircleSkinner";
	private static final String PLUGIN_VERSION = "0.1.0-SNAPSHOT";

	/*
	 * SERVICES.
	 */

	@Parameter
	private LogService log;

	@Parameter
	private FormatService formatService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private DatasetIOService datasetIOService;

	@Parameter
	private ImageDisplayService imageDisplayService;

	@Parameter
	private UIService uiService;

	@Parameter
	private OpService opService;

	@Parameter
	private LegacyService legacyService;

	@Parameter
	private DisplayService displayService;

	/*
	 * PARAMETERS.
	 */

	@Parameter( label = "<html><b>Parameters:</b></html>", visibility = ItemVisibility.MESSAGE, persist = false )
	private String headerParameters = "";

	/**
	 * The circle thickness (crown thickness), in pixel units.
	 */
	@Parameter( label = "Circle thickness (pixels)", min = "1", type = ItemIO.INPUT )
	private double circleThickness = 10.;

	@Parameter( label = "Threshold adjustment", min = "0", max = "100", type = ItemIO.INPUT )
	private double thresholdFactor = 10.;

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

	@Parameter( label = "Adjust threshold" )
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
		@SuppressWarnings( "unchecked" )
		final Display< String > m = ( Display< String > ) displayService.createDisplay( "CircleSkinner log", PLUGIN_NAME + " v" + PLUGIN_VERSION );
		this.messages = m;
		
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
		if (saveSnapshot)
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

	protected void targetChanged()
	{
		switch (analysisTarget)
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

	private void printCurrentImage()
	{
		final Dataset dataset = imageDisplayService.getActiveDataset();
		if ( null == dataset )
			infoFiles = String.format( "No active image." );
		else
			infoFiles = String.format( "Active image: %s.", dataset.getName() );
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
}

