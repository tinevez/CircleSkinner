/*-
 * #%L
 * A Fiji plugin for the automated detection and quantification of circular structure in images.
 * %%
 * Copyright (C) 2016 - 2022 My Company, Inc.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package net.imagej.circleskinner.gui;

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.scijava.command.Command;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.io.location.FileLocation;
import org.scijava.io.location.Location;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.ui.UIService;
import org.scijava.util.VersionUtils;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Overlay;
import ij.measure.ResultsTable;
import io.scif.FormatException;
import io.scif.config.SCIFIOConfig;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.circleskinner.CircleSkinnerOp;
import net.imagej.circleskinner.CircleSkinnerOp.DetectionMethod;
import net.imagej.circleskinner.hough.HoughCircle;
import net.imagej.circleskinner.util.EverythingDisablerAndReenabler;
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

@Plugin( type = Command.class, menuPath = "Plugins > Circle Skinner", headless = false, iconPath = "logo.png" )
public class CircleSkinnerGUI< T extends RealType< T > & NativeType< T > > extends JFrame implements Command
{
	private static final long serialVersionUID = 1L;

	public static final ImageIcon LOGO = new ImageIcon( CircleSkinnerGUI.class.getResource( "logo.png" ) );

	private static final String PNG_OUTPUT_FOLDER = "PNGs";

	public static final String PLUGIN_NAME = "CircleSkinner";

	public static final String PLUGIN_VERSION = VersionUtils.getVersion( CircleSkinnerGUI.class );

	private static final String RESULTS_TABLE_TITLE = PLUGIN_NAME + " Results";

	private static final long DEFAULT_SEGMENTATION_CHANNEL = 1l;

	private static final int DEFAULT_MIN_RADIUS = 50;

	private static final int DEFAULT_MAX_RADIUS = 100;

	private static final int DEFAULT_THICKNESS = 10;

	private static final double DEFAULT_THRESHOLD_FACTOR = 100.;

	private static final double DEFAULT_SENSITIVITY = 150.;

	private static final int DEFAULT_STEP_RADIUS = 2;

	static final int MAX_THICKNESS = 50;

	static final int MIN_THICKNESS = 1;

	static final int MAX_THRESHOLD = 500;

	static final int MIN_THRESHOLD = 10;

	static final int MAX_SENSITIVITY = 500;

	static final int MIN_SENSITIVITY = 0;

	private static final boolean DEFAULT_LIMIT_DETECTION_NUMBER = false;

	private static final int DEFAULT_MAX_N_DETECTIONS = 20;

	private static final int MAX_MAX_N_DETECTIONS = 100;

	/*
	 * SERVICES.
	 */

	@Parameter
	private UIService uiService;

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

	@Parameter
	private LogService log;

	/*
	 * FIELDS
	 */

	/**
	 * The channel to use for segmentation, 1-based (first one is 1, not 0).
	 */
	private long segmentationChannel = DEFAULT_SEGMENTATION_CHANNEL;

	/**
	 * The circle thickness (crown thickness), in pixel units.
	 */
	private int circleThickness;

	private double thresholdFactor = 100.;

	private double sensitivity = 100.;

	private int minRadius = 50;

	private int maxRadius = 100;

	private int stepRadius = 2;

	private DetectionMethod detectionMethod = DetectionMethod.FAST;

	private boolean limitDetectionNumber = DEFAULT_LIMIT_DETECTION_NUMBER;

	private int maxNDetections = DEFAULT_MAX_N_DETECTIONS;

	private AnalysisTarget analysisTarget = AnalysisTarget.CURRENT_IMAGE;

	private File folder;

	private boolean saveSnapshot = false;

	private ResultsTable resultsTable;

	private Display< String > messages;

	private CircleSkinnerOp< T > circleSkinner;

	/*
	 * CONSTRUCTOR.
	 */

	public CircleSkinnerGUI()
	{
//		run();
	}

	/*
	 * METHODS.
	 */

	@Override
	public void run()
	{
		setTitle( PLUGIN_NAME );
		setIconImage( LOGO.getImage() );

		/*
		 * Try to read parameters from Prefs.
		 */

		this.segmentationChannel = prefs.getLong( CircleSkinnerGUI.class, "segmentationChannel", DEFAULT_SEGMENTATION_CHANNEL );
		this.circleThickness = prefs.getInt( CircleSkinnerGUI.class, "circleThickness", DEFAULT_THICKNESS );
		this.thresholdFactor = prefs.getDouble( CircleSkinnerGUI.class, "thresholdFactor", DEFAULT_THRESHOLD_FACTOR );
		this.sensitivity = prefs.getDouble( CircleSkinnerGUI.class, "sensitivity", DEFAULT_SENSITIVITY );
		this.minRadius = prefs.getInt( CircleSkinnerGUI.class, "minRadius", DEFAULT_MIN_RADIUS );
		this.maxRadius = prefs.getInt( CircleSkinnerGUI.class, "maxRadius", DEFAULT_MAX_RADIUS );
		this.stepRadius = prefs.getInt( CircleSkinnerGUI.class, "stepRadius", DEFAULT_STEP_RADIUS );
		this.detectionMethod = DetectionMethod.valueOf( prefs.get( CircleSkinnerGUI.class, "detectionMethod", DetectionMethod.FAST.name() ) );
		this.limitDetectionNumber = prefs.getBoolean( CircleSkinnerGUI.class, "limitDetectionNumber", DEFAULT_LIMIT_DETECTION_NUMBER );
		this.maxNDetections = prefs.getInt( CircleSkinnerGUI.class, "maxNDetections", DEFAULT_MAX_N_DETECTIONS );
		this.analysisTarget = AnalysisTarget.valueOf( prefs.get( CircleSkinnerGUI.class,
				"analysisTarget", AnalysisTarget.CURRENT_IMAGE.name() ) );
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
		btnRun.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				if ( !btnRun.getText().toLowerCase().equals( "run" ) )
				{
					cancel();
				}
				else
				{
					final EverythingDisablerAndReenabler reenabler = new EverythingDisablerAndReenabler(
							CircleSkinnerGUI.this, new Class[] { JLabel.class } );
					reenabler.disable();
					btnRun.setText( "Cancel" );
					btnRun.setEnabled( true );
					new SwingWorker< Boolean, String >()
					{

						@Override
						protected Boolean doInBackground() throws Exception
						{
							CircleSkinnerGUI.this.process();
							final boolean canceled = circleSkinner.isCanceled();
							circleSkinner = null;
							return Boolean.valueOf( !canceled );
						}

						@Override
						protected void done()
						{
							try
							{
								btnRun.setText( "Run" );
								reenabler.reenable();
								get();
							}
							catch ( final ExecutionException ee )
							{
								ee.getCause().printStackTrace();
								final String msg = String.format( "Unexpected problem: %s",
										ee.getCause().toString() );
								log.error( msg );
							}
							catch ( final InterruptedException ie )
							{
								ie.printStackTrace();
							}

						}
					}.execute();
				}
			}

		} );
		panelButtons.add( btnRun );

		final JPanel parametersPanel = new JPanel();
		parametersPanel.setLayout( new BoxLayout( parametersPanel, BoxLayout.PAGE_AXIS ) );
		parametersPanel.addPropertyChangeListener( ( e ) -> pack() );

		panel.add( parametersPanel, BorderLayout.CENTER );

		final JLabel lblTitle = new JLabel( PLUGIN_NAME + " v" + PLUGIN_VERSION );
		lblTitle.setFont( panel.getFont().deriveFont( Font.BOLD ).deriveFont( 15f ) );
		lblTitle.setHorizontalAlignment( SwingConstants.CENTER );
		lblTitle.setPreferredSize( new Dimension( 50, 50 ) );
		panel.add( lblTitle, BorderLayout.NORTH );

		/*
		 * Parameters.
		 */

		final JPanel parametersCollapsible = new JPanel();
		final GridBagLayout gbl_parametersPanel = new GridBagLayout();
		gbl_parametersPanel.columnWidths = new int[] { 100, 70, 100, 70 };
		gbl_parametersPanel.columnWeights = new double[] { 0.1, 0.15, 0.6, 0.15 };
		parametersCollapsible.setLayout( gbl_parametersPanel );

		final JButton toggleParameters = new JButton();
		toggleParameters.setAction( new AbstractAction( "Parameters:" )
		{

			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed( final ActionEvent e )
			{
				parametersCollapsible.setVisible( !parametersCollapsible.isVisible() );
				pack();
			}
		} );
		toggleParameters.setFont( panel.getFont().deriveFont( Font.BOLD ).deriveFont( 13f ) );
		toggleParameters.setAlignmentX( 0f );
		parametersPanel.add( toggleParameters );

		final JLabel lblSegmentationChannel = new JLabel( "Segmentation channel" );
		final GridBagConstraints gbc_lblSegmentationChannel = new GridBagConstraints();
		gbc_lblSegmentationChannel.anchor = GridBagConstraints.EAST;
		gbc_lblSegmentationChannel.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblSegmentationChannel.gridx = 0;
		gbc_lblSegmentationChannel.gridy = 0;
		parametersCollapsible.add( lblSegmentationChannel, gbc_lblSegmentationChannel );

		final JSpinner spinnerSegmentationChannel = new JSpinner( new SpinnerNumberModel( segmentationChannel, 1l, 99l, 1 ) );
		final GridBagConstraints gbc_spinnerSegmentationChannel = new GridBagConstraints();
		gbc_spinnerSegmentationChannel.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerSegmentationChannel.insets = new Insets( 5, 5, 5, 5 );
		gbc_spinnerSegmentationChannel.gridx = 1;
		gbc_spinnerSegmentationChannel.gridy = 0;
		parametersCollapsible.add( spinnerSegmentationChannel, gbc_spinnerSegmentationChannel );

		spinnerSegmentationChannel.addChangeListener( new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				segmentationChannel = ( ( Number ) spinnerSegmentationChannel.getModel().getValue() ).longValue();
				final Dataset dataset = imageDisplayService.getActiveDataset();
				if ( null == dataset )
					return;

				if ( dataset.getChannels() < segmentationChannel )
				{
					segmentationChannel = dataset.getChannels();
					spinnerSegmentationChannel.setValue( segmentationChannel );
				}

			}
		} );

		final JLabel lblCircleThicknesspixels = new JLabel( "Circle thickness (pixels)" );
		final GridBagConstraints gbc_lblCircleThicknesspixels = new GridBagConstraints();
		gbc_lblCircleThicknesspixels.anchor = GridBagConstraints.EAST;
		gbc_lblCircleThicknesspixels.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblCircleThicknesspixels.gridx = 0;
		gbc_lblCircleThicknesspixels.gridy = 1;
		parametersCollapsible.add( lblCircleThicknesspixels, gbc_lblCircleThicknesspixels );

		final JSlider sliderThickness = new JSlider();
		sliderThickness.setPaintLabels( true );
		sliderThickness.setPaintTicks( true );
		sliderThickness.setMajorTickSpacing( 10 );
		sliderThickness.setMinorTickSpacing( 2 );
		sliderThickness.setMaximum( MAX_THICKNESS );
		sliderThickness.setMinimum( MIN_THICKNESS );
		sliderThickness.setValue( circleThickness );
		final GridBagConstraints gbc_sliderThifckness = new GridBagConstraints();
		gbc_sliderThifckness.gridwidth = 2;
		gbc_sliderThifckness.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderThifckness.insets = new Insets( 5, 5, 5, 5 );
		gbc_sliderThifckness.gridx = 1;
		gbc_sliderThifckness.gridy = 1;
		parametersCollapsible.add( sliderThickness, gbc_sliderThifckness );

		final JSpinner spinnerThickness = new JSpinner( new SpinnerNumberModel( circleThickness, MIN_THICKNESS, MAX_THICKNESS, 1 ) );
		final GridBagConstraints gbc_spinnerThickness = new GridBagConstraints();
		gbc_spinnerThickness.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerThickness.insets = new Insets( 5, 5, 5, 5 );
		gbc_spinnerThickness.gridx = 3;
		gbc_spinnerThickness.gridy = 1;
		parametersCollapsible.add( spinnerThickness, gbc_spinnerThickness );

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
		gbc_lblThresholdAdjustment.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblThresholdAdjustment.gridx = 0;
		gbc_lblThresholdAdjustment.gridy = 2;
		parametersCollapsible.add( lblThresholdAdjustment, gbc_lblThresholdAdjustment );

		final JSlider sliderThreshold = new JSlider();
		sliderThreshold.setPaintLabels( true );
		sliderThreshold.setMinorTickSpacing( 20 );
		sliderThreshold.setMajorTickSpacing( 100 );
		sliderThreshold.setMaximum( MAX_THRESHOLD );
		sliderThreshold.setMinimum( MIN_THRESHOLD );
		sliderThreshold.setValue( ( int ) thresholdFactor );
		sliderThreshold.setPaintTicks( true );
		final GridBagConstraints gbc_sliderThreshold = new GridBagConstraints();
		gbc_sliderThreshold.gridwidth = 2;
		gbc_sliderThreshold.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderThreshold.insets = new Insets( 5, 5, 5, 5 );
		gbc_sliderThreshold.gridx = 1;
		gbc_sliderThreshold.gridy = 2;
		parametersCollapsible.add( sliderThreshold, gbc_sliderThreshold );

		final JSpinner spinnerThreshold = new JSpinner( new SpinnerNumberModel( ( int ) thresholdFactor, MIN_THRESHOLD, MAX_THRESHOLD, 10 ) );
		final GridBagConstraints gbc_spinnerThreshold = new GridBagConstraints();
		gbc_spinnerThreshold.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerThreshold.insets = new Insets( 5, 5, 5, 5 );
		gbc_spinnerThreshold.gridx = 3;
		gbc_spinnerThreshold.gridy = 2;
		parametersCollapsible.add( spinnerThreshold, gbc_spinnerThreshold );

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
		gbc_lblCircleDetectionSensitivity.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblCircleDetectionSensitivity.gridx = 0;
		gbc_lblCircleDetectionSensitivity.gridy = 3;
		parametersCollapsible.add( lblCircleDetectionSensitivity, gbc_lblCircleDetectionSensitivity );

		final JSlider sliderSensitivity = new JSlider();
		sliderSensitivity.setMinorTickSpacing( 25 );
		sliderSensitivity.setMajorTickSpacing( 100 );
		sliderSensitivity.setPaintLabels( true );
		sliderSensitivity.setPaintTicks( true );
		sliderSensitivity.setMaximum( MAX_SENSITIVITY );
		sliderSensitivity.setMinimum( MIN_SENSITIVITY );
		sliderSensitivity.setValue( ( int ) sensitivity );
		final GridBagConstraints gbc_sliderSensitivity = new GridBagConstraints();
		gbc_sliderSensitivity.gridwidth = 2;
		gbc_sliderSensitivity.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderSensitivity.insets = new Insets( 5, 5, 5, 5 );
		gbc_sliderSensitivity.gridx = 1;
		gbc_sliderSensitivity.gridy = 3;
		parametersCollapsible.add( sliderSensitivity, gbc_sliderSensitivity );

		final JSpinner spinnerSentivity = new JSpinner( new SpinnerNumberModel( ( int ) sensitivity, MIN_SENSITIVITY, MAX_SENSITIVITY, 10 ) );
		final GridBagConstraints gbc_spinnerSentivity = new GridBagConstraints();
		gbc_spinnerSentivity.insets = new Insets( 5, 5, 5, 5 );
		gbc_spinnerSentivity.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerSentivity.gridx = 3;
		gbc_spinnerSentivity.gridy = 3;
		parametersCollapsible.add( spinnerSentivity, gbc_spinnerSentivity );

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
		parametersCollapsible.setAlignmentX( 0f );
		parametersPanel.add( parametersCollapsible );

		/*
		 * Advanced parameters.
		 */

		final JPanel advancedParametersCollapsible = new JPanel();
		advancedParametersCollapsible.addPropertyChangeListener( ( e ) -> pack() );

		final GridBagLayout gbl_advancedParametersPanel = new GridBagLayout();
		gbl_advancedParametersPanel.columnWidths = new int[] { 100, 70, 100, 70 };
		gbl_advancedParametersPanel.columnWeights = new double[] { 0.1, 0.15, 0.6, 0.15 };
		advancedParametersCollapsible.setLayout( gbl_advancedParametersPanel );

		final JButton toggleAdvancedParameters = new JButton();
		toggleAdvancedParameters.setAction( new AbstractAction( "Advanced parameters:" )
		{

			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed( final ActionEvent e )
			{
				advancedParametersCollapsible.setVisible( !advancedParametersCollapsible.isVisible() );
				pack();
			}
		} );
		advancedParametersCollapsible.setVisible( false );

		toggleAdvancedParameters.setFont( panel.getFont().deriveFont( Font.BOLD ).deriveFont( 13f ) );
		toggleAdvancedParameters.setAlignmentX( 0f );
		parametersPanel.add( toggleAdvancedParameters );

		final JLabel lblMinMax = new JLabel( "Min & Max radius (pixels)" );
		final GridBagConstraints gbc_lblMinMax = new GridBagConstraints();
		gbc_lblMinMax.anchor = GridBagConstraints.EAST;
		gbc_lblMinMax.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblMinMax.gridx = 0;
		gbc_lblMinMax.gridy = 0;
		advancedParametersCollapsible.add( lblMinMax, gbc_lblMinMax );

		final JSpinner spinnerMinRadius = new JSpinner( new SpinnerNumberModel( minRadius, 5, 300, 2 ) );
		final GridBagConstraints gbc_spinnerMinRadius = new GridBagConstraints();
		gbc_spinnerMinRadius.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerMinRadius.insets = new Insets( 5, 5, 5, 5 );
		gbc_spinnerMinRadius.gridx = 1;
		gbc_spinnerMinRadius.gridy = 0;
		advancedParametersCollapsible.add( spinnerMinRadius, gbc_spinnerMinRadius );

		final JSpinner spinnerMaxRadius = new JSpinner( new SpinnerNumberModel( maxRadius, 5, 300, 2 ) );
		final GridBagConstraints gbc_spinnerMaxRadius = new GridBagConstraints();
		gbc_spinnerMaxRadius.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerMaxRadius.insets = new Insets( 5, 5, 5, 5 );
		gbc_spinnerMaxRadius.gridx = 3;
		gbc_spinnerMaxRadius.gridy = 0;
		advancedParametersCollapsible.add( spinnerMaxRadius, gbc_spinnerMaxRadius );

		final RangeSlider rangeSlider = new RangeSlider( 5, 300 );
		rangeSlider.setValue( minRadius );
		rangeSlider.setUpperValue( maxRadius );
		rangeSlider.addChangeListener( new ChangeListener()
		{

			@Override
			public void stateChanged( final ChangeEvent e )
			{

				final int minVal = rangeSlider.getValue();
				final int maxVal = rangeSlider.getUpperValue();

				minRadius = minVal;
				prefs.put( CircleSkinnerGUI.class, "minRadius", minRadius );
				spinnerMinRadius.setValue( minVal );

				maxRadius = maxVal;
				prefs.put( CircleSkinnerGUI.class, "maxRadius", maxRadius );
				spinnerMaxRadius.setValue( maxVal );
			}
		} );

		spinnerMinRadius.addChangeListener( ( e ) -> rangeSlider.setValue( ( int ) spinnerMinRadius.getValue() ) );
		spinnerMaxRadius.addChangeListener( ( e ) -> rangeSlider.setUpperValue( ( int ) spinnerMaxRadius.getValue() ) );

		final GridBagConstraints gbc_rangeSlider = new GridBagConstraints();
		gbc_rangeSlider.fill = GridBagConstraints.HORIZONTAL;
		gbc_rangeSlider.insets = new Insets( 5, 5, 5, 5 );
		gbc_rangeSlider.gridx = 2;
		gbc_rangeSlider.gridy = 0;
		advancedParametersCollapsible.add( rangeSlider, gbc_rangeSlider );

		final JLabel lblStepRadiuspixels = new JLabel( "Step radius (pixels)" );
		final GridBagConstraints gbc_lblStepRadiuspixels = new GridBagConstraints();
		gbc_lblStepRadiuspixels.anchor = GridBagConstraints.EAST;
		gbc_lblStepRadiuspixels.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblStepRadiuspixels.gridx = 0;
		gbc_lblStepRadiuspixels.gridy = 1;
		advancedParametersCollapsible.add( lblStepRadiuspixels, gbc_lblStepRadiuspixels );

		final JSpinner spinnerStepRadius = new JSpinner( new SpinnerNumberModel( stepRadius, 1, 100, 1 ) );
		final GridBagConstraints gbc_spinnerStepRadius = new GridBagConstraints();
		gbc_spinnerStepRadius.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerStepRadius.insets = new Insets( 5, 5, 5, 5 );
		gbc_spinnerStepRadius.gridx = 1;
		gbc_spinnerStepRadius.gridy = 1;
		advancedParametersCollapsible.add( spinnerStepRadius, gbc_spinnerStepRadius );

		spinnerStepRadius.addChangeListener( new ChangeListener()
		{

			@Override
			public void stateChanged( final ChangeEvent e )
			{
				stepRadius = ( int ) spinnerStepRadius.getValue();
				prefs.put( CircleSkinnerGUI.class, "stepRadius", stepRadius );
			}
		} );

		final JCheckBox chckbxLimitNumberOf = new JCheckBox( "Limit number of detections", limitDetectionNumber );
		chckbxLimitNumberOf.setHorizontalTextPosition( SwingConstants.LEFT );
		final GridBagConstraints gbc_chckbxLimitNumberOf = new GridBagConstraints();
		gbc_chckbxLimitNumberOf.anchor = GridBagConstraints.EAST;
		gbc_chckbxLimitNumberOf.gridwidth = 2;
		gbc_chckbxLimitNumberOf.insets = new Insets( 5, 5, 5, 5 );
		gbc_chckbxLimitNumberOf.gridx = 0;
		gbc_chckbxLimitNumberOf.gridy = 2;
		advancedParametersCollapsible.add( chckbxLimitNumberOf, gbc_chckbxLimitNumberOf );

		final JSlider sliderMaxNDetections = new JSlider();
		sliderMaxNDetections.setMinorTickSpacing( 5 );
		sliderMaxNDetections.setMajorTickSpacing( 20 );
		sliderMaxNDetections.setPaintLabels( true );
		sliderMaxNDetections.setPaintTicks( true );
		sliderMaxNDetections.setMaximum( MAX_MAX_N_DETECTIONS );
		sliderMaxNDetections.setMinimum( 0 );
		sliderMaxNDetections.setValue( maxNDetections );
		final GridBagConstraints gbc_sliderMaxNDetections = new GridBagConstraints();
		gbc_sliderMaxNDetections.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderMaxNDetections.insets = new Insets( 0, 0, 5, 5 );
		gbc_sliderMaxNDetections.gridx = 2;
		gbc_sliderMaxNDetections.gridy = 2;
		advancedParametersCollapsible.add( sliderMaxNDetections, gbc_sliderMaxNDetections );

		final JSpinner spinnerMaxNDetections = new JSpinner( new SpinnerNumberModel( maxNDetections, 0, 100, 1 ) );
		final GridBagConstraints gbc_spinnerMaxNDetections = new GridBagConstraints();
		gbc_spinnerMaxNDetections.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerMaxNDetections.insets = new Insets( 5, 5, 5, 5 );
		gbc_spinnerMaxNDetections.gridx = 3;
		gbc_spinnerMaxNDetections.gridy = 2;
		advancedParametersCollapsible.add( spinnerMaxNDetections, gbc_spinnerMaxNDetections );

		spinnerMaxNDetections.addChangeListener( ( e ) -> sliderMaxNDetections.setValue( ( int ) spinnerMaxNDetections.getValue() ) );
		sliderMaxNDetections.addChangeListener( new ChangeListener()
		{

			@Override
			public void stateChanged( final ChangeEvent e )
			{
				maxNDetections = sliderMaxNDetections.getValue();
				prefs.put( CircleSkinnerGUI.class, "maxNDetections", maxNDetections );
				spinnerMaxNDetections.setValue( sliderMaxNDetections.getValue() );
			}
		} );

		chckbxLimitNumberOf.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				limitDetectionNumber = chckbxLimitNumberOf.isSelected();
				prefs.put( CircleSkinnerGUI.class, "limitDetectionNumber", limitDetectionNumber );
				sliderMaxNDetections.setEnabled( chckbxLimitNumberOf.isSelected() );
				spinnerMaxNDetections.setEnabled( chckbxLimitNumberOf.isSelected() );
			}
		} );

		final JLabel lblDetectionMethod = new JLabel( "Detection method" );
		final GridBagConstraints gbc_lblDetectionMethod = new GridBagConstraints();
		gbc_lblDetectionMethod.anchor = GridBagConstraints.EAST;
		gbc_lblDetectionMethod.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblDetectionMethod.gridx = 0;
		gbc_lblDetectionMethod.gridy = 3;
		gbc_lblDetectionMethod.gridwidth = 2;
		advancedParametersCollapsible.add( lblDetectionMethod, gbc_lblDetectionMethod );

		final JComboBox< DetectionMethod > jComboBoxDetectionMethod = new JComboBox<>( DetectionMethod.values() );
		jComboBoxDetectionMethod.setSelectedItem( detectionMethod );
		jComboBoxDetectionMethod.addActionListener( new ActionListener()
		{

			@Override
			public void actionPerformed( final ActionEvent e )
			{
				detectionMethod = ( DetectionMethod ) jComboBoxDetectionMethod.getSelectedItem();
				prefs.put( CircleSkinnerGUI.class, "detectionMethod", detectionMethod.name() );
			}
		} );

		final GridBagConstraints gbc_jComboBoxDetectionMethod = new GridBagConstraints();
		gbc_jComboBoxDetectionMethod.anchor = GridBagConstraints.WEST;
		gbc_jComboBoxDetectionMethod.insets = new Insets( 5, 5, 5, 5 );
		gbc_jComboBoxDetectionMethod.gridx = 2;
		gbc_jComboBoxDetectionMethod.gridy = 3;
		advancedParametersCollapsible.add( jComboBoxDetectionMethod, gbc_jComboBoxDetectionMethod );
		advancedParametersCollapsible.setAlignmentX( 0f );

		parametersPanel.add( advancedParametersCollapsible );

		/*
		 * Manual adjusters.
		 */

		final JPanel manualAdjustmentCollapsible = new JPanel();
		manualAdjustmentCollapsible.addPropertyChangeListener( ( e ) -> pack() );

		final GridBagLayout gbl_manualAdjustmentsPanel = new GridBagLayout();
		gbl_manualAdjustmentsPanel.columnWidths = new int[] { 100, 70, 100, 70 };
		gbl_manualAdjustmentsPanel.columnWeights = new double[] { 0.1, 0.15, 0.6, 0.15 };
		manualAdjustmentCollapsible.setLayout( gbl_manualAdjustmentsPanel );

		final JButton toggleManualParameterAdjutments = new JButton();
		toggleManualParameterAdjutments.setAction( new AbstractAction( "Manual parameter adjustments:" )
		{

			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed( final ActionEvent e )
			{
				manualAdjustmentCollapsible.setVisible( !manualAdjustmentCollapsible.isVisible() );
				pack();
			}
		} );
		manualAdjustmentCollapsible.setVisible( false );
		toggleManualParameterAdjutments.setFont( panel.getFont().deriveFont( Font.BOLD ).deriveFont( 13f ) );
		toggleManualParameterAdjutments.setAlignmentX( 0f );
		parametersPanel.add( toggleManualParameterAdjutments );

		final JButton btnAdjustThreshold = new JButton( "Launch" );
		btnAdjustThreshold.setToolTipText( TOOLTIP_ADJUST_THRESHOLD );
		btnAdjustThreshold.addActionListener( ( e ) -> adjustThreshold( sliderThickness, sliderThreshold ) );
		final GridBagConstraints gbc_btnAdjustThreshold = new GridBagConstraints();
		gbc_btnAdjustThreshold.anchor = GridBagConstraints.EAST;
		gbc_btnAdjustThreshold.insets = new Insets( 5, 5, 5, 5 );
		gbc_btnAdjustThreshold.gridx = 0;
		gbc_btnAdjustThreshold.gridy = 0;
		manualAdjustmentCollapsible.add( btnAdjustThreshold, gbc_btnAdjustThreshold );

		final JLabel lblAdjustThreshold = new JLabel( "Adjust thickness and threshold." );
		lblAdjustThreshold.setToolTipText( TOOLTIP_ADJUST_THRESHOLD );
		final GridBagConstraints gbc_lblAdjustThreshold = new GridBagConstraints();
		gbc_lblAdjustThreshold.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblAdjustThreshold.anchor = GridBagConstraints.WEST;
		gbc_lblAdjustThreshold.gridwidth = 3;
		gbc_lblAdjustThreshold.gridx = 1;
		gbc_lblAdjustThreshold.gridy = 0;
		manualAdjustmentCollapsible.add( lblAdjustThreshold, gbc_lblAdjustThreshold );

		final JButton btnAdjustSensitivity = new JButton( "Launch" );
		btnAdjustSensitivity.addActionListener( ( e ) -> adjustSensitivity( sliderSensitivity ) );
		final GridBagConstraints gbc_btnAdjustSensitivity = new GridBagConstraints();
		gbc_btnAdjustSensitivity.anchor = GridBagConstraints.EAST;
		gbc_btnAdjustSensitivity.insets = new Insets( 5, 5, 5, 5 );
		gbc_btnAdjustSensitivity.gridx = 0;
		gbc_btnAdjustSensitivity.gridy = 1;
		manualAdjustmentCollapsible.add( btnAdjustSensitivity, gbc_btnAdjustSensitivity );

		final JLabel lblAdjustDetectionSensitivity = new JLabel( "Adjust detection sensitivity." );
		final GridBagConstraints gbc_lblAdjustDetectionSensitivity = new GridBagConstraints();
		gbc_lblAdjustDetectionSensitivity.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblAdjustDetectionSensitivity.gridwidth = 3;
		gbc_lblAdjustDetectionSensitivity.anchor = GridBagConstraints.WEST;
		gbc_lblAdjustDetectionSensitivity.gridx = 1;
		gbc_lblAdjustDetectionSensitivity.gridy = 1;
		manualAdjustmentCollapsible.add( lblAdjustDetectionSensitivity, gbc_lblAdjustDetectionSensitivity );
		manualAdjustmentCollapsible.setAlignmentX( 0f );
		parametersPanel.add( manualAdjustmentCollapsible );

		/*
		 * Analysis target.
		 */

		final JPanel analysisTargetCollapsible = new JPanel();
		analysisTargetCollapsible.addPropertyChangeListener( ( e ) -> pack() );

		final GridBagLayout gbl_analysisTargetPanel = new GridBagLayout();
		gbl_analysisTargetPanel.columnWidths = new int[] { 100, 70, 100, 70 };
		gbl_analysisTargetPanel.columnWeights = new double[] { 0.1, 0.15, 0.6, 0.15 };
		analysisTargetCollapsible.setLayout( gbl_analysisTargetPanel );

		final JButton toggleAnalysisTarget = new JButton();
		toggleAnalysisTarget.setAction( new AbstractAction( "Analysis target:" )
		{

			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed( final ActionEvent e )
			{
				analysisTargetCollapsible.setVisible( !analysisTargetCollapsible.isVisible() );
				pack();
			}
		} );
		toggleAnalysisTarget.setFont( panel.getFont().deriveFont( Font.BOLD ).deriveFont( 13f ) );
		toggleAnalysisTarget.setAlignmentX( 0f );
		parametersPanel.add( toggleAnalysisTarget );

		final JLabel lblAnalyze = new JLabel( "Analyze" );
		final GridBagConstraints gbc_lblAnalyze = new GridBagConstraints();
		gbc_lblAnalyze.anchor = GridBagConstraints.EAST;
		gbc_lblAnalyze.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblAnalyze.gridx = 0;
		gbc_lblAnalyze.gridy = 0;
		analysisTargetCollapsible.add( lblAnalyze, gbc_lblAnalyze );

		final JRadioButton rdbtnCurrentImage = new JRadioButton( "Current image." );
		final GridBagConstraints gbc_rdbtnCurrentImage = new GridBagConstraints();
		gbc_rdbtnCurrentImage.anchor = GridBagConstraints.WEST;
		gbc_rdbtnCurrentImage.gridwidth = 2;
		gbc_rdbtnCurrentImage.insets = new Insets( 5, 5, 5, 5 );
		gbc_rdbtnCurrentImage.gridx = 1;
		gbc_rdbtnCurrentImage.gridy = 0;
		analysisTargetCollapsible.add( rdbtnCurrentImage, gbc_rdbtnCurrentImage );

		final JRadioButton rdbtnFolder = new JRadioButton( "Folder:" );
		final GridBagConstraints gbc_rdbtnFolder = new GridBagConstraints();
		gbc_rdbtnFolder.anchor = GridBagConstraints.WEST;
		gbc_rdbtnFolder.gridwidth = 2;
		gbc_rdbtnFolder.insets = new Insets( 5, 5, 5, 5 );
		gbc_rdbtnFolder.gridx = 1;
		gbc_rdbtnFolder.gridy = 1;
		analysisTargetCollapsible.add( rdbtnFolder, gbc_rdbtnFolder );

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
		gbc_btnBrowse.insets = new Insets( 5, 5, 5, 5 );
		gbc_btnBrowse.gridx = 0;
		gbc_btnBrowse.gridy = 2;
		analysisTargetCollapsible.add( btnBrowse, gbc_btnBrowse );

		final JTextField textFieldFolder = new JTextField( folder.getAbsolutePath() );
		textFieldFolder.setFont( panel.getFont().deriveFont( 10f ) );
		final GridBagConstraints gbc_textFieldFolder = new GridBagConstraints();
		gbc_textFieldFolder.insets = new Insets( 5, 5, 5, 5 );
		gbc_textFieldFolder.gridwidth = 3;
		gbc_textFieldFolder.fill = GridBagConstraints.HORIZONTAL;
		gbc_textFieldFolder.gridx = 1;
		gbc_textFieldFolder.gridy = 2;
		analysisTargetCollapsible.add( textFieldFolder, gbc_textFieldFolder );
		textFieldFolder.setColumns( 10 );

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
		gbc_chckbxSavePngs.insets = new Insets( 5, 5, 5, 5 );
		gbc_chckbxSavePngs.anchor = GridBagConstraints.WEST;
		gbc_chckbxSavePngs.gridwidth = 3;
		gbc_chckbxSavePngs.gridx = 1;
		gbc_chckbxSavePngs.gridy = 3;
		analysisTargetCollapsible.add( chckbxSavePngs, gbc_chckbxSavePngs );

		final JLabel lblInfoFiles = new JLabel( " " );
		final GridBagConstraints gbc_lblInfoFiles = new GridBagConstraints();
		gbc_lblInfoFiles.anchor = GridBagConstraints.EAST;
		gbc_lblInfoFiles.gridwidth = 4;
		gbc_lblInfoFiles.gridx = 0;
		gbc_lblInfoFiles.gridy = 4;
		analysisTargetCollapsible.add( lblInfoFiles, gbc_lblInfoFiles );

		final JComponent[] targetComponents = new JComponent[] { textFieldFolder, btnBrowse, chckbxSavePngs };
		final ActionListener rdnBtnListener = new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				for ( final JComponent component : targetComponents )
					component.setEnabled( rdbtnFolder.isSelected() );
				if ( rdbtnFolder.isSelected() )
				{
					analysisTarget = AnalysisTarget.FOLDER;
					folderChanged( lblInfoFiles );
				}
				else
				{
					analysisTarget = AnalysisTarget.CURRENT_IMAGE;
					printCurrentImage( lblInfoFiles );
				}
				prefs.put( CircleSkinnerGUI.class, "analysisTarget", analysisTarget.name() );
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
		analysisTargetCollapsible.setAlignmentX( 0f );
		parametersPanel.add( analysisTargetCollapsible );

		pack();
		setVisible( true );
	}

	private void cancel()
	{
		if ( null != circleSkinner )
			circleSkinner.cancel( "User canceled from the GUI." );
	}

	private void process()
	{
		// Determine if we need to create a new log window.
		if ( null == messages || null == uiService.getDisplayViewer( messages ) )
		{
			@SuppressWarnings( "unchecked" )
			final Display< String > m = ( Display< String > ) displayService.createDisplay( "CircleSkinner log", PLUGIN_NAME + " v" + PLUGIN_VERSION );
			this.messages = m;
		}

		messages.add( "" );
		messages.add( "____________________________________" );
		messages.add( PLUGIN_NAME + " started on " + DateFormat.getInstance().format( new Date() ) );
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

		messages.add( String.format( " - Segmentation channel (1-based): %d", segmentationChannel ) );
		messages.add( String.format( " - Circle thickness (pixels): %d", circleThickness ) );
		messages.add( String.format( " - Threshold adjustment: %.1f %%", thresholdFactor ) );
		messages.add( String.format( " - Sensitivity: %.1f", sensitivity ) );
		messages.add( String.format( " - Min. radius (pixels): %d", minRadius ) );
		messages.add( String.format( " - Max. radius (pixels): %d", maxRadius ) );
		messages.add( String.format( " - Step radius (pixels): %d", stepRadius ) );
		if ( maxNDetections == Integer.MAX_VALUE )
			messages.add( " - Do not limit the number of detections" );
		else
			messages.add( String.format( " - Limit the number of detections to: %d", maxNDetections ) );
		messages.add( String.format( " - Detection method: %s", detectionMethod.toString() ) );
		messages.add( "" );

		final long start = System.currentTimeMillis();
		if ( null == resultsTable || null == WindowManager.getWindow( RESULTS_TABLE_TITLE ) )
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
			if ( null == dataset )
			{
				messages.add( "No image opened. Exiting." );
				messages.update();
				return;
			}

			messages.add( "Processing " + dataset );
			messages.update();

			final List< HoughCircle > circles = processImage( dataset, resultsTable );
			messages.add( String.format( " - Thresholded image retained %.2f%% pixels.", circleSkinner.getPercentPixelsInThresholded() ) );
			messages.add( String.format( " - Found %d circles.", circles.size() ) );
			messages.update();
			if ( null != imp )
			{
				final Overlay overlay = new Overlay();
				imp.setOverlay( overlay );
				final HoughCircleOverlay circleOverlay = new HoughCircleOverlay( imp, sensitivity );
				overlay.add( circleOverlay, "Hough circles" );
				circleOverlay.setCircles( circles );
			}

			break;

		case FOLDER:
			processFolder( folder, resultsTable );
			break;
		}

		final long end = System.currentTimeMillis();

		resultsTable.show( RESULTS_TABLE_TITLE );
		messages.add( "" );
		if ( circleSkinner != null && circleSkinner.isCanceled() )
		{
			messages.add( String.format( "CircleSkinner was canceled after %.1f min.", ( end - start ) / 60000. ) );
			messages.add( String.format( "Reason: %s", circleSkinner.getCancelReason() ) );
		}
		else
		{
			messages.add( String.format( "CircleSkinner completed in %.1f min.", ( end - start ) / 60000. ) );
		}
		messages.update();

		/*
		 * Automatically save results table and log in folder mode.
		 */

		switch ( analysisTarget )
		{
		case CURRENT_IMAGE:
			break;
		case FOLDER:
		{
			final File logFile = new File( folder, "log.txt" );
			try (final PrintWriter writer = new PrintWriter( logFile ))
			{
				for ( final String line : messages )
					writer.println( line );
			}
			catch ( final FileNotFoundException e )
			{
				messages.add( String.format( "\nERROR: Could not save to log file %s. Error is:\n" + e.getMessage(), logFile.getAbsolutePath() ) );
				e.printStackTrace();
			}

			final File resultsFile = new File( folder, "results.csv" );
			resultsTable.save( resultsFile.getAbsolutePath() );

			break;
		}
		default:
			break;

		}
	}

	@SuppressWarnings( "unchecked" )
	private void processFolder( final File sourceFolder, final ResultsTable aResultsTable )
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
			if ( circleSkinner != null && circleSkinner.isCanceled() )
				return;

			if ( !file.exists() || !file.isFile() )
				continue;

			if ( !canOpen( new FileLocation( file ) ) )
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

				final List< HoughCircle > circles = processImage( dataset, aResultsTable );
				messages.add( String.format( " - Thresholded image retained %.2f%% pixels.", circleSkinner.getPercentPixelsInThresholded() ) );
				messages.add( String.format( " - Found %d circles.", circles.size() ) );

				if ( saveSnapshot && null != imp )
				{
					imp.show();
					PngExporter.exportToPng( imp, saveFolder, circles );
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

	private void adjustThreshold( final JSlider sliderThickness, final JSlider sliderThreshold )
	{
		if ( imageDisplayService.getActiveImageDisplay() == null )
			return;

		final AdjustThresholdDialog< T > adjustThresholdDialog = new AdjustThresholdDialog<>(
				imageDisplayService.getActiveImageDisplay(),
				circleThickness, thresholdFactor,
				opService.getContext() );
		adjustThresholdDialog.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				if ( e.getActionCommand().equals( "OK" ) )
				{
					thresholdFactor = adjustThresholdDialog.getThresholdFactor();
					circleThickness = adjustThresholdDialog.getCircleThickness();

					sliderThickness.setValue( circleThickness );
					sliderThreshold.setValue( ( int ) thresholdFactor );
				}
			}
		} );
		adjustThresholdDialog.setLocationRelativeTo( this );
		adjustThresholdDialog.setVisible( true );
	}

	private void adjustSensitivity( final JSlider sliderSensitivity )
	{
		if ( imageDisplayService.getActiveImageDisplay() == null )
			return;

		final AdjustSensitivityDialog< T > adjustSensitivityDialog = new AdjustSensitivityDialog<>(
				imageDisplayService.getActiveImageDisplay(),
				segmentationChannel - 1l,
				circleThickness,
				thresholdFactor,
				sensitivity,
				minRadius,
				maxRadius,
				stepRadius,
				detectionMethod,
				opService.getContext() );
		adjustSensitivityDialog.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				if ( e.getActionCommand().equals( "OK" ) )
				{
					sensitivity = adjustSensitivityDialog.getSensitivity();
					sliderSensitivity.setValue( ( int ) sensitivity );
				}
			}
		} );
		adjustSensitivityDialog.setLocationRelativeTo( this );
		adjustSensitivityDialog.setVisible( true );
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

				if ( canOpen( new FileLocation( file ) ) )
					nImages++;
			}

			infoFiles = String.format( "Found %d candidate %s.", nImages, ( nImages == 1 ? "image" : "images" ) );
		}
		label.setText( infoFiles );
	}

	/*
	 * PRIVATE METHODS
	 */

	@SuppressWarnings( "unchecked" )
	private List< HoughCircle > processImage( final Dataset dataset, final ResultsTable aResultsTable )
	{
		final int maxND = limitDetectionNumber ? maxNDetections : Integer.MAX_VALUE;

		this.circleSkinner = ( CircleSkinnerOp< T > ) Computers.unary( opService, CircleSkinnerOp.class, aResultsTable,
				dataset,
				segmentationChannel - 1l,
				circleThickness,
				thresholdFactor,
				sensitivity,
				minRadius,
				maxRadius,
				stepRadius,
				maxND,
				detectionMethod,
				true,
				false );
		circleSkinner.compute( dataset, resultsTable );
		return circleSkinner.getCircles();
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

	private boolean canOpen( final Location source )
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
		catch ( final UnsupportedOperationException uoe )
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

		final ImageJ ij = new ImageJ();
		ij.launch( args );
		final Object dataset = ij.io().open( "samples/ca-01.lsm" );
//		final Object dataset2 = ij.io().open( "samples/mg-20.lsm" );
		ij.ui().show( dataset );
//		ij.ui().show( dataset2 );
		ij.command().run( CircleSkinnerGUI.class, true );
	}
}
