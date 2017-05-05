package net.imagej.circleskinner.gui;

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.scijava.command.Command;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

import com.jidesoft.swing.RangeSlider;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.measure.ResultsTable;
import io.scif.FormatException;
import io.scif.config.SCIFIOConfig;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.circleskinner.CircleSkinnerOp;
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
public class CircleSkinnerGUI< T extends RealType< T > & NativeType< T > > extends JFrame implements Command
{
	private static final long serialVersionUID = 1L;

	private static final String PNG_OUTPUT_FOLDER = "PNGs";

	public static final String PLUGIN_NAME = "CircleSkinner";

	public static final String PLUGIN_VERSION = "1.0.1-SNAPSHOT";

	private static final int DEFAULT_MIN_RADIUS = 50;

	private static final int DEFAULT_MAX_RADIUS = 100;

	private static final int DEFAULT_THICKNESS = 10;

	private static final double DEFAULT_THRESHOLD_FACTOR = 100.;

	private static final double DEFAULT_SENSITIVITY = 150.;

	private static final int DEFAULT_STEP_RADIUS = 2;

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
	private PrefService prefs;

//	@Parameter
//	private CommandService commandService;

//	@Parameter
//	private ThreadService threadService;

	/*
	 * FIELDS
	 */

	/**
	 * The circle thickness (crown thickness), in pixel units.
	 */
	private int circleThickness;

	private double thresholdFactor = 100.;

	private double sensitivity = 100.;

	private int minRadius = 50;

	private int maxRadius = 100;

	private int stepRadius = 2;

	private AnalysisTarget analysisTarget = AnalysisTarget.CURRENT_IMAGE;

	private File folder;

	private boolean saveSnapshot = false;

	private ResultsTable resultsTable;

	private Display< String > messages;

	/*
	 * CONSTRUCTOR.
	 */

	public CircleSkinnerGUI()
	{
	}

	/*
	 * METHODS.
	 */

	@Override
	public void run()
	{

		/*
		 * Try to read parameters from Prefs.
		 */

		this.circleThickness = prefs.getInt( CircleSkinnerGUI.class, "circleThickness", DEFAULT_THICKNESS );
		this.thresholdFactor = prefs.getDouble( CircleSkinnerGUI.class, "thresholdFactor", DEFAULT_THRESHOLD_FACTOR );
		this.sensitivity = prefs.getDouble( CircleSkinnerGUI.class, "sensitivity", DEFAULT_SENSITIVITY );
		this.minRadius = prefs.getInt( CircleSkinnerGUI.class, "minRadius", DEFAULT_MIN_RADIUS );
		this.maxRadius = prefs.getInt( CircleSkinnerGUI.class, "maxRadius", DEFAULT_MAX_RADIUS );
		this.stepRadius = prefs.getInt( CircleSkinnerGUI.class, "stepRadius", DEFAULT_STEP_RADIUS );
//		this.analysisTarget = AnalysisTarget.valueOf( prefs.get( CircleSkinnerGUI.class, "analysisTarget", AnalysisTarget.CURRENT_IMAGE.name() ) );
		this.analysisTarget = AnalysisTarget.CURRENT_IMAGE;
		this.folder = new File( prefs.get( CircleSkinnerGUI.class, "folder", System.getProperty( "user.home" ) ) );
		this.saveSnapshot = prefs.getBoolean( CircleSkinnerGUI.class, "saveSnapshot", true );
		
		/*
		 * Init GUI.
		 */

		final JPanel panel = new JPanel();
		getContentPane().add( panel, BorderLayout.CENTER );
		panel.setLayout( new BorderLayout( 0, 0 ) );

		final JPanel panelButtons = new JPanel();
		final FlowLayout flowLayout = ( FlowLayout ) panelButtons.getLayout();
		flowLayout.setAlignment( FlowLayout.TRAILING );
		panel.add( panelButtons, BorderLayout.SOUTH );

		final JButton btnRun = new JButton( "Run" );
		panelButtons.add( btnRun );

		final JPanel parametersPanel = new JPanel();
		panel.add( parametersPanel, BorderLayout.NORTH );
		final GridBagLayout gbl_parametersPanel = new GridBagLayout();
		gbl_parametersPanel.columnWidths = new int[] { 100, 70, 100, 70 };
		gbl_parametersPanel.columnWeights = new double[] { 0.1, 0.15, 0.6, 0.15 };
		gbl_parametersPanel.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0, 40, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		gbl_parametersPanel.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, };
		parametersPanel.setLayout( gbl_parametersPanel );

