package net.imagej.circleskinner.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import net.imagej.DatasetService;
import net.imagej.circleskinner.TubenessOp;
import net.imagej.circleskinner.util.DisplayUpdater;
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

public class AdjustThresholdDialog< T extends RealType< T > & NativeType< T > > extends JDialog
{

	private static final long serialVersionUID = 1L;

	private static final int MAX_THICKNESS = 50;

	private static final int MIN_THICKNESS = 1;

	private static final double MAX_THRESHOLD = 5.;

	private static final double MIN_THRESHOLD = 0.1;

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
	private ImageDisplay source;

	/**
	 * The circle thickness (crown thickness), in pixel units.
	 */
	private int circleThickness = 10;

	private double thresholdFactor = 1.;

	/*
	 * FIELDS.
	 */

	private Img< T > slice;

	private Img< DoubleType > filtered;

	private ImagePlus filteredImp;

	private ImagePlus thresholdedImp;

	private IterableInterval< BitType > thresholded;

	private final HashSet< ActionListener > listeners = new HashSet<>();

	private JLabel lblInfoPixels;

	private final DisplayUpdater previewAllUpdater = new DisplayUpdater()
	{
		@Override
		public void refresh()
		{
			previewAll();
		}
	};

	private final DisplayUpdater previewThresholdUpdater = new DisplayUpdater()
	{
		@Override
		public void refresh()
		{
			previewThreshold();
		}
	};

	/*
	 * CONSTRUCTOR.
	 */

	public AdjustThresholdDialog( final ImageDisplay source, final int circleThickness, final double thresholdFactor, final Context context )
	{
		this.source = source;
		this.circleThickness = circleThickness;
		this.thresholdFactor = thresholdFactor;
		context.inject( this );
		initialize();
	}

	/*
	 * METHODS.
	 */

	public double getThresholdFactor()
	{
		return thresholdFactor;
	}

	public int getCircleThickness()
	{
		return circleThickness;
	}

	public void addActionListener( final ActionListener listener )
	{
		listeners.add( listener );
	}

	public void removeActionListener( final ActionListener listener )
	{
		listeners.remove( listener );
	}

	private void cancelAdjustment()
	{
		filteredImp.changes = false;
		filteredImp.close();
		thresholdedImp.changes = false;
		thresholdedImp.close();
		for ( final ActionListener listener : listeners )
			listener.actionPerformed( new ActionEvent( this, 1, "cancel" ) );

		previewAllUpdater.quit();
		previewThresholdUpdater.quit();
		dispose();
	}

