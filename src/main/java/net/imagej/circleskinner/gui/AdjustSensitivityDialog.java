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
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.measure.ResultsTable;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.DefaultDataset;
import net.imagej.ImgPlus;
import net.imagej.circleskinner.CircleSkinnerOp;
import net.imagej.circleskinner.hough.HoughCircle;
import net.imagej.circleskinner.util.EverythingDisablerAndReenabler;
import net.imagej.circleskinner.util.HoughCircleOverlay;
import net.imagej.display.ImageDisplay;
import net.imagej.display.ImageDisplayService;
import net.imagej.legacy.LegacyService;
import net.imagej.ops.OpService;
import net.imagej.ops.special.computer.Computers;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class AdjustSensitivityDialog< T extends RealType< T > & NativeType< T > > extends JDialog
{

	private static final long serialVersionUID = 1L;

	private static final int MAX_SENSITIVITY = 500;

	private static final int MIN_SENSITIVITY = 10;

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

	/**
	 * Will take the current display regardless of the input.
	 */
	private ImageDisplay source;

	/**
	 * The circle thickness (crown thickness), in pixel units.
	 */
	private final int circleThickness;

	private final double thresholdFactor;

	/*
	 * FIELDS.
	 */

	private final HashSet< ActionListener > listeners = new HashSet<>();

	private JLabel lblInfo;

	private double sensitivity = MAX_SENSITIVITY;

	private final int minRadius;

	private final int maxRadius;

	private final int stepRadius;

	private final Context context;

	/*
	 * CONSTRUCTOR.
	 */

	public AdjustSensitivityDialog( final ImageDisplay source,
			final int circleThickness,
			final double thresholdFactor,
			final int minRadius,
			final int maxRadius,
			final int stepRadius,
			final Context context )
	{
		this.source = source;
		this.circleThickness = circleThickness;
		this.thresholdFactor = thresholdFactor;
		this.minRadius = minRadius;
		this.maxRadius = maxRadius;
		this.stepRadius = stepRadius;
		this.context = context;
		context.inject( this );
		initialize();
	}

	/*
	 * METHODS.
	 */

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
		for ( final ActionListener listener : listeners )
			listener.actionPerformed( new ActionEvent( this, 1, "cancel" ) );

		dispose();
	}

	private void acceptAdjustment()
	{

		for ( final ActionListener listener : listeners )
			listener.actionPerformed( new ActionEvent( this, 0, "OK" ) );

		dispose();
	}

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
		gbl_panelAdjustments.columnWidths = new int[] { 0, 168, 0, 0 };
		gbl_panelAdjustments.rowHeights = new int[] { 0, 0, 0, 0 };
		gbl_panelAdjustments.columnWeights = new double[] { 0.0, 1.0, 1.0, Double.MIN_VALUE };
		gbl_panelAdjustments.rowWeights = new double[] { 0.0, 0.0, 1.0, Double.MIN_VALUE };
		panelAdjustments.setLayout( gbl_panelAdjustments );

		final JLabel lblCircleThicknesspixels = new JLabel( "Sensitivity" );
		final GridBagConstraints gbc_lblCircleThicknesspixels = new GridBagConstraints();
		gbc_lblCircleThicknesspixels.anchor = GridBagConstraints.EAST;
		gbc_lblCircleThicknesspixels.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblCircleThicknesspixels.gridx = 0;
		gbc_lblCircleThicknesspixels.gridy = 0;
		panelAdjustments.add( lblCircleThicknesspixels, gbc_lblCircleThicknesspixels );

		final JSlider sliderSensitivity = new JSlider();
		sliderSensitivity.setPaintLabels( true );
		sliderSensitivity.setValue( ( int ) sensitivity );
		sliderSensitivity.setMaximum( 500 );
		sliderSensitivity.setMinimum( 10 );
		sliderSensitivity.setMinorTickSpacing( 25 );
		sliderSensitivity.setMajorTickSpacing( 100 );
		sliderSensitivity.setPaintTicks( true );
		final GridBagConstraints gbc_sliderSensitivity = new GridBagConstraints();
		gbc_sliderSensitivity.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderSensitivity.insets = new Insets( 0, 0, 5, 5 );
		gbc_sliderSensitivity.gridx = 1;
		gbc_sliderSensitivity.gridy = 0;
		panelAdjustments.add( sliderSensitivity, gbc_sliderSensitivity );

		final JSpinner spinnerSensitivity = new JSpinner( new SpinnerNumberModel( ( int ) sensitivity, MIN_SENSITIVITY, MAX_SENSITIVITY, 10 ) );
		final GridBagConstraints gbc_spinnerSensitivity = new GridBagConstraints();
		gbc_spinnerSensitivity.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerSensitivity.insets = new Insets( 0, 0, 5, 5 );
		gbc_spinnerSensitivity.gridx = 2;
		gbc_spinnerSensitivity.gridy = 0;
		panelAdjustments.add( spinnerSensitivity, gbc_spinnerSensitivity );

		spinnerSensitivity.addChangeListener( ( e ) -> sliderSensitivity.setValue( ( int ) spinnerSensitivity.getValue() ) );
		sliderSensitivity.addChangeListener( new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				spinnerSensitivity.setValue( sliderSensitivity.getValue() );
				sensitivity = ( int ) spinnerSensitivity.getValue();
			}
		} );

		lblInfo = new JLabel( " " );
		final GridBagConstraints gbc_lblInfoPixels = new GridBagConstraints();
		gbc_lblInfoPixels.anchor = GridBagConstraints.SOUTHEAST;
		gbc_lblInfoPixels.gridwidth = 3;
		gbc_lblInfoPixels.insets = new Insets( 0, 0, 0, 5 );
		gbc_lblInfoPixels.gridx = 0;
		gbc_lblInfoPixels.gridy = 2;
		panelAdjustments.add( lblInfo, gbc_lblInfoPixels );

		lblInfo.setText( "Please wait while detecting circles." );
		pack();

		final EverythingDisablerAndReenabler reenabler = new EverythingDisablerAndReenabler( this, new Class[] { JLabel.class } );
		reenabler.disable();

		new SwingWorker< Boolean, String >()
		{

			@Override
			protected Boolean doInBackground() throws Exception
			{
				computeCircles();
				return Boolean.valueOf( true );
			}

			@Override
			protected void done()
			{
				reenabler.reenable();
				lblInfo.setText( "Detection done." );
			}
		}.execute();
	}

	private void computeCircles()
	{
		final ImagePlus imp = legacyService.getImageMap().lookupImagePlus( source );
		final ImagePlus newImp = new ImagePlus( "Slice - " + imp.getShortTitle(),
				imp.getProcessor().duplicate() );
		final Img< T > slice = ImageJFunctions.wrap( newImp );

		final ResultsTable table = CircleSkinnerOp.createResulsTable();

		@SuppressWarnings( "unchecked" )
		final CircleSkinnerOp< T > circleSkinner = ( CircleSkinnerOp< T > ) Computers.unary( opService, CircleSkinnerOp.class, ResultsTable.class,
				Dataset.class, circleThickness, thresholdFactor, MAX_SENSITIVITY, minRadius, maxRadius, stepRadius, false );
		final Dataset dataset = new DefaultDataset( context, new ImgPlus<>( slice ) );
		circleSkinner.compute( dataset, table );

		newImp.show();
		Overlay overlay = newImp.getOverlay();
		if ( null == overlay )
		{
			overlay = new Overlay();
			newImp.setOverlay( overlay );
		}
		final HoughCircleOverlay circleOverlay = new HoughCircleOverlay( newImp, sensitivity );
		overlay.add( circleOverlay, "Hough circles" );
		final Map< Integer, List< HoughCircle > > circles = circleSkinner.getCircles();
		circleOverlay.setCircles( circles );

		/*
		 * Setup window.
		 */

		final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		final double ws = screenSize.getWidth();
		final double hs = screenSize.getHeight();

		final int x = imp.getWindow().getX();
		final int y = imp.getWindow().getY();

		int w1 = newImp.getWindow().getWidth();
		int h1 = newImp.getWindow().getHeight();
		while ( w1 + x >= ws || h1 + y > hs )
		{
			newImp.getCanvas().zoomOut( w1 / 2, h1 / 2 );
			w1 = newImp.getWindow().getWidth();
			h1 = newImp.getWindow().getHeight();
		}
		newImp.getWindow().setLocation( x, y );
	}
}