		final JLabel lblTitle = new JLabel( PLUGIN_NAME + " v" + PLUGIN_VERSION );
		lblTitle.setFont( panel.getFont().deriveFont( Font.BOLD ).deriveFont( 15f ) );
		final GridBagConstraints gbc_lblTitle = new GridBagConstraints();
		gbc_lblTitle.gridwidth = 4;
		gbc_lblTitle.insets = new Insets( 15, 5, 15, 0 );
		gbc_lblTitle.gridx = 0;
		gbc_lblTitle.gridy = 0;
		parametersPanel.add( lblTitle, gbc_lblTitle );

		final JLabel lblparameters = new JLabel( "Parameters:" );
		lblparameters.setFont( panel.getFont().deriveFont( Font.BOLD ).deriveFont( 13f ) );
		final GridBagConstraints gbc_lblparameters = new GridBagConstraints();
		gbc_lblparameters.insets = new Insets( 15, 5, 5, 0 );
		gbc_lblparameters.anchor = GridBagConstraints.WEST;
		gbc_lblparameters.gridwidth = 4;
		gbc_lblparameters.gridx = 0;
		gbc_lblparameters.gridy = 1;
		parametersPanel.add( lblparameters, gbc_lblparameters );

		final JLabel lblCircleThicknesspixels = new JLabel( "Circle thickness (pixels)" );
		final GridBagConstraints gbc_lblCircleThicknesspixels = new GridBagConstraints();
		gbc_lblCircleThicknesspixels.anchor = GridBagConstraints.EAST;
		gbc_lblCircleThicknesspixels.insets = new Insets( 0, 5, 5, 5 );
		gbc_lblCircleThicknesspixels.gridx = 0;
		gbc_lblCircleThicknesspixels.gridy = 2;
		parametersPanel.add( lblCircleThicknesspixels, gbc_lblCircleThicknesspixels );

		final JSlider sliderThickness = new JSlider();
		sliderThickness.setValue( circleThickness );
		sliderThickness.setPaintLabels( true );
		sliderThickness.setPaintTicks( true );
		sliderThickness.setMajorTickSpacing( 10 );
		sliderThickness.setMinorTickSpacing( 2 );
		sliderThickness.setMaximum( 31 );
		sliderThickness.setMinimum( 1 );
		final GridBagConstraints gbc_sliderThifckness = new GridBagConstraints();
		gbc_sliderThifckness.gridwidth = 2;
		gbc_sliderThifckness.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderThifckness.insets = new Insets( 0, 0, 5, 5 );
		gbc_sliderThifckness.gridx = 1;
		gbc_sliderThifckness.gridy = 2;
		parametersPanel.add( sliderThickness, gbc_sliderThifckness );
		
		final JSpinner spinnerThickness = new JSpinner( new SpinnerNumberModel( circleThickness, 1, 31, 1 ) );
		final GridBagConstraints gbc_spinnerThickness = new GridBagConstraints();
		gbc_spinnerThickness.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerThickness.insets = new Insets( 0, 0, 5, 0 );
		gbc_spinnerThickness.gridx = 3;
		gbc_spinnerThickness.gridy = 2;
		parametersPanel.add( spinnerThickness, gbc_spinnerThickness );
		
		spinnerThickness.addChangeListener( ( e ) -> sliderThickness.setValue( ( int ) spinnerThickness.getValue() ) );
		sliderThickness.addChangeListener( new ChangeListener()
		{

			@Override
			public void stateChanged( final ChangeEvent e )
			{
				circleThickness = sliderThickness.getValue();
				prefs.put( CircleSkinnerGUI.class, "circleThickness", circleThickness );
				spinnerThickness.setValue( sliderThickness.getValue() );
			}
		} );

		final JLabel lblThresholdAdjustment = new JLabel( "Threshold adjustment (%)" );
		final GridBagConstraints gbc_lblThresholdAdjustment = new GridBagConstraints();
		gbc_lblThresholdAdjustment.anchor = GridBagConstraints.EAST;
		gbc_lblThresholdAdjustment.insets = new Insets( 0, 5, 5, 5 );
		gbc_lblThresholdAdjustment.gridx = 0;
		gbc_lblThresholdAdjustment.gridy = 3;
		parametersPanel.add( lblThresholdAdjustment, gbc_lblThresholdAdjustment );