	private void acceptAdjustment()
	{
		filteredImp.changes = false;
		filteredImp.close();
		thresholdedImp.changes = false;
		thresholdedImp.close();
		for ( final ActionListener listener : listeners )
			listener.actionPerformed( new ActionEvent( this, 0, "OK" ) );

		previewAllUpdater.quit();
		previewThresholdUpdater.quit();
		dispose();
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	private void initialize()
	{
		final JPanel panelButtons = new JPanel();
		final FlowLayout flowLayout = ( FlowLayout ) panelButtons.getLayout();
		flowLayout.setAlignment( FlowLayout.TRAILING );
		getContentPane().add( panelButtons, BorderLayout.SOUTH );

		final JButton btnCancel = new JButton( "Cancel" );
		btnCancel.addActionListener( ( e ) -> cancelAdjustment() );
		panelButtons.add( btnCancel );

		final JButton btnOk = new JButton( "OK" );
		btnOk.addActionListener( ( e ) -> acceptAdjustment() );
		panelButtons.add( btnOk );

		final JPanel panelAdjustments = new JPanel();
		getContentPane().add( panelAdjustments, BorderLayout.CENTER );
		final GridBagLayout gbl_panelAdjustments = new GridBagLayout();
		gbl_panelAdjustments.columnWidths = new int[] { 0, 0, 0, 0 };
		gbl_panelAdjustments.rowHeights = new int[] { 0, 0, 0, 0 };
		gbl_panelAdjustments.columnWeights = new double[] { 0.0, 1.0, 1.0, Double.MIN_VALUE };
		gbl_panelAdjustments.rowWeights = new double[] { 0.0, 0.0, 1.0, Double.MIN_VALUE };
		panelAdjustments.setLayout( gbl_panelAdjustments );

		final JLabel lblCircleThicknesspixels = new JLabel( "Circle thickness (pixels):" );
		final GridBagConstraints gbc_lblCircleThicknesspixels = new GridBagConstraints();
		gbc_lblCircleThicknesspixels.anchor = GridBagConstraints.EAST;
		gbc_lblCircleThicknesspixels.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblCircleThicknesspixels.gridx = 0;
		gbc_lblCircleThicknesspixels.gridy = 0;
		panelAdjustments.add( lblCircleThicknesspixels, gbc_lblCircleThicknesspixels );

		final JSlider sliderCircleThickness = new JSlider();
		sliderCircleThickness.setPaintLabels( true );
		sliderCircleThickness.setValue( circleThickness );
		sliderCircleThickness.setMaximum( MAX_THICKNESS );
		sliderCircleThickness.setMinimum( MIN_THICKNESS );
		sliderCircleThickness.setMinorTickSpacing( 2 );
		sliderCircleThickness.setMajorTickSpacing( 10 );
		sliderCircleThickness.setPaintTicks( true );
		final GridBagConstraints gbc_sliderCircleThickness = new GridBagConstraints();
		gbc_sliderCircleThickness.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderCircleThickness.insets = new Insets( 0, 0, 5, 5 );
		gbc_sliderCircleThickness.gridx = 1;
		gbc_sliderCircleThickness.gridy = 0;
		panelAdjustments.add( sliderCircleThickness, gbc_sliderCircleThickness );

		final JSpinner spinnerCircleThickness = new JSpinner( new SpinnerNumberModel( circleThickness, MIN_THICKNESS, MAX_THICKNESS, 1 ) );
		final GridBagConstraints gbc_spinnerCircleThickness = new GridBagConstraints();
		gbc_spinnerCircleThickness.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerCircleThickness.insets = new Insets( 0, 0, 5, 5 );
		gbc_spinnerCircleThickness.gridx = 2;
		gbc_spinnerCircleThickness.gridy = 0;
		panelAdjustments.add( spinnerCircleThickness, gbc_spinnerCircleThickness );

		spinnerCircleThickness.addChangeListener( ( e ) -> sliderCircleThickness.setValue( ( int ) spinnerCircleThickness.getValue() ) );
		sliderCircleThickness.addChangeListener( new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				spinnerCircleThickness.setValue( sliderCircleThickness.getValue() );
				circleThickness = ( int ) spinnerCircleThickness.getValue();
				previewAllUpdater.doUpdate();
			}
		} );

		final JLabel lblThresholdAdjustment = new JLabel( "Threshold adjustment (%):" );
		final GridBagConstraints gbc_lblThresholdAdjustment = new GridBagConstraints();
		gbc_lblThresholdAdjustment.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblThresholdAdjustment.anchor = GridBagConstraints.EAST;
		gbc_lblThresholdAdjustment.gridx = 0;
		gbc_lblThresholdAdjustment.gridy = 1;
		panelAdjustments.add( lblThresholdAdjustment, gbc_lblThresholdAdjustment );

		final JSlider sliderThreshold = new JSlider();
		sliderThreshold.setPaintLabels( true );
		sliderThreshold.setMinorTickSpacing( 10 );
		sliderThreshold.setMajorTickSpacing( 100 );
		sliderThreshold.setPaintTicks( true );
		sliderThreshold.setValue( ( int ) ( thresholdFactor * 100 ) );
		sliderThreshold.setMaximum( ( int ) ( MAX_THRESHOLD * 100 ) );
		sliderThreshold.setMinimum( ( int ) ( MIN_THRESHOLD * 100 ) );
		final GridBagConstraints gbc_sliderThreshold = new GridBagConstraints();
		gbc_sliderThreshold.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderThreshold.insets = new Insets( 0, 0, 5, 5 );
		gbc_sliderThreshold.gridx = 1;
		gbc_sliderThreshold.gridy = 1;
		panelAdjustments.add( sliderThreshold, gbc_sliderThreshold );

