/*******************************************************************************
 *     SDR Trunk 
 *     Copyright (C) 2014,2015 Dennis Sheirer
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>
 ******************************************************************************/
package spectrum;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.util.Arrays;

import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import settings.ColorSetting;
import settings.ColorSetting.ColorSettingName;
import settings.Setting;
import settings.SettingChangeListener;
import controller.ResourceManager;
import dsp.filter.smoothing.GaussianSmoothingFilter;
import dsp.filter.smoothing.NoSmoothingFilter;
import dsp.filter.smoothing.RectangularSmoothingFilter;
import dsp.filter.smoothing.SmoothingFilter;
import dsp.filter.smoothing.SmoothingFilter.SmoothingType;
import dsp.filter.smoothing.TriangularSmoothingFilter;

public class SpectrumPanel extends JPanel
							implements DFTResultsListener,
									   SettingChangeListener,
									   SpectralDisplayAdjuster
{
	private static final long serialVersionUID = 1L;

	private final static Logger mLog = 
			LoggerFactory.getLogger( SpectrumPanel.class );
	
	private static final RenderingHints RENDERING_HINTS = 
    		new RenderingHints( RenderingHints.KEY_ANTIALIASING, 
    							RenderingHints.VALUE_ANTIALIAS_ON );

	//Color settings
	private static final String SPECTRUM_BACKGROUND = "spectrum_background";
	private static final String SPECTRUM_GRADIENT_TOP = "spectrum_gradient_top";
	private static final String SPECTRUM_GRADIENT_BOTTOM = "spectrum_gradient_bottom";
	private static final String SPECTRUM_LINE = "spectrum_line";
	
	private Color mColorSpectrumBackground;
	private Color mColorSpectrumGradientTop;
	private Color mColorSpectrumGradientBottom;
	private Color mColorSpectrumLine;

	//Defines the panel inset along the bottom for frequency display
	private float mSpectrumInset = 20.0f;

	//Current DFT output bins in dB
	private float[] mDisplayFFTBins = new float[ 1 ];

	//Averaging across multiple DFT result sets
	private int mAveraging = 4;
	
	//Smoothing across bins in the same DFT result set
	private SmoothingFilter mSmoothingFilter = new GaussianSmoothingFilter();

	//Reference dB value set according to the source sample size
	private float mDBScale; 

	private int mZoom = 0;
	private int mZoomBinOffset = 0;;
	
	private ResourceManager mResourceManager;
	
	public SpectrumPanel( ResourceManager resourceManager )
    {
		mResourceManager = resourceManager;
		
		RENDERING_HINTS.put( RenderingHints.KEY_RENDERING, 
							 RenderingHints.VALUE_RENDER_QUALITY );
		
		setSampleSize( 16.0 );
		
		mSmoothingFilter.setPointSize( SmoothingFilter.SMOOTHING_DEFAULT );

		getSettings();

		setAveraging( mAveraging );
    }
	
	public void dispose()
	{
		mResourceManager = null;
	}
	
    /**
     * DFTResultsListener interface for receiving the processed data
     * to display
     */
    public void receive( float[] currentFFTBins )
    {
    	//Prevent arrays of NaN values from being rendered.  The first few
    	//DFT result sets on startup will contain NaN values
    	if( Float.isInfinite( currentFFTBins[ 0 ] ) || Float.isNaN( currentFFTBins[ 0 ] ) )
		{
			currentFFTBins = new float[ currentFFTBins.length ];
		}

    	//Construct and/or resize our DFT results variables
    	if( mDisplayFFTBins == null || 
    		mDisplayFFTBins.length != currentFFTBins.length )
    	{
    		mDisplayFFTBins = currentFFTBins;
    	}

    	//Apply smoothing across the bins of the DFT results
    	float[] smoothedBins = mSmoothingFilter.filter( currentFFTBins );

    	//Apply averaging over multiple DFT output frames
    	if( mAveraging > 1 )
    	{
    		float gain = 1.0f / (float)mAveraging;
    		
    		for( int x = 0; x < mDisplayFFTBins.length; x++ )
    		{
    			mDisplayFFTBins[ x ] += ( smoothedBins[ x ] - mDisplayFFTBins[ x ] ) * gain;
    		}
    	}
    	else
    	{
    		mDisplayFFTBins = smoothedBins;
    	}
    	
		repaint();
    }

    @Override
    public void paintComponent( Graphics g )
    {
    	super.paintComponent( g );
    	
    	Graphics2D graphics = (Graphics2D) g;
    	graphics.setBackground( mColorSpectrumBackground );


        graphics.setRenderingHints( RENDERING_HINTS );
        
    	drawSpectrum( graphics );
    }
    
    /**
     * Draws the current fft spectrum with a line and a gradient fill.
     */
    private void drawSpectrum( Graphics2D graphics )
    {
    	Dimension size = getSize();

    	//Draw the background
    	Rectangle background = new Rectangle( 0, 0, size.width, size.height );
    	graphics.setColor( mColorSpectrumBackground );
    	graphics.draw( background );
    	graphics.fill( background );

    	//Define the gradient
    	GradientPaint gradient = new GradientPaint( 0, 
    												getSize().height / 4, 
    												mColorSpectrumGradientTop,
    												0,
    												getSize().height, 
    												mColorSpectrumGradientBottom );
    	
    	graphics.setBackground( mColorSpectrumBackground );
    	
    	GeneralPath spectrumShape = new GeneralPath();

    	//Start at the lower right inset point
    	spectrumShape.moveTo( size.getWidth(), 
    						  size.getHeight() - mSpectrumInset );
    	
    	//Draw to the lower left
    	spectrumShape.lineTo( 0, size.getHeight() - mSpectrumInset );

    	float[] bins = getBins();
    	
    	//If we have FFT data to display ...
    	if( bins != null )
    	{
    		float insideHeight = size.height - mSpectrumInset;

    		float scalor = insideHeight / -mDBScale;
			
    		float insideWidth = size.width;

    		int binCount = bins.length;

    		float binSize = insideWidth / ( binCount );
    		
    		for( int x = 0; x < binCount; x++ )
    		{
    			float height;
    			
				height = bins[ x ] * scalor;
				
    			if( height > insideHeight )
    			{
    				height = insideHeight;
    			}
    			
    			if( height < 0 )
    			{
    				height = 0;
    			}

        		spectrumShape.lineTo( ( x * binSize ), height );
    		}
    	}
    	//Otherwise show an empty spectrum
    	else
    	{
    		//Draw Left Size
        	graphics.setPaint( gradient );
    		spectrumShape.lineTo( 0, size.getHeight() - mSpectrumInset );
    		//Draw Middle
        	spectrumShape.lineTo( size.getWidth(), 
        						  size.getHeight() - mSpectrumInset );
    	}
    	
    	//Draw Right Side
    	spectrumShape.lineTo( size.getWidth(), 
    						  size.getHeight() - mSpectrumInset );
    	
    	graphics.setPaint( gradient );
    	graphics.draw( spectrumShape );
    	graphics.fill( spectrumShape );
    	
    	graphics.setPaint( mColorSpectrumLine );

    	//Draw the bottom line under the spectrum
    	graphics.draw( new Line2D.Float( 0, 
    					   size.height - mSpectrumInset, 
    					   size.width,
    					   size.height - mSpectrumInset ) );
    }

    /**
     * Sets the current zoom level
     * 
     * 0 	No Zoom
     * 1	2x Zoom
     * 2	4x Zoom
     * 3	8x Zoom
     * 4	16x Zoom
     * 5	32x Zoom
     * 
     * @param zoom level, 0 - 5.
     */
    public void setZoom( int zoom, int offset )
    {
    	assert( 0 <= zoom && zoom <= 5 );
    	
    	mZoom = zoom;
    	
    	setZoomBinOffset( offset );
    }

    /**
     * Sets the offset to define which subset of zoomed bins to display
     * 
     * @param offset
     */
    public void setZoomBinOffset( int offset )
    {
    	mZoomBinOffset = offset;
    }

	/**
	 * Sets the source sample size in bits which defines the lowest dB value to
	 * display in the spectrum
	 * 
	 * @param sampleSize in bits
	 */
	public void setSampleSize( double sampleSize )
	{
		assert( 2.0 <= sampleSize && sampleSize <= 32.0 );

		mDBScale = (float)( 20.0 * Math.log10( Math.pow( 2.0, sampleSize - 1 ) ) );
	}

	/**
	 * Defines the number of DFT result sets to average across
	 */
	public void setAveraging( int size )
	{
		mAveraging = size;
	}

	/**
	 * DFT result sets averaging value
	 */
	public int getAveraging()
	{
		return mAveraging;
	}

	/**
	 * Clears the spectral display
	 */
	public void clearSpectrum()
	{
		mDisplayFFTBins = null;
		
		repaint();
	}
	
	private void getSettings()
	{
		mColorSpectrumBackground = 
				getColor( ColorSettingName.SPECTRUM_BACKGROUND );

		mColorSpectrumGradientBottom = 
				getColor( ColorSettingName.SPECTRUM_GRADIENT_BOTTOM );

		mColorSpectrumGradientTop = 
				getColor( ColorSettingName.SPECTRUM_GRADIENT_TOP );

		mColorSpectrumLine = getColor( ColorSettingName.SPECTRUM_LINE );
	}

	/**
	 * Retrieves the current setting for the named color setting
	 */
	private Color getColor( ColorSettingName name )
	{
		ColorSetting setting = 
				mResourceManager.getSettingsManager().getColorSetting( name );
		
		return setting.getColor();
	}

	/**
	 * Listener interface to receive setting change notifications
	 */
	@Override
    public void settingChanged( Setting setting )
    {
		if( setting instanceof ColorSetting )
		{
			ColorSetting colorSetting = (ColorSetting)setting;
			
			switch( ( (ColorSetting) setting ).getColorSettingName() )
			{
				case SPECTRUM_BACKGROUND:
					mColorSpectrumBackground = colorSetting.getColor();
					break;
				case SPECTRUM_GRADIENT_BOTTOM:
					mColorSpectrumGradientBottom = colorSetting.getColor();
					break;
				case SPECTRUM_GRADIENT_TOP:
					mColorSpectrumGradientTop = colorSetting.getColor();
					break;
				case SPECTRUM_LINE:
					mColorSpectrumLine = colorSetting.getColor();
					break;
				default:
					break;
			}
		}
    }
	
    private float[] getBins()
    {
    	if( mZoom == 0 )
    	{
        	return mDisplayFFTBins;
    	}
    	else
    	{
    		int zoom = (int)Math.pow( 2.0, mZoom );
    		
    		int length = mDisplayFFTBins.length / zoom;
    		
    		int offset = mZoomBinOffset;
    		
    		if( ( offset + length ) > mDisplayFFTBins.length )
    		{
    			offset = mDisplayFFTBins.length - length;
    		}

    		return Arrays.copyOfRange( mDisplayFFTBins, offset, offset + length );
    	}
    }

	@Override
    public void settingDeleted( Setting setting ) {  /* not implemented */ }

	/**
	 * Returns the current inter-bin smoothing setting
	 */
	@Override
	public int getSmoothing()
	{
		return mSmoothingFilter.getPointSize();
	}

	/**
	 * Sets the bin smoothing value to define how wide the smoothing window is
	 * when averaging across DFT bins
	 */
	@Override
	public void setSmoothing( int smoothing )
	{
		mSmoothingFilter.setPointSize( smoothing );
	}

	/**
	 * Returns the current smoothing filter type
	 */
	@Override
	public SmoothingType getSmoothingType()
	{
		return mSmoothingFilter.getSmoothingType();
	}

	/**
	 * Sets the smoothing filter type
	 */
	@Override
	public void setSmoothingType( SmoothingType type )
	{
		if( mSmoothingFilter.getSmoothingType() != type )
		{
			int pointSize = getSmoothing();

			synchronized ( mSmoothingFilter )
			{
				switch( type )
				{
					case GAUSSIAN:
						mSmoothingFilter = new GaussianSmoothingFilter();
						break;
					case RECTANGLE:
						mSmoothingFilter = new RectangularSmoothingFilter();
						break;
					case TRIANGLE:
						mSmoothingFilter = new TriangularSmoothingFilter();
						break;
					case NONE:
					default:
						mSmoothingFilter = new NoSmoothingFilter();
						break;
				}
			}
			
			setSmoothing( pointSize );
		}
	}
}