		final JSlider sliderThreshold = new JSlider();
		sliderThreshold.setPaintLabels( true );
		sliderThreshold.setMinorTickSpacing( 10 );
		sliderThreshold.setMajorTickSpacing( 50 );
		sliderThreshold.setMaximum( 200 );
		sliderThreshold.setValue( ( int ) thresholdFactor );
		sliderThreshold.setPaintTicks( true );
		final GridBagConstraints gbc_sliderThreshold = new GridBagConstraints();
		gbc_sliderThreshold.gridwidth = 2;
		gbc_sliderThreshold.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderThreshold.insets = new Insets( 0, 0, 5, 5 );
		gbc_sliderThreshold.gridx = 1;
		gbc_sliderThreshold.gridy = 3;
		parametersPanel.add( sliderThreshold, gbc_sliderThreshold );

		final JSpinner spinnerThreshold = new JSpinner( new SpinnerNumberModel( ( int ) thresholdFactor, 0, 200, 10 ) );
		final GridBagConstraints gbc_spinnerThreshold = new GridBagConstraints();
		gbc_spinnerThreshold.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerThreshold.insets = new Insets( 0, 0, 5, 0 );
		gbc_spinnerThreshold.gridx = 3;
		gbc_spinnerThreshold.gridy = 3;
		parametersPanel.add( spinnerThreshold, gbc_spinnerThreshold );

		spinnerThreshold.addChangeListener( ( e ) -> sliderThreshold.setValue( ( int ) spinnerThreshold.getValue() ) );
		sliderThreshold.addChangeListener( new ChangeListener()
		{

			@Override
			public void stateChanged( final ChangeEvent e )
			{
				thresholdFactor = sliderThreshold.getValue();
				prefs.put( CircleSkinnerGUI.class, "thresholdFactor", thresholdFactor );
				spinnerThreshold.setValue( sliderThreshold.getValue() );
			}
		} );

		final JLabel lblCircleDetectionSensitivity = new JLabel( "Circle detection sensitivity" );
		final GridBagConstraints gbc_lblCircleDetectionSensitivity = new GridBagConstraints();
		gbc_lblCircleDetectionSensitivity.anchor = GridBagConstraints.EAST;
		gbc_lblCircleDetectionSensitivity.insets = new Insets( 0, 5, 5, 5 );
		gbc_lblCircleDetectionSensitivity.gridx = 0;
		gbc_lblCircleDetectionSensitivity.gridy = 4;
		parametersPanel.add( lblCircleDetectionSensitivity, gbc_lblCircleDetectionSensitivity );

		final JSlider sliderSensitivity = new JSlider();
		sliderSensitivity.setValue( ( int ) sensitivity );
		sliderSensitivity.setMinorTickSpacing( 25 );
		sliderSensitivity.setMajorTickSpacing( 100 );
		sliderSensitivity.setPaintLabels( true );
		sliderSensitivity.setPaintTicks( true );
		sliderSensitivity.setMaximum( 500 );
		final GridBagConstraints gbc_sliderSensitivity = new GridBagConstraints();
		gbc_sliderSensitivity.gridwidth = 2;
		gbc_sliderSensitivity.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderSensitivity.insets = new Insets( 0, 0, 5, 5 );
		gbc_sliderSensitivity.gridx = 1;
		gbc_sliderSensitivity.gridy = 4;
		parametersPanel.add( sliderSensitivity, gbc_sliderSensitivity );

		final JSpinner spinnerSentivity = new JSpinner( new SpinnerNumberModel( ( int ) sensitivity, 0, 500, 10 ) );
		final GridBagConstraints gbc_spinnerSentivity = new GridBagConstraints();
		gbc_spinnerSentivity.insets = new Insets( 0, 0, 5, 0 );
		gbc_spinnerSentivity.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerSentivity.gridx = 3;
		gbc_spinnerSentivity.gridy = 4;
		parametersPanel.add( spinnerSentivity, gbc_spinnerSentivity );

