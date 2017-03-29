package net.imagej.circleskinner.gui;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.FileWidget;

import ij.ImagePlus;
import ij.gui.Overlay;
import io.scif.FormatException;
import io.scif.config.SCIFIOConfig;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.circleskinner.CircleSkinner;
import net.imagej.circleskinner.hough.HoughCircle;
import net.imagej.circleskinner.util.HoughCircleOverlay;
import net.imagej.display.ImageDisplayService;
import net.imagej.legacy.LegacyService;
import net.imagej.ops.OpService;
import net.imagej.ops.special.computer.Computers;
import net.imagej.table.DefaultGenericTable;
import net.imagej.table.GenericTable;
import net.imglib2.type.numeric.RealType;

@Plugin( type = Command.class, menuPath = "Plugins > Circle Skinner GUI" )
public class CircleSkinnerCommand< T extends RealType< T > > implements Command
{
	private static final String CHOICE1 = "Current image";
	private static final String CHOICE2 = "Folder";

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

	/*
	 * PARAMETERS.
	 */

	@Parameter( label = "<html><b>Parameters:</b></html>", visibility = ItemVisibility.MESSAGE, persist = false )
	private String headerParameters = "";

	/**
	 * The circle thickness (crown thickness), in pixel units.
	 */
	@Parameter( label = "Circle thickness", min = "1", type = ItemIO.INPUT )
	private double circleThickness = 10.;

	@Parameter( label = "Threshold adjustment", min = "0", max = "100", type = ItemIO.INPUT )
	private double thresholdFactor = 10.;

	@Parameter( label = "Circle detection sensitivity", min = "1", type = ItemIO.INPUT )
	private double sensitivity = 100.;

	@Parameter( label = "<html><b>Target:</b></html>", visibility = ItemVisibility.MESSAGE, persist = false )
	private String headerTarget = " ";

	@Parameter( label = "Operate on:",
			style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = { CHOICE1, CHOICE2 }, callback = "targetChanged" )
	private String analysisTarget = CHOICE1;

	@Parameter( label = "Folder for batch processing", type = ItemIO.INPUT, required = false, style = FileWidget.DIRECTORY_STYLE, callback = "folderChanged" )
	private File folder;

	@Parameter( visibility = ItemVisibility.MESSAGE, persist = false )
	private String infoFiles = " ";

	@Parameter( label = "<html><b>Manual adjustments:</b></html>", visibility = ItemVisibility.MESSAGE, persist = false )
	private String headerAdjustments = " ";

	@Parameter( label = "Adjust threshold" )
	private Button adjustThresholdButton;

	@Parameter( label = "Adjust sensitivity" )
	private Button adjustSensitivityButton;

	@Parameter( type = ItemIO.OUTPUT )
	private DefaultGenericTable resultsTable;

	/*
	 * CONSTRUCTOR.
	 */



	/*
	 * METHODS.
	 */

	@Override
	public void run()
	{
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
			processImage( dataset, resultsTable, imp );
			break;
		case CHOICE2:
			processFolder( folder, resultsTable );
			break;
		}
	}

	private void processFolder( final File targetFolder, final GenericTable resultsTable )
	{
		if ( targetFolder == null || !targetFolder.exists() || !targetFolder.isDirectory() )
		{
			statusService.showStatus( "Invalid folder: " + targetFolder );
			return;
		}

		final File[] files = folder.listFiles();
		int nImages = 0;
		for ( final File file : files )
		{
			if ( !file.exists() || !file.isFile() )
				continue;

			if ( !canOpen( file.getAbsolutePath() ) )
			{
				log.info( "File " + file + " is not in a supported format." );
				continue;
			}

			nImages++;
			log.info( "Opening " + file );
			try
			{
				final Dataset dataset = datasetIOService.open( file.getAbsolutePath() );
				processImage( dataset, resultsTable, null );
			}
			catch ( final IOException e )
			{
				log.info( "Could not open file " + file + ":\n" + e.getMessage() );
				continue;
			}

		}

		statusService.showStatus( String.format( "Finshed processing %d images.", nImages ) );
	}

	private void processImage( final Dataset dataset, final GenericTable resultsTable, final ImagePlus imp )
	{
		@SuppressWarnings( "unchecked" )
		final CircleSkinner< T > circleSkinner = ( CircleSkinner< T > ) Computers.unary( opService, CircleSkinner.class, resultsTable,
				dataset, circleThickness, thresholdFactor, sensitivity );
		circleSkinner.compute( dataset, resultsTable );

		if ( null != imp )
		{
			Overlay overlay = imp.getOverlay();
			if ( null == overlay )
			{
				overlay = new Overlay();
				imp.setOverlay( overlay );
			}
			final HoughCircleOverlay circleOverlay = new HoughCircleOverlay( imp );
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

