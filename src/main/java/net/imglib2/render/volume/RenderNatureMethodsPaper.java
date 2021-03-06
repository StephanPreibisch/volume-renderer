package net.imglib2.render.volume;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.render.volume.Renderer.Interpolation;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class RenderNatureMethodsPaper
{
	public static < T extends Type< T > > void setPlane( final RandomAccessibleInterval< T > img, final int dim, final long pos, final T value )
	{
		final RandomAccessibleInterval< T > interval = Views.hyperSlice( img, dim, pos );
		
		for ( final T t : Views.iterable( interval ) )
			t.set( value );
	}

	public static < T extends Type< T > > void paintOutCube( final RandomAccessibleInterval< T > img, final int dim, final long from, final long to, final T value )
	{
		paintOutCubeIntersect( img, new int[]{ dim }, new long[]{ from }, new long[]{ to }, value );
	}
	
	public static < T extends Type< T > > void paintOutCubeIntersect( final RandomAccessibleInterval< T > img, final int[] dim, final long[] from, final long[] to, final T value )
	{
		final Cursor< T > cursor = Views.iterable( img ).localizingCursor();
		
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			
			boolean inside = true;
			
			for ( int e = 0; e < dim.length && inside; ++e )
			{
				final int d = dim[ e ];
				final int pos = cursor.getIntPosition( d );
				
				if ( pos < from[ e ] || pos >= to[ e ] )
					inside = false;
			}
			
			if ( inside )
				cursor.get().set( value );
		}
	}
	
	public static String makeFileName( final int sizeX, final int sizeY, final int sliceXY, final int sliceYZ )
	{
		return "_" + sizeX + "_" + sizeY + "_" + sliceXY + "_" + sliceYZ;
	}
	
	public static ImagePlus renderCornerSlices( final ImagePlus imp3d, final AffineTransform3D t, final Interpolation interpolation, 
			final int sizeX, final int sizeY, final int sliceXY, final int sliceYZ )
	{
		final File file = new File( "corner_slice" + makeFileName( sizeX, sizeY, sliceXY, sliceYZ ) + ".tif" );
		
		if ( file.exists() )
			return new ImagePlus( file.getAbsolutePath() );

		final ImagePlus imp = imp3d.duplicate();
		final Img< FloatType > img = ImageJFunctions.wrapFloat( imp );
		
		// corner
		paintOutCube( img, 0, 0, sliceXY, new FloatType( 0f ) );
		paintOutCube( img, 2, sliceYZ + 1, img.dimension( 2 ), new FloatType( 0f ) );
		
		// corner slice (needs corner)
		paintOutCubeIntersect( img, new int[]{ 0, 2 }, new long[]{ sliceXY + 1, 0 }, new long[]{ img.dimension( 0 ), sliceYZ }, new FloatType( 0f ) );

		final ImagePlus omp = Renderer.runGray(
				imp,
				sizeX,
				sizeY,
				t,
				0,
				1,
				new Translation3D(),
				1,
				0,
				interpolation,
				0,
				1,
				1.0,
				0.0,
				true );
		
		new FileSaver( omp ).saveAsTiff( file.getAbsolutePath() );
		
		return omp;
	}
	
	public static int drawTextBottomRight( final ByteProcessor bp, final int border, final String text, final Font font )
	{
		final Rectangle2D r = font.getStringBounds( text, ((Graphics2D)bp.getBufferedImage().getGraphics()).getFontRenderContext() );
		bp.setFont( font ); 
		bp.setColor( Color.white );
		bp.setAntialiasedText( true );
		bp.drawString( text, bp.getWidth() - border - (int)Math.round( r.getWidth() ), bp.getHeight() - border + (int)Math.round( r.getHeight() + r.getY() ));
		
		return (int)Math.round( r.getWidth() );
	}

	public static ImagePlus renderPlanes( final ImagePlus imp3d, final AffineTransform3D t, final Interpolation interpolation, 
			final int sizeX, final int sizeY, final int sliceXY, final int sliceYZ )
	{
		final int borderXY = 37;
		final int borderYZ = 40;
		final Font font = new Font( "Arial", Font.PLAIN, 24 );
		final String xy = "XY";
		final String yz = "YZ";
		
		final File file = new File( "plane" + makeFileName( sizeX, sizeY, sliceXY, sliceYZ ) + ".tif" );
		
		if ( file.exists() )
			return new ImagePlus( file.getAbsolutePath() );

		final ImagePlus imp = imp3d.duplicate();
		final Img< FloatType > img = ImageJFunctions.wrapFloat( imp );

		// clear the image
		for ( final FloatType f : img )
			f.set( 0 );

		// draw the text
		final ByteProcessor bpXY = new ByteProcessor( imp3d.getWidth(), imp3d.getHeight() );
		final ByteProcessor bpYZ = new ByteProcessor( imp3d.getWidth(), imp3d.getHeight() );

		drawTextBottomRight( bpXY, borderXY, xy, font );
		final int widthTextYZ = drawTextBottomRight( bpYZ, borderYZ, yz, font );
		
		// add the xy slice
		final RandomAccess< FloatType > rXY = Views.hyperSlice( img, 2, sliceXY ).randomAccess();
		final Cursor< UnsignedByteType > cXY  = ArrayImgs.unsignedBytes( (byte[])bpXY.getPixels(), imp3d.getWidth(), imp3d.getHeight() ).localizingCursor();
		
		while ( cXY.hasNext() )
		{
			cXY.fwd();
			rXY.setPosition( cXY );
			rXY.get().set( cXY.get().get() / 255.0f );
		}

		// add the yz slice
		final RandomAccess< FloatType > rYZ = Views.extendZero( Views.hyperSlice( img, 0, sliceYZ ) ).randomAccess();
		final Cursor< UnsignedByteType > cYZ  = ArrayImgs.unsignedBytes( (byte[])bpYZ.getPixels(), imp3d.getWidth(), imp3d.getHeight() ).localizingCursor();
		
		while ( cYZ.hasNext() )
		{
			cYZ.fwd();
			rYZ.setPosition( cYZ.getIntPosition( 1 ), 0 );
			rYZ.setPosition( cYZ.getIntPosition( 0 ) - imp.getHeight() + 2*borderYZ + widthTextYZ, 1 );
			rYZ.get().set( cYZ.get().get() / 255.0f );
		}
		
		final ImagePlus omp = Renderer.runGray(
				imp,
				sizeX,
				sizeY,
				t,
				0,
				1,
				new Translation3D(),
				1,
				0,
				interpolation,
				0,
				0.5,
				1.0,
				0.0,
				true );
		
		new FileSaver( omp ).saveAsTiff( file.getAbsolutePath() );
		
		return omp;
	}

	public static ImagePlus renderOutline( final ImagePlus imp3d, final AffineTransform3D t, final Interpolation interpolation, 
			final int sizeX, final int sizeY, final int sliceXY, final int sliceYZ, final int verticalLine, final boolean showAxesLabels ) throws IncompatibleTypeException
	{
		final ImagePlus cornerSlices = renderCornerSlices( imp3d, t, interpolation, sizeX, sizeY, sliceXY, sliceYZ );	
		final Img< UnsignedByteType > input = ImageJFunctions.wrapByte( cornerSlices );
		
		// make binary (0=bg, 1=fg)
		final int imgBg = 15;
		final int bg = 0;
		final int fg = 1;
		for ( final UnsignedByteType b : input )
			if ( b.get() > imgBg )
				b.set( fg );
			else
				b.set( bg );
		
		// flood-fill background (real outside = 2)
		final int outside = 2;
		FloodFiller f = new FloodFiller( cornerSlices.getProcessor() );
		cornerSlices.getProcessor().setValue( outside );
		f.fill( 0, 0 );
		
		// find and draw outline
		final float[] outlinePx = new float[ cornerSlices.getWidth() * cornerSlices.getHeight() ];
		final Img< FloatType > outline = ArrayImgs.floats( outlinePx, new long[]{ cornerSlices.getWidth(), cornerSlices.getHeight() } );//input.factory().imgFactory( new FloatType() ).create( input, new FloatType() );
		final Cursor< UnsignedByteType > cursor = input.localizingCursor();
		final RandomAccess< UnsignedByteType > randomAccess = Views.extendValue( input, new UnsignedByteType(2) ).randomAccess();
		final RandomAccess< FloatType > outlineR = outline.randomAccess();
		
		while ( cursor.hasNext() )
		{
			if ( cursor.next().get() == outside )
			{
				randomAccess.setPosition( cursor );
				outlineR.setPosition( cursor );
				
				for ( int d = 0; d < input.numDimensions(); ++d )
				{
					randomAccess.fwd( d );
					
					if ( randomAccess.get().get() == fg )
						outlineR.get().set( fg );
					
					randomAccess.bck( d );
					randomAccess.bck( d );
					
					if ( randomAccess.get().get() == fg )
						outlineR.get().set( fg );
					
					randomAccess.fwd( d );
				}
			}
		}
		
		// draw the vertical line and get a location inside left and right of the line
		boolean inside = false;
		int posR = -1;
		int posL = -1;
		
		outlineR.setPosition( verticalLine, 0 );
		for ( int y = 0; y < outline.dimension( 1 ); ++y )
		{
			outlineR.setPosition( y, 1 );
						
			if ( outlineR.get().get() == fg )
				inside = !inside;
			
			if ( inside )
			{
				if ( posL == -1 )
				{
					outlineR.bck( 0 );
					if ( outlineR.get().get() == bg )
						posL = outlineR.getIntPosition( 1 );
					outlineR.fwd( 0 );
				}
				
				if ( posR == -1 )
				{
					outlineR.fwd( 0 );
					if ( outlineR.get().get() == bg )
						posR = outlineR.getIntPosition( 1 );
					outlineR.bck( 0 );					
				}
				outlineR.get().set( fg );
			}
		}
		
		// flood fill the interors
		final FloatProcessor fp = new FloatProcessor( cornerSlices.getWidth(), cornerSlices.getHeight(), outlinePx );
		f = new FloodFiller( fp );
		fp.setValue( (float)fg / 15.0f );
		f.fill( verticalLine - 1, posL );
		f.fill( verticalLine + 1, posR );
		
		// smooth the outline a little
		Gauss3.gauss( 0.6, Views.extendZero( outline ), outline );

		// the result
		final ByteProcessor bpOutline = (ByteProcessor)fp.convertToByte( true );

		// get the labels for the slices and max them into the image
		if ( showAxesLabels )
		{
			final ImagePlus frame = renderPlanes( imp3d, t, interpolation, sizeX, sizeY, sliceXY, sliceYZ );
			
			final Cursor< UnsignedByteType > c1 = ArrayImgs.unsignedBytes( (byte[])bpOutline.getPixels(), sizeX, sizeY ).cursor();
			final Cursor< UnsignedByteType > c2 = ArrayImgs.unsignedBytes( (byte[])frame.getProcessor().getPixels(), sizeX, sizeY ).cursor();
			
			while ( c1.hasNext() )
			{
				c1.fwd(); c2.fwd();
				c1.get().set( Math.max( Math.min( 255, Math.round( c1.get().get() * 1.25f ) ), Math.round( c2.get().get() / 1.1f ) ) );
			}
		}
		
		// display
		return new ImagePlus( "outline", bpOutline );
	}
	
	public static int computeVerticalLine( final ImagePlus imp3d, final AffineTransform3D t, 
			final int sizeX, final int sizeY, final int sliceXY, final int sliceYZ ) throws NumberFormatException, IOException
	{
		final File file = new File( "vertical_line" + makeFileName( sizeX, sizeY, sliceXY, sliceYZ ) + ".txt" );
		
		if ( file.exists() )
		{
			BufferedReader inputFile = new BufferedReader(new FileReader(file));
			int verticalPosition = Integer.parseInt( inputFile.readLine() );
			inputFile.close();
			return verticalPosition;
		}

		final ImagePlus imp = imp3d.duplicate();
		final Img< FloatType > img = ImageJFunctions.wrapFloat( imp );
		
		// just center line
		paintOutCube( img, 0, 0, sliceXY, new FloatType( 0f ) );
		paintOutCube( img, 0, sliceXY + 1, img.dimension( 0 ), new FloatType( 0f ) );
		paintOutCube( img, 2, 0, sliceYZ, new FloatType( 0f ) );
		paintOutCube( img, 2, sliceYZ + 1, img.dimension( 2 ), new FloatType( 0f ) );
		
		// corner slice (needs corner)
		paintOutCubeIntersect( img, new int[]{ 0, 2 }, new long[]{ sliceXY + 1, 0 }, new long[]{ img.dimension( 0 ), sliceYZ }, new FloatType( 0f ) );
		
		final ImagePlus omp = Renderer.runGray(
				imp,
				sizeX,
				sizeY,
				t,
				0,
				1,
				new Translation3D(),
				1,
				0,
				Interpolation.NL,
				0,
				1,
				1.0,
				0.0,
				false );

		// compute average position
		final Img< UnsignedByteType > omg = ImageJFunctions.wrapByte( omp );
		final Cursor< UnsignedByteType > c = omg.localizingCursor();
		
		long sumX = 0;
		long count = 0;
		
		while ( c.hasNext() )
		{
			c.fwd();
			
			if ( c.get().get() > 0 )
			{
				sumX += c.getLongPosition( 0 );
				++count;
			}
		}
		
		final int verticalPosition = (int)(sumX/count);

		PrintWriter outputFile = new PrintWriter(new FileWriter(file));
		outputFile.println( verticalPosition );
		outputFile.close();
		
		return verticalPosition;
	}
	
	public static ImagePlus renderInverseCorner( final ImagePlus imp3d, final AffineTransform3D t, final Interpolation interpolation,
			final int sizeX, final int sizeY, final int sliceXY, final int sliceYZ, final String fileName )
	{
		final File file = new File( "inversecorner_" + fileName + makeFileName( sizeX, sizeY, sliceXY, sliceYZ ) + ".tif" );
		
		if ( file.exists() )
			return new ImagePlus( file.getAbsolutePath() );

		final ImagePlus imp = imp3d.duplicate();
		final Img< FloatType > img = ImageJFunctions.wrapFloat( imp );
		
		// inverse corner
		paintOutCubeIntersect( img, new int[]{ 0, 2 }, new long[]{ sliceXY + 1, 0 }, new long[]{ img.dimension( 0 ), sliceYZ }, new FloatType( 0f ) );
		
		final ImagePlus omp = Renderer.runGray(
				imp,
				sizeX,
				sizeY,
				t,
				0,
				1,
				new Translation3D(),
				1,
				0,
				interpolation,
				0,
				1,
				1.0,
				0.0,
				true );
		
		new FileSaver( omp ).saveAsTiff( file.getAbsolutePath() );

		return omp;
	}
	
	public static ImagePlus combineOutlineImage( final ImagePlus image, final ImagePlus outline, final ARGBType overlayColor )
	{
		final long[] dim = new long[]{ image.getWidth(), image.getHeight() };
		
		final int[] px = new int[ image.getWidth() * image.getHeight() ];
		final Img< ARGBType > out = ArrayImgs.argbs( px, dim );
		
		final Img< UnsignedByteType > img = ArrayImgs.unsignedBytes( (byte[])image.getProcessor().getPixels(), dim );
		final Img< UnsignedByteType > line = ArrayImgs.unsignedBytes( (byte[])outline.getProcessor().getPixels(), dim );
		
		final Cursor< UnsignedByteType > c1 = img.cursor();
		final Cursor< UnsignedByteType > c2 = line.cursor();
		
		final float or = ARGBType.red( overlayColor.get() ) / 255.0f;
		final float og = ARGBType.green( overlayColor.get() ) / 255.0f;
		final float ob = ARGBType.blue( overlayColor.get() ) / 255.0f;
		
		for ( final ARGBType t : out )
		{
			final int i = Math.min( 255, Math.round( c1.next().get() * 1.076f ) );
			final int wi = c2.next().get();
			final float w = (float)wi/(float)255;

			final int r = Math.round( Math.max( i - i*w*og - i*w*ob,  wi*or ) );
			final int g = Math.round( Math.max( i - i*w*or - i*w*ob,  wi*og ) );
			final int b = Math.round( Math.max( i - i*w*or - i*w*og,  wi*ob ) );
						
			t.set( ARGBType.rgba( r, g, b, 0 ) );
		}
		
		return new ImagePlus( "combined", new ColorProcessor( image.getWidth(), image.getHeight(), px ) );
	}
	
	final static public void main( final String[] args ) throws Exception
	{
		String fileName;
		
		if ( args != null && args.length > 0 )
			fileName = args[ 0 ];
		else
			fileName = "/Users/preibischs/Desktop/analysis/50-7/groundtruth.tif";
		
		final File f = new File( fileName );
		
		if ( !f.exists() )
			throw new RuntimeException( "File '" + f.getAbsolutePath() + "' does not exist." );
		
		new ImageJ();
				
		final AffineTransform3D t = new AffineTransform3D();
		
		t.rotate( 1, Math.toRadians( 38 ) );
		t.rotate( 0, Math.toRadians( 15 ) );
		t.scale( 0.7 );
		
		final ImagePlus imp = new ImagePlus( fileName );

		final int sizeX = 800;
		final int sizeY = 600;
		
		final int sliceXY = 145;
		final int sliceYZ = 145;
		
		final int verticalLine = computeVerticalLine( imp, t, sizeX, sizeY, sliceXY, sliceYZ );
		final ARGBType overlayColor = new ARGBType( ARGBType.rgba( 255, 35, 15, 0 ) );
		final Interpolation interpolation = Interpolation.LC;
		final boolean showAxesLabels = false;
		
		// start computing
		ImagePlus image = renderInverseCorner( imp, t, interpolation, sizeX, sizeY, sliceXY, sliceYZ, f.getName() );
		image.show();

		ImagePlus outline = renderOutline( imp, t, interpolation, sizeX, sizeY, sliceXY, sliceYZ, verticalLine, showAxesLabels );
		outline.show();
				
		ImagePlus result = combineOutlineImage( image, outline, overlayColor );
		result.show();
		
		//final Img< FloatType > img = ImageJFunctions.wrapFloat( imp );

		//setPlane( img, 0, sliceXY, new FloatType( .75f ) );
		//setPlane( img, 2, sliceYZ, new FloatType( .75f ) );
		
		// inverse corner
		//paintOutCubeIntersect( img, new int[]{ 0, 2 }, new long[]{ sliceXY, 0 }, new long[]{ img.dimension( 0 ), sliceYZ }, new FloatType( 0f ) );
		
		// xy cut
		// paintOutCube( img, 0, sliceXY + 1, img.dimension( 0 ), new FloatType( 0f ) );

		// xy slice
		//paintOutCube( img, 0, 0, sliceXY, new FloatType( 0f ) );
		//paintOutCube( img, 0, sliceXY + 1, img.dimension( 0 ), new FloatType( 0f ) );

		// yz cut
		//paintOutCube( img, 2, 0, sliceYZ, new FloatType( 0f ) );

		// yz slice
		//paintOutCube( img, 2, 0, sliceYZ, new FloatType( 0f ) );
		//paintOutCube( img, 2, sliceYZ + 1, img.dimension( 0 ), new FloatType( 0f ) );
	}
}