		spinnerSentivity.addChangeListener( ( e ) -> sliderSensitivity.setValue( ( int ) spinnerSentivity.getValue() ) );
		sliderSensitivity.addChangeListener( new ChangeListener()
		{

			@Override
			public void stateChanged( final ChangeEvent e )
			{
				sensitivity = sliderSensitivity.getValue();
				prefs.put( CircleSkinnerGUI.class, "sensitivity", sensitivity );
				spinnerSentivity.setValue( sliderSensitivity.getValue() );
			}
		} );

		final JCheckBox chckbxAdvancedParameters = new JCheckBox( "Advanced parameters:" );
		chckbxAdvancedParameters.setFont( panel.getFont().deriveFont( Font.BOLD ).deriveFont( 13f ) );
		chckbxAdvancedParameters.setHorizontalTextPosition( SwingConstants.LEFT );
		final GridBagConstraints gbc_chckbxAdvancedParameters = new GridBagConstraints();
		gbc_chckbxAdvancedParameters.insets = new Insets( 15, 5, 5, 0 );
		gbc_chckbxAdvancedParameters.anchor = GridBagConstraints.WEST;
		gbc_chckbxAdvancedParameters.gridwidth = 4;
		gbc_chckbxAdvancedParameters.gridx = 0;
		gbc_chckbxAdvancedParameters.gridy = 5;
		parametersPanel.add( chckbxAdvancedParameters, gbc_chckbxAdvancedParameters );

		final JLabel lblMinMax = new JLabel( "Min & Max radius (pixels)" );
		final GridBagConstraints gbc_lblMinMax = new GridBagConstraints();
		gbc_lblMinMax.anchor = GridBagConstraints.EAST;
		gbc_lblMinMax.insets = new Insets( 0, 5, 5, 5 );
		gbc_lblMinMax.gridx = 0;
		gbc_lblMinMax.gridy = 6;
		parametersPanel.add( lblMinMax, gbc_lblMinMax );

		final JSpinner spinnerMinRadius = new JSpinner( new SpinnerNumberModel( minRadius, 10, 300, 2 ) );
		final GridBagConstraints gbc_spinnerMinRadius = new GridBagConstraints();
		gbc_spinnerMinRadius.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerMinRadius.insets = new Insets( 0, 0, 5, 5 );
		gbc_spinnerMinRadius.gridx = 1;
		gbc_spinnerMinRadius.gridy = 6;
		parametersPanel.add( spinnerMinRadius, gbc_spinnerMinRadius );

		final JSpinner spinnerMaxRadius = new JSpinner( new SpinnerNumberModel( maxRadius, 10, 300, 2 ) );
		final GridBagConstraints gbc_spinnerMaxRadius = new GridBagConstraints();
		gbc_spinnerMaxRadius.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerMaxRadius.insets = new Insets( 0, 0, 5, 0 );
		gbc_spinnerMaxRadius.gridx = 3;
		gbc_spinnerMaxRadius.gridy = 6;
		parametersPanel.add( spinnerMaxRadius, gbc_spinnerMaxRadius );

		final RangeSlider rangeSlider = new RangeSlider( 10, 300, minRadius, maxRadius );
		rangeSlider.setMinorTickSpacing( 25 );
		rangeSlider.setMajorTickSpacing( 100 );
		rangeSlider.setPaintLabels( true );
		rangeSlider.setPaintTicks( true );

		spinnerMinRadius.addChangeListener( ( e ) -> rangeSlider.setLowValue( ( int ) spinnerMinRadius.getValue() ) );
		rangeSlider.addChangeListener( new ChangeListener()
		{

			@Override
			public void stateChanged( final ChangeEvent e )
			{
				sensitivity = rangeSlider.getLowValue();
				prefs.put( CircleSkinnerGUI.class, "minRadius", minRadius );
				spinnerMinRadius.setValue( rangeSlider.getLowValue() );
			}
		} );
		spinnerMaxRadius.addChangeListener( ( e ) -> rangeSlider.setHighValue( ( int ) spinnerMaxRadius.getValue() ) );
		rangeSlider.addChangeListener( new ChangeListener()
		{

			@Override
			public void stateChanged( final ChangeEvent e )
			{
				sensitivity = rangeSlider.getHighValue();
				prefs.put( CircleSkinnerGUI.class, "maxRadius", maxRadius );
				spinnerMaxRadius.setValue( rangeSlider.getHighValue() );
			}
		} );

