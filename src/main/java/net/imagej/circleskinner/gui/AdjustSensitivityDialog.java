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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
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
import java.util.concurrent.ExecutionException;

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

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;
import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.measure.ResultsTable;
import net.imagej.Dataset;
import net.imagej.DefaultDataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.circleskinner.CircleSkinnerOp;
import net.imagej.circleskinner.CircleSkinnerOp.DetectionMethod;
import net.imagej.circleskinner.hough.HoughCircle;
import net.imagej.circleskinner.util.DisplayUpdater;
import net.imagej.circleskinner.util.EverythingDisablerAndReenabler;
import net.imagej.circleskinner.util.HoughCircleOverlay;
import net.imagej.display.ImageDisplay;
import net.imagej.legacy.LegacyService;
import net.imagej.ops.OpService;
import net.imagej.ops.special.computer.Computers;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

public class AdjustSensitivityDialog< T extends RealType< T > & NativeType< T > > extends JDialog
{

	private static final long serialVersionUID = 1L;

	/*
	 * SERVICES.
	 */

	@Parameter
	private UIService uiService;

	@Parameter
	private LegacyService legacyService;

	@Parameter
	private OpService opService;

	@Parameter
	private LogService log;

	/*
	 * FIELDS
	 */

	/**
	 * Will take the current display regardless of the input.
	 */
	private ImageDisplay source;

	/**
	 * The segmentation channel index, 1-based (first is 0).
	 */
	private final long segmentationChannel;

	/**
	 * The circle thickness (crown thickness), in pixel units.
	 */
	private final int circleThickness;

	private final double thresholdFactor;

	private final HashSet< ActionListener > listeners = new HashSet<>();

	private JLabel lblInfo;

	private double sensitivity;

	private final int minRadius;

	private final int maxRadius;

	private final int stepRadius;

	private final DetectionMethod detectionMethod;

	private final Context context;

	private final DisplayUpdater overlayUpdater = new DisplayUpdater()
	{

		@Override
		public void refresh()
		{
			refreshOverlay();
		}
	};

	private HoughCircleOverlay circleOverlay;

	private ImagePlus newImp;

	private JPanel panelAdjustments;

	private IntervalMarker intervalMarker;

	private double[] sensitivities = new double[] { CircleSkinnerGUI.MIN_SENSITIVITY };

	private Img< DoubleType > voteImg;

	/*
	 * CONSTRUCTOR.
	 */

	public AdjustSensitivityDialog( final ImageDisplay source,
			final long segmentationChannel,
			final int circleThickness,
			final double thresholdFactor,
			final double sensitivity,
			final int minRadius,
			final int maxRadius,
			final int stepRadius,
			final DetectionMethod detectionMethod,
			final Context context )
	{
		this.source = source;
		this.segmentationChannel = segmentationChannel;
		this.circleThickness = circleThickness;
		this.thresholdFactor = thresholdFactor;
		this.sensitivity = sensitivity;
		this.minRadius = minRadius;
		this.maxRadius = maxRadius;
		this.stepRadius = stepRadius;
		this.detectionMethod = detectionMethod;
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
		newImp.changes = false;
		newImp.close();
		overlayUpdater.quit();
		voteImg = null;

		for ( final ActionListener listener : listeners )
			listener.actionPerformed( new ActionEvent( this, 1, "cancel" ) );

		dispose();
	}

	private void acceptAdjustment()
	{
		newImp.changes = false;
		newImp.close();
		overlayUpdater.quit();
		voteImg = null;

		for ( final ActionListener listener : listeners )
			listener.actionPerformed( new ActionEvent( this, 0, "OK" ) );

		dispose();
	}