		final JSpinner spinnerThreshold = new JSpinner( new SpinnerNumberModel(
				( int ) ( thresholdFactor * 100 ), ( int ) ( MIN_THRESHOLD * 100 ), ( int ) ( MAX_THRESHOLD * 100 ), 10 ) );
		final GridBagConstraints gbc_spinnerThreshold = new GridBagConstraints();
		gbc_spinnerThreshold.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerThreshold.insets = new Insets( 0, 0, 5, 5 );
		gbc_spinnerThreshold.gridx = 2;
		gbc_spinnerThreshold.gridy = 1;
		panelAdjustments.add( spinnerThreshold, gbc_spinnerThreshold );

		spinnerThreshold.addChangeListener( ( e ) -> sliderThreshold.setValue( ( int ) spinnerThreshold.getValue() ) );
		sliderThreshold.addChangeListener( new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				spinnerThreshold.setValue( sliderThreshold.getValue() );
				thresholdFactor = ( int ) spinnerThreshold.getValue() / 100.;
				previewThresholdUpdater.doUpdate();
			}
		} );

		lblInfoPixels = new JLabel( " " );
		final GridBagConstraints gbc_lblInfoPixels = new GridBagConstraints();
		gbc_lblInfoPixels.anchor = GridBagConstraints.SOUTHEAST;
		gbc_lblInfoPixels.gridwidth = 3;
		gbc_lblInfoPixels.insets = new Insets( 0, 0, 0, 5 );
		gbc_lblInfoPixels.gridx = 0;
		gbc_lblInfoPixels.gridy = 2;
		panelAdjustments.add( lblInfoPixels, gbc_lblInfoPixels );


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
		setupWindows();

		setLocationRelativeTo( filteredImp.getWindow() );
		pack();

	}

	private void previewAll()
	{
		/*
		 * Tubeness filter.
		 */

		final double sigma = circleThickness / 2. / Math.sqrt( slice.numDimensions() );
		statusService.showStatus( String.format( "Filtering with sigma = %.1f...", sigma ) );
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final TubenessOp< T > tubeness = ( TubenessOp ) Hybrids.unaryCF( opService, TubenessOp.class, Img.class,
				slice, sigma, Util.getArrayFromValue( 1., slice.numDimensions() ) );
		tubeness.compute( slice, filtered );

		final ImageProcessor processor = ImageJFunctions.wrapFloat( filtered, "" ).getProcessor();
		processor.resetMinAndMax();
		filteredImp.setProcessor( processor );
		filteredImp.updateAndDraw();
		
		previewThreshold();
	}

	private void previewThreshold()
	{

		/*
		 * Threshold.
		 */

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
		
		statusService.clearStatus();

		lblInfoPixels.setText( String.format( "Retained %5.1f%% pixels.",
				100. * opService.stats().sum( thresholded ).getRealDouble() / thresholded.size() ) );
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
			filteredImp.getCanvas().zoomOut( w1 / 2, h1 / 2 );
			w1 = filteredImp.getWindow().getWidth();
			h1 = filteredImp.getWindow().getHeight();
		}
		filteredImp.getWindow().setLocation( x, y );
		
		thresholdedImp.show();
		int w2 = thresholdedImp.getWindow().getWidth();
		int h2 = thresholdedImp.getWindow().getHeight();
		for ( int i = 0; i < nZoomOut; i++ )
		{
			thresholdedImp.getCanvas().zoomOut( w2 / 2, h2 / 2 );
			w2 = thresholdedImp.getWindow().getWidth();
			h2 = thresholdedImp.getWindow().getHeight();
		}
		thresholdedImp.getWindow().setLocation( x, y + h1 );

	}
}