		final GridBagConstraints gbc_rangeSlider = new GridBagConstraints();
		gbc_rangeSlider.fill = GridBagConstraints.HORIZONTAL;
		gbc_rangeSlider.insets = new Insets( 0, 0, 5, 5 );
		gbc_rangeSlider.gridx = 2;
		gbc_rangeSlider.gridy = 6;
		parametersPanel.add( rangeSlider, gbc_rangeSlider );

		final JLabel lblStepRadiuspixels = new JLabel( "Step radius (pixels)" );
		final GridBagConstraints gbc_lblStepRadiuspixels = new GridBagConstraints();
		gbc_lblStepRadiuspixels.anchor = GridBagConstraints.EAST;
		gbc_lblStepRadiuspixels.insets = new Insets( 0, 5, 5, 5 );
		gbc_lblStepRadiuspixels.gridx = 0;
		gbc_lblStepRadiuspixels.gridy = 7;
		parametersPanel.add( lblStepRadiuspixels, gbc_lblStepRadiuspixels );

		final JSpinner spinnerStepRadius = new JSpinner();
		final GridBagConstraints gbc_spinner = new GridBagConstraints();
		gbc_spinner.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinner.insets = new Insets( 0, 0, 5, 5 );
		gbc_spinner.gridx = 1;
		gbc_spinner.gridy = 7;
		parametersPanel.add( spinnerStepRadius, gbc_spinner );

		final JComponent[] advancedParameterComponents = new JComponent[] { lblMinMax, rangeSlider,
				lblStepRadiuspixels, spinnerStepRadius, spinnerMinRadius, spinnerMaxRadius };
		for ( final JComponent component : advancedParameterComponents )
			component.setEnabled( chckbxAdvancedParameters.isSelected() );
		chckbxAdvancedParameters.addActionListener( new ActionListener()
		{

			@Override
			public void actionPerformed( final ActionEvent e )
			{
				for ( final JComponent component : advancedParameterComponents )
					component.setEnabled( chckbxAdvancedParameters.isSelected() );
			}
		} );

		final JLabel lblManualParameterAdjutments = new JLabel( "Manual parameter adjustments:" );
		lblManualParameterAdjutments.setFont( panel.getFont().deriveFont( Font.BOLD ).deriveFont( 13f ) );
		final GridBagConstraints gbc_lblManualParameterAdjutments = new GridBagConstraints();
		gbc_lblManualParameterAdjutments.anchor = GridBagConstraints.WEST;
		gbc_lblManualParameterAdjutments.gridwidth = 4;
		gbc_lblManualParameterAdjutments.insets = new Insets( 15, 5, 5, 0 );
		gbc_lblManualParameterAdjutments.gridx = 0;
		gbc_lblManualParameterAdjutments.gridy = 8;
		parametersPanel.add( lblManualParameterAdjutments, gbc_lblManualParameterAdjutments );

		final JButton btnAdjustThreshold = new JButton( "Launch" );
		btnAdjustThreshold.setToolTipText( TOOLTIP_ADJUST_THRESHOLD );
		btnAdjustThreshold.addActionListener( ( e ) -> adjustThreshold() );
		final GridBagConstraints gbc_btnAdjustThreshold = new GridBagConstraints();
		gbc_btnAdjustThreshold.anchor = GridBagConstraints.EAST;
		gbc_btnAdjustThreshold.insets = new Insets( 0, 0, 5, 5 );
		gbc_btnAdjustThreshold.gridx = 0;
		gbc_btnAdjustThreshold.gridy = 9;
		parametersPanel.add( btnAdjustThreshold, gbc_btnAdjustThreshold );

		final JLabel lblAdjustThreshold = new JLabel( "Adjust thickness and threshold." );
		lblAdjustThreshold.setToolTipText( TOOLTIP_ADJUST_THRESHOLD );
		final GridBagConstraints gbc_lblAdjustThreshold = new GridBagConstraints();
		gbc_lblAdjustThreshold.insets = new Insets( 0, 0, 5, 0 );
		gbc_lblAdjustThreshold.anchor = GridBagConstraints.WEST;
		gbc_lblAdjustThreshold.gridwidth = 3;
		gbc_lblAdjustThreshold.gridx = 1;
		gbc_lblAdjustThreshold.gridy = 9;
		parametersPanel.add( lblAdjustThreshold, gbc_lblAdjustThreshold );

