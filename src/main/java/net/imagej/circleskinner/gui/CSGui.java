package net.imagej.circleskinner.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;

public class CSGui extends JFrame
{
	private JTextField textField;
	public CSGui()
	{
		setTitle( "Circle Skinner" );

		final JPanel panelParameters = new JPanel();
		panelParameters.setAlignmentY( Component.TOP_ALIGNMENT );
		panelParameters.setAlignmentX( Component.LEFT_ALIGNMENT );
		getContentPane().add( panelParameters, BorderLayout.NORTH );
		final GridBagLayout gbl_panelParameters = new GridBagLayout();
		gbl_panelParameters.columnWidths = new int[] { 30, 120, 80, 60, 10 };
		gbl_panelParameters.rowHeights = new int[] { 16, 16, 21, 26, 0 };
		gbl_panelParameters.columnWeights = new double[] { 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE };
		gbl_panelParameters.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		panelParameters.setLayout( gbl_panelParameters );

		final JLabel lblParameters = new JLabel( "Parameters" );
		final GridBagConstraints gbc_lblParameters = new GridBagConstraints();
		gbc_lblParameters.anchor = GridBagConstraints.WEST;
		gbc_lblParameters.insets = new Insets( 10, 10, 5, 0 );
		gbc_lblParameters.gridwidth = 4;
		gbc_lblParameters.gridx = 0;
		gbc_lblParameters.gridy = 0;
		panelParameters.add( lblParameters, gbc_lblParameters );

		final JLabel lblCircleThickness = new JLabel( "Circle thickness" );
		final GridBagConstraints gbc_lblCircleThickness = new GridBagConstraints();
		gbc_lblCircleThickness.anchor = GridBagConstraints.EAST;
		gbc_lblCircleThickness.insets = new Insets( 0, 0, 5, 5 );
		gbc_lblCircleThickness.gridx = 1;
		gbc_lblCircleThickness.gridy = 1;
		panelParameters.add( lblCircleThickness, gbc_lblCircleThickness );

		final JSpinner spinnerThickness = new JSpinner();
		final GridBagConstraints gbc_spinnerThickness = new GridBagConstraints();
		gbc_spinnerThickness.anchor = GridBagConstraints.NORTH;
		gbc_spinnerThickness.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerThickness.insets = new Insets( 0, 0, 5, 5 );
		gbc_spinnerThickness.gridx = 2;
		gbc_spinnerThickness.gridy = 1;
		panelParameters.add( spinnerThickness, gbc_spinnerThickness );

		final JLabel lblPixels = new JLabel( "pixels" );
		final GridBagConstraints gbc_lblPixels = new GridBagConstraints();
		gbc_lblPixels.anchor = GridBagConstraints.NORTH;
		gbc_lblPixels.fill = GridBagConstraints.HORIZONTAL;
		gbc_lblPixels.insets = new Insets( 0, 0, 5, 0 );
		gbc_lblPixels.gridx = 3;
		gbc_lblPixels.gridy = 1;
		panelParameters.add( lblPixels, gbc_lblPixels );

		final JLabel lblThresholdAdjustment = new JLabel( "Threshold adjustment" );
		lblThresholdAdjustment.setEnabled( false );
		final GridBagConstraints gbc_lblThresholdAdjustment = new GridBagConstraints();
		gbc_lblThresholdAdjustment.anchor = GridBagConstraints.EAST;
		gbc_lblThresholdAdjustment.insets = new Insets( 0, 0, 5, 5 );
		gbc_lblThresholdAdjustment.gridx = 1;
		gbc_lblThresholdAdjustment.gridy = 2;
		panelParameters.add( lblThresholdAdjustment, gbc_lblThresholdAdjustment );

		final JSpinner spinnerThreshold = new JSpinner();
		spinnerThreshold.setEnabled( false );
		final GridBagConstraints gbc_spinnerThreshold = new GridBagConstraints();
		gbc_spinnerThreshold.anchor = GridBagConstraints.NORTH;
		gbc_spinnerThreshold.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerThreshold.insets = new Insets( 0, 0, 5, 5 );
		gbc_spinnerThreshold.gridx = 2;
		gbc_spinnerThreshold.gridy = 2;
		panelParameters.add( spinnerThreshold, gbc_spinnerThreshold );

		final JLabel labelPercent = new JLabel( "%" );
		labelPercent.setEnabled( false );
		final GridBagConstraints gbc_labelPercent = new GridBagConstraints();
		gbc_labelPercent.anchor = GridBagConstraints.NORTHWEST;
		gbc_labelPercent.insets = new Insets( 0, 0, 5, 0 );
		gbc_labelPercent.gridx = 3;
		gbc_labelPercent.gridy = 2;
		panelParameters.add( labelPercent, gbc_labelPercent );

		final JLabel lblSensitivity = new JLabel( "Sensitivity" );
		final GridBagConstraints gbc_lblSensitivity = new GridBagConstraints();
		gbc_lblSensitivity.anchor = GridBagConstraints.EAST;
		gbc_lblSensitivity.insets = new Insets( 0, 0, 0, 5 );
		gbc_lblSensitivity.gridx = 1;
		gbc_lblSensitivity.gridy = 3;
		panelParameters.add( lblSensitivity, gbc_lblSensitivity );

		final JSpinner spinnerSensitivity = new JSpinner();
		final GridBagConstraints gbc_spinnerSensitivity = new GridBagConstraints();
		gbc_spinnerSensitivity.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerSensitivity.anchor = GridBagConstraints.NORTH;
		gbc_spinnerSensitivity.insets = new Insets( 0, 0, 0, 5 );
		gbc_spinnerSensitivity.gridx = 2;
		gbc_spinnerSensitivity.gridy = 3;
		panelParameters.add( spinnerSensitivity, gbc_spinnerSensitivity );

		final JPanel panelTarget = new JPanel();
		panelTarget.setAlignmentX( Component.LEFT_ALIGNMENT );
		panelTarget.setAlignmentY( Component.TOP_ALIGNMENT );
		getContentPane().add( panelTarget, BorderLayout.CENTER );
		final GridBagLayout gbl_panelTarget = new GridBagLayout();
		gbl_panelTarget.columnWidths = new int[] { 30, 30, 120, 80, 60 };
		gbl_panelTarget.rowHeights = new int[] { 21, 21, 21, 21, 21 };
		gbl_panelTarget.columnWeights = new double[] { 0.0, 1.0 };
		gbl_panelTarget.rowWeights = new double[] { 0.0, 0.0 };
		panelTarget.setLayout( gbl_panelTarget );

		final JLabel lblTarget = new JLabel( "Target" );
		final GridBagConstraints gbc_lblTarget = new GridBagConstraints();
		gbc_lblTarget.insets = new Insets( 10, 10, 5, 0 );
		gbc_lblTarget.anchor = GridBagConstraints.WEST;
		gbc_lblTarget.gridwidth = 5;
		gbc_lblTarget.gridx = 0;
		gbc_lblTarget.gridy = 0;
		panelTarget.add( lblTarget, gbc_lblTarget );

		final JRadioButton rdbtnCurrentImage = new JRadioButton( "Current image" );
		final GridBagConstraints gbc_rdbtnCurrentImage = new GridBagConstraints();
		gbc_rdbtnCurrentImage.anchor = GridBagConstraints.WEST;
		gbc_rdbtnCurrentImage.gridwidth = 4;
		gbc_rdbtnCurrentImage.insets = new Insets( 0, 0, 5, 0 );
		gbc_rdbtnCurrentImage.gridx = 1;
		gbc_rdbtnCurrentImage.gridy = 1;
		panelTarget.add( rdbtnCurrentImage, gbc_rdbtnCurrentImage );

		final JLabel lblCurrentImageTitle = new JLabel( "Current image title" );
		final GridBagConstraints gbc_lblCurrentImageTitle = new GridBagConstraints();
		gbc_lblCurrentImageTitle.gridwidth = 3;
		gbc_lblCurrentImageTitle.insets = new Insets( 0, 0, 5, 5 );
		gbc_lblCurrentImageTitle.gridx = 1;
		gbc_lblCurrentImageTitle.gridy = 2;
		panelTarget.add( lblCurrentImageTitle, gbc_lblCurrentImageTitle );

		final JRadioButton rdbtnFolder = new JRadioButton( "Folder" );
		final GridBagConstraints gbc_rdbtnFolder = new GridBagConstraints();
		gbc_rdbtnFolder.anchor = GridBagConstraints.WEST;
		gbc_rdbtnFolder.gridwidth = 4;
		gbc_rdbtnFolder.insets = new Insets( 0, 0, 5, 0 );
		gbc_rdbtnFolder.gridx = 1;
		gbc_rdbtnFolder.gridy = 3;
		panelTarget.add( rdbtnFolder, gbc_rdbtnFolder );

		textField = new JTextField();
		final GridBagConstraints gbc_textField = new GridBagConstraints();
		gbc_textField.gridwidth = 3;
		gbc_textField.insets = new Insets( 0, 0, 0, 5 );
		gbc_textField.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField.gridx = 1;
		gbc_textField.gridy = 4;
		panelTarget.add( textField, gbc_textField );
		textField.setColumns( 10 );

		final JButton btnBrowse = new JButton( "Browse" );
		final GridBagConstraints gbc_btnBrowse = new GridBagConstraints();
		gbc_btnBrowse.gridx = 4;
		gbc_btnBrowse.gridy = 4;
		panelTarget.add( btnBrowse, gbc_btnBrowse );

		final JPanel panelButtons = new JPanel();
		getContentPane().add( panelButtons, BorderLayout.SOUTH );
		panelButtons.setLayout( new GridLayout( 0, 4, 0, 0 ) );

		final JButton btnAdjustThreshold = new JButton( "Adjust Threshold" );
		panelButtons.add( btnAdjustThreshold );

		final JLabel label = new JLabel( "" );
		panelButtons.add( label );

		final JLabel label_1 = new JLabel( "" );
		panelButtons.add( label_1 );

		final JLabel label_2 = new JLabel( "" );
		panelButtons.add( label_2 );

		final JButton btnAdjustSensitivity = new JButton( "Adjust Sensitivity" );
		panelButtons.add( btnAdjustSensitivity );

		final JButton btnCancel = new JButton( "Cancel" );
		panelButtons.add( btnCancel );

		final JButton btnStart = new JButton( "Start" );
		panelButtons.add( btnStart );
	}
}