	private void initialize()
	{
		setTitle( CircleSkinnerGUI.PLUGIN_NAME + " Adjust sensitivity" );

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

		this.panelAdjustments = new JPanel();
		getContentPane().add( panelAdjustments, BorderLayout.CENTER );
		final GridBagLayout gbl_panelAdjustments = new GridBagLayout();
		gbl_panelAdjustments.columnWidths = new int[] { 0, 168, 0, 0 };
		gbl_panelAdjustments.rowHeights = new int[] { 0, 152, 0, 0, 0 };
		gbl_panelAdjustments.columnWeights = new double[] { 1.0, 1.0, 1.0, Double.MIN_VALUE };
		gbl_panelAdjustments.rowWeights = new double[] { 0.0, 1.0, 0.0, 0.0, Double.MIN_VALUE };
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
		sliderSensitivity.setMaximum( CircleSkinnerGUI.MAX_SENSITIVITY );
		sliderSensitivity.setMinimum( CircleSkinnerGUI.MIN_SENSITIVITY );
		sliderSensitivity.setMinorTickSpacing( 25 );
		sliderSensitivity.setMajorTickSpacing( 100 );
		sliderSensitivity.setPaintTicks( true );
		sliderSensitivity.setValue( ( int ) sensitivity );
		final GridBagConstraints gbc_sliderSensitivity = new GridBagConstraints();
		gbc_sliderSensitivity.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderSensitivity.insets = new Insets( 0, 0, 5, 5 );
		gbc_sliderSensitivity.gridx = 1;
		gbc_sliderSensitivity.gridy = 0;
		panelAdjustments.add( sliderSensitivity, gbc_sliderSensitivity );

		final JSpinner spinnerSensitivity = new JSpinner( new SpinnerNumberModel( ( int ) sensitivity,
				CircleSkinnerGUI.MIN_SENSITIVITY, CircleSkinnerGUI.MAX_SENSITIVITY, 10 ) );
		final GridBagConstraints gbc_spinnerSensitivity = new GridBagConstraints();
		gbc_spinnerSensitivity.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerSensitivity.insets = new Insets( 0, 0, 5, 0 );
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
				overlayUpdater.doUpdate();
			}
		} );

		final JLabel lblHistogram = new JLabel( "Histogram" );
		final GridBagConstraints gbc_lblHistogram = new GridBagConstraints();
		gbc_lblHistogram.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblHistogram.gridx = 0;
		gbc_lblHistogram.gridy = 1;
		panelAdjustments.add( lblHistogram, gbc_lblHistogram );

		final JLabel lblShowVoteImage = new JLabel( "Show vote image." );
		final GridBagConstraints gbc_lblShowVoteImage = new GridBagConstraints();
		gbc_lblShowVoteImage.anchor = GridBagConstraints.EAST;
		gbc_lblShowVoteImage.insets = new Insets( 0, 0, 5, 5 );
		gbc_lblShowVoteImage.gridx = 1;
		gbc_lblShowVoteImage.gridy = 2;
		panelAdjustments.add( lblShowVoteImage, gbc_lblShowVoteImage );

		final JButton btnShow = new JButton( "Show" );
		btnShow.addActionListener( ( e ) -> new Thread( () -> uiService.show( voteImg ) ).start() );
		final GridBagConstraints gbc_btnShow = new GridBagConstraints();
		gbc_btnShow.insets = new Insets( 0, 0, 5, 0 );
		gbc_btnShow.gridx = 2;
		gbc_btnShow.gridy = 2;
		panelAdjustments.add( btnShow, gbc_btnShow );

		lblInfo = new JLabel( " " );
		final GridBagConstraints gbc_lblInfoPixels = new GridBagConstraints();
		gbc_lblInfoPixels.anchor = GridBagConstraints.SOUTHEAST;
		gbc_lblInfoPixels.gridwidth = 3;
		gbc_lblInfoPixels.gridx = 0;
		gbc_lblInfoPixels.gridy = 3;
		panelAdjustments.add( lblInfo, gbc_lblInfoPixels );

		lblInfo.setText( "Please wait while detecting circles..." );
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
				try
				{
					lblInfo.setText( "Detection done." );
					reenabler.reenable();
					get();
				}
				catch ( final ExecutionException e )
				{
					e.getCause().printStackTrace();
					final String msg = String.format( "Unexpected problem: %s",
							e.getCause().toString() );
					log.error( msg );
				}
				catch ( final InterruptedException e )
				{
					e.printStackTrace();
				}

			}
		}.execute();
	}

	private void computeCircles()
	{
		final ImagePlus imp = legacyService.getImageMap().lookupImagePlus( source );
		this.newImp = new ImagePlus( "Preview - " + imp.getShortTitle(),
				imp.getProcessor().duplicate() );
		final Img< T > slice = ImageJFunctions.wrap( newImp );

		final ResultsTable table = CircleSkinnerOp.createResulsTable();

		final Dataset dataset = new DefaultDataset( context, new ImgPlus<>( slice ) );
		final boolean keepVoteImg = true;
		@SuppressWarnings( "unchecked" )
		final CircleSkinnerOp< T > circleSkinner = ( CircleSkinnerOp< T > ) Computers.unary( opService, CircleSkinnerOp.class, ResultsTable.class,
				dataset,
				segmentationChannel,
				circleThickness,
				thresholdFactor,
				( double ) CircleSkinnerGUI.MAX_SENSITIVITY,
				minRadius,
				maxRadius,
				stepRadius,
				Integer.MAX_VALUE,
				detectionMethod,
				false,
				keepVoteImg );
		circleSkinner.compute( dataset, table );

		final List< HoughCircle > circles = circleSkinner.getCircles();
		this.voteImg = new ImgPlus<>( circleSkinner.getVoteImg(), "Vote image",
				new AxisType[] { Axes.X, Axes.Y, Axes.Z } );

		/*
		 * Collect sensitivity values.
		 */

		if ( null == circles || circles.isEmpty() )
		{
			this.sensitivities = new double[] { CircleSkinnerGUI.MAX_SENSITIVITY };
		}
		else
		{
			this.sensitivities = new double[ circles.size() ];
			for ( int i = 0; i < sensitivities.length; i++ )
				sensitivities[ i ] = circles.get( i ).getSensitivity();
		}

		createHistogram( sensitivities );
		pack();

		/*
		 * Show circle overlay.
		 */

		newImp.show();
		Overlay overlay = newImp.getOverlay();
		if ( null == overlay )
		{
			overlay = new Overlay();
			newImp.setOverlay( overlay );
		}
		this.circleOverlay = new HoughCircleOverlay( newImp, CircleSkinnerGUI.MAX_SENSITIVITY );
		overlay.add( circleOverlay, "Hough circles" );
		circleOverlay.setCircles( circles );
		circleOverlay.setSensitivity( sensitivity );

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

	private ChartPanel createHistogram( final double[] aSensitivities )
	{
		final HistogramDataset dataset = new HistogramDataset();
		dataset.setType( HistogramType.RELATIVE_FREQUENCY );
		dataset.addSeries( "Sensitivities", aSensitivities, 50 );
		final String plotTitle = null; // "Sensitivity histogram";
		final String xaxis = "Sensitivity";
		final String yaxis = "#";
		final PlotOrientation orientation = PlotOrientation.VERTICAL;
		final boolean show = false;
		final boolean toolTips = false;
		final boolean urls = false;
		final JFreeChart chart = ChartFactory.createHistogram( plotTitle, xaxis, yaxis,
				dataset, orientation, show, toolTips, urls );
		chart.setBackgroundPaint( this.getBackground() );

		final XYPlot plot = chart.getXYPlot();
		final XYBarRenderer renderer = ( XYBarRenderer ) plot.getRenderer();
		renderer.setShadowVisible( false );
		renderer.setMargin( 0 );
		renderer.setBarPainter( new StandardXYBarPainter() );
		renderer.setDrawBarOutline( true );
		renderer.setSeriesOutlinePaint( 0, Color.BLACK );
		renderer.setSeriesPaint( 0, new Color( 1, 1, 1, 0 ) );

		plot.setBackgroundPaint( new Color( 1, 1, 1, 0 ) );
		plot.setOutlineVisible( false );
		plot.setDomainCrosshairVisible( false );
		plot.setDomainGridlinesVisible( false );
		plot.setRangeCrosshairVisible( false );
		plot.setRangeGridlinesVisible( false );

		plot.getRangeAxis().setVisible( false );
		plot.getDomainAxis().setVisible( true );
		plot.getDomainAxis().setRange( CircleSkinnerGUI.MIN_SENSITIVITY, CircleSkinnerGUI.MAX_SENSITIVITY );

		chart.setBorderVisible( false );
		chart.setBackgroundPaint( new Color( 0.6f, 0.6f, 0.7f ) );

		intervalMarker = new IntervalMarker( CircleSkinnerGUI.MIN_SENSITIVITY, sensitivity,
				new Color( 0.3f, 0.5f, 0.8f ), new BasicStroke(), new Color( 0, 0, 0.5f ), new BasicStroke( 1.5f ), 0.5f );
		plot.addDomainMarker( intervalMarker );

		final ChartPanel panel = new ChartPanel( chart );
		panel.setPreferredSize( new Dimension( 0, 0 ) );

		final GridBagConstraints gbc_histoPanel = new GridBagConstraints();
		gbc_histoPanel.gridwidth = 1;
		gbc_histoPanel.insets = new Insets( 5, 5, 5, 5 );
		gbc_histoPanel.fill = GridBagConstraints.BOTH;
		gbc_histoPanel.gridx = 1;
		gbc_histoPanel.gridy = 1;
		panelAdjustments.add( panel, gbc_histoPanel );

		return panel;
	}

	private void refreshOverlay()
	{
		circleOverlay.setSensitivity( sensitivity );
		newImp.updateAndDraw();
		intervalMarker.setEndValue( sensitivity );

		int nCircles = 0;
		for ( final double s : sensitivities )
		{
			if ( s < sensitivity )
				nCircles++;
		}
		lblInfo.setText( String.format( "Retain %d %s out of %d.",
				nCircles, nCircles == 1 ? "circle" : "circles", sensitivities.length ) );
	}

	public double getSensitivity()
	{
		return sensitivity;
	}

}