		final JButton btnAdjustSensitivity = new JButton( "Launch" );
		btnAdjustSensitivity.addActionListener( ( e ) -> adjustSensitivity() );
		final GridBagConstraints gbc_btnAdjustSensitivity = new GridBagConstraints();
		gbc_btnAdjustSensitivity.anchor = GridBagConstraints.EAST;
		gbc_btnAdjustSensitivity.insets = new Insets( 0, 0, 5, 5 );
		gbc_btnAdjustSensitivity.gridx = 0;
		gbc_btnAdjustSensitivity.gridy = 10;
		parametersPanel.add( btnAdjustSensitivity, gbc_btnAdjustSensitivity );

		final JLabel lblAdjustDetectionSensitivity = new JLabel( "Adjust detection sensitivity." );
		final GridBagConstraints gbc_lblAdjustDetectionSensitivity = new GridBagConstraints();
		gbc_lblAdjustDetectionSensitivity.insets = new Insets( 0, 0, 5, 0 );
		gbc_lblAdjustDetectionSensitivity.gridwidth = 3;
		gbc_lblAdjustDetectionSensitivity.anchor = GridBagConstraints.WEST;
		gbc_lblAdjustDetectionSensitivity.gridx = 1;
		gbc_lblAdjustDetectionSensitivity.gridy = 10;
		parametersPanel.add( lblAdjustDetectionSensitivity, gbc_lblAdjustDetectionSensitivity );

		final JLabel lblAnalysisTarget = new JLabel( "Analysis target:" );
		lblAnalysisTarget.setFont( panel.getFont().deriveFont( Font.BOLD ).deriveFont( 13f ) );
		final GridBagConstraints gbc_lblAnalysisTarget = new GridBagConstraints();
		gbc_lblAnalysisTarget.insets = new Insets( 15, 5, 5, 0 );
		gbc_lblAnalysisTarget.anchor = GridBagConstraints.WEST;
		gbc_lblAnalysisTarget.gridwidth = 4;
		gbc_lblAnalysisTarget.gridx = 0;
		gbc_lblAnalysisTarget.gridy = 11;
		parametersPanel.add( lblAnalysisTarget, gbc_lblAnalysisTarget );

		final JCheckBox chckbxSavePngs = new JCheckBox( "Export PNGs?", saveSnapshot );
		chckbxSavePngs.setComponentOrientation( ComponentOrientation.RIGHT_TO_LEFT );
		chckbxSavePngs.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				saveSnapshot = chckbxSavePngs.isSelected();
				prefs.put( CircleSkinnerGUI.class, "saveSnapshot", saveSnapshot );
			}
		} );
		chckbxSavePngs.setToolTipText( TOOLTIP_ADJUST_SAVE_PNG );
		final GridBagConstraints gbc_chckbxSavePngs = new GridBagConstraints();
		gbc_chckbxSavePngs.insets = new Insets( 0, 0, 5, 0 );
		gbc_chckbxSavePngs.anchor = GridBagConstraints.WEST;
		gbc_chckbxSavePngs.gridwidth = 3;
		gbc_chckbxSavePngs.gridx = 1;
		gbc_chckbxSavePngs.gridy = 12;
		parametersPanel.add( chckbxSavePngs, gbc_chckbxSavePngs );

		final JLabel lblAnalyze = new JLabel( "Analyze" );
		final GridBagConstraints gbc_lblAnalyze = new GridBagConstraints();
		gbc_lblAnalyze.anchor = GridBagConstraints.EAST;
		gbc_lblAnalyze.insets = new Insets( 0, 0, 5, 5 );
		gbc_lblAnalyze.gridx = 0;
		gbc_lblAnalyze.gridy = 13;
		parametersPanel.add( lblAnalyze, gbc_lblAnalyze );

		final JRadioButton rdbtnCurrentImage = new JRadioButton( "Current image." );
		final GridBagConstraints gbc_rdbtnCurrentImage = new GridBagConstraints();
		gbc_rdbtnCurrentImage.anchor = GridBagConstraints.WEST;
		gbc_rdbtnCurrentImage.gridwidth = 2;
		gbc_rdbtnCurrentImage.insets = new Insets( 0, 0, 5, 5 );
		gbc_rdbtnCurrentImage.gridx = 1;
		gbc_rdbtnCurrentImage.gridy = 13;
		parametersPanel.add( rdbtnCurrentImage, gbc_rdbtnCurrentImage );

		final JRadioButton rdbtnFolder = new JRadioButton( "Folder:" );
		final GridBagConstraints gbc_rdbtnFolder = new GridBagConstraints();
		gbc_rdbtnFolder.anchor = GridBagConstraints.WEST;
		gbc_rdbtnFolder.gridwidth = 2;
		gbc_rdbtnFolder.insets = new Insets( 0, 0, 5, 5 );
		gbc_rdbtnFolder.gridx = 1;
		gbc_rdbtnFolder.gridy = 14;
		parametersPanel.add( rdbtnFolder, gbc_rdbtnFolder );

		final ButtonGroup buttonGroup = new ButtonGroup();
		buttonGroup.add( rdbtnCurrentImage );
		buttonGroup.add( rdbtnFolder );
		if ( analysisTarget == AnalysisTarget.CURRENT_IMAGE )
			buttonGroup.setSelected( rdbtnCurrentImage.getModel(), true );
		else
			buttonGroup.setSelected( rdbtnFolder.getModel(), true );

		final JButton btnBrowse = new JButton( "Browse" );
		final GridBagConstraints gbc_btnBrowse = new GridBagConstraints();
		gbc_btnBrowse.anchor = GridBagConstraints.EAST;
		gbc_btnBrowse.insets = new Insets( 0, 0, 5, 5 );
		gbc_btnBrowse.gridx = 0;
		gbc_btnBrowse.gridy = 15;
		parametersPanel.add( btnBrowse, gbc_btnBrowse );

		final JTextField textFieldFolder = new JTextField( folder.getAbsolutePath() );
		textFieldFolder.setFont( panel.getFont().deriveFont( 10f ) );
		final GridBagConstraints gbc_textFieldFolder = new GridBagConstraints();
		gbc_textFieldFolder.insets = new Insets( 0, 0, 5, 0 );
		gbc_textFieldFolder.gridwidth = 3;
		gbc_textFieldFolder.fill = GridBagConstraints.HORIZONTAL;
		gbc_textFieldFolder.gridx = 1;
		gbc_textFieldFolder.gridy = 15;
		parametersPanel.add( textFieldFolder, gbc_textFieldFolder );
		textFieldFolder.setColumns( 10 );

		final JLabel lblInfoFiles = new JLabel( " " );
		final GridBagConstraints gbc_lblInfoFiles = new GridBagConstraints();
		gbc_lblInfoFiles.anchor = GridBagConstraints.EAST;
		gbc_lblInfoFiles.gridwidth = 4;
		gbc_lblInfoFiles.gridx = 0;
		gbc_lblInfoFiles.gridy = 16;
		parametersPanel.add( lblInfoFiles, gbc_lblInfoFiles );

		final JComponent[] targetComponents = new JComponent[] { textFieldFolder, btnBrowse };
		final ActionListener rdnBtnListener = new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				for ( final JComponent component : targetComponents )
					component.setEnabled( rdbtnFolder.isSelected() );
				if ( rdbtnFolder.isSelected() )
					folderChanged( lblInfoFiles );
				else
					printCurrentImage( lblInfoFiles );
			}
		};

		rdbtnCurrentImage.addActionListener( rdnBtnListener );
		rdbtnFolder.addActionListener( rdnBtnListener );
		for ( final JComponent component : targetComponents )
			component.setEnabled( rdbtnFolder.isSelected() );
		textFieldFolder.addActionListener( new ActionListener()
		{

			@Override
			public void actionPerformed( final ActionEvent e )
			{
				folder = new File( textFieldFolder.getText() );
				prefs.put( CircleSkinnerGUI.class, "folder", folder.getAbsolutePath() );
				folderChanged( lblInfoFiles );
			}
		} );
		btnBrowse.addActionListener( new ActionListener()
		{

			@Override
			public void actionPerformed( final ActionEvent e )
			{
				final FileDialog fileDialog = new FileDialog( CircleSkinnerGUI.this, "CircleSkinner target folder", FileDialog.LOAD );
				fileDialog.setDirectory( textFieldFolder.getText() );
				fileDialog.setVisible( true );
				if ( null == fileDialog.getDirectory() )
					return;

				textFieldFolder.setText( fileDialog.getDirectory() );
				folder = new File( textFieldFolder.getText() );
				prefs.put( CircleSkinnerGUI.class, "folder", folder.getAbsolutePath() );
			}
		} );

		pack();
		setVisible( true );
	}
	private void process()
	{
		@SuppressWarnings( "unchecked" )
		final Display< String > m = ( Display< String > ) displayService.createDisplay(
				"CircleSkinner log", PLUGIN_NAME + " v" + PLUGIN_VERSION );
		this.messages = m;

		messages.add( "" );
		messages.add( PLUGIN_NAME + " started on " + SimpleDateFormat.getInstance().format( new Date() ) );
		switch ( analysisTarget )
		{
		case CURRENT_IMAGE:
			messages.add( " - Target: active image." );
			break;
		case FOLDER:
			messages.add( " - Target folder: " + folder );
			break;
		default:
			messages.add( " - Unknown target: " + analysisTarget );
			break;
		}
		messages.add( String.format( " - Circle thickness (pixels): %.1f", circleThickness ) );
		messages.add( String.format( " - Threshold adjusment: %.1f %%", thresholdFactor ) );
		messages.add( String.format( " - Sensitivity: %.1f", sensitivity ) );
		messages.add( String.format( " - Min. radius (pixels): %.1f", minRadius ) );
		messages.add( String.format( " - Max. radius (pixels): %.1f", maxRadius ) );
		messages.add( String.format( " - Step radius (pixels): %.1f", stepRadius ) );
		messages.add( "" );

		final long start = System.currentTimeMillis();
		resultsTable = CircleSkinnerOp.createResulsTable();

		switch ( analysisTarget )
		{
		case CURRENT_IMAGE:
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

		case FOLDER:
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

	private void adjustThreshold()
	{
		final AdjustThresholdDialog< T > adjustThresholdDialog = new AdjustThresholdDialog<>(
				imageDisplayService.getActiveImageDisplay(),
				circleThickness, thresholdFactor / 100.,
				opService.getContext() );
		adjustThresholdDialog.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				if ( e.getActionCommand().equals( "OK" ) )
				{
					thresholdFactor = adjustThresholdDialog.getThresholdFactor() * 100.;
					circleThickness = adjustThresholdDialog.getCircleThickness();
				}
			}
		} );
		adjustThresholdDialog.setVisible( true );
	}

	private void adjustSensitivity()
	{
		// TODO
	}

	private void folderChanged( final JLabel label )
	{
		String infoFiles;
		if ( folder == null || !folder.exists() || !folder.isDirectory() )
		{
			infoFiles = "Invalid folder: " + folder;
		}
		else
		{
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
		label.setText( infoFiles );
	}

	/*
	 * PRIVATE METHODS
	 */

	private void processImage( final Dataset dataset, final ResultsTable resultsTable, final ImagePlus imp )
	{
		@SuppressWarnings( "unchecked" )
		final CircleSkinnerOp< T > circleSkinner = ( CircleSkinnerOp< T > ) Computers.unary( opService, CircleSkinnerOp.class, resultsTable,
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

	private void printCurrentImage( final JLabel label )
	{
		final Dataset dataset = imageDisplayService.getActiveDataset();
		String infoFiles;
		if ( null == dataset )
			infoFiles = String.format( "No active image." );
		else
			infoFiles = String.format( "Active image: %s.", dataset.getName() );
		label.setText( infoFiles );
	}

	private boolean canOpen( final String source )
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

	private enum AnalysisTarget
	{
		CURRENT_IMAGE( "Current image" ), FOLDER( "Folder" );

		private final String str;

		private AnalysisTarget( final String name )
		{
			this.str = name;
		}

		@Override
		public String toString()
		{
			return str;
		}
	}

	private static final String TOOLTIP_ADJUST_THRESHOLD = "Will show a separate window on which you will "
			+ "be able to tune the thickness and threshold values.";

	private static final String TOOLTIP_ADJUST_SAVE_PNG = "If checked, then a PNG capture of the color image "
			+ "plus an overlay showing detected circles will be saved in a subolder if the input folder.";

	public static void main( final String... args ) throws Exception
	{
		Locale.setDefault( Locale.ROOT );
		UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );

		final ImageJ ij = net.imagej.Main.launch( args );
		final Object dataset = ij.io().open( "samples/ca-01.lsm" );
		ij.ui().show( dataset );
		ij.command().run( CircleSkinnerGUI.class, true );
	}
}
