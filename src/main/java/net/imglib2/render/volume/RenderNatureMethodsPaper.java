package net.imglib2.render.volume;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

import ij.ImageJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss.Gauss;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.labeling.AllConnectedComponents;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.labeling.NativeImgLabeling;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.render.volume.Renderer.Interpolation;
import net.imglib2.type.Type;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.IntType;
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
		final int n = img.numDimensions();
		
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
	
	public static ImagePlus renderCornerSlices( final ImagePlus imp3d, final AffineTransform3D t, 
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
				Interpolation.LC,
				0,
				1,
				1.0,
				0.0,
				true );
		
		new FileSaver( omp ).saveAsTiff( file.getAbsolutePath() );
		
		return omp;
	}
	
	public static ImagePlus renderOutline( final ImagePlus imp3d, final AffineTransform3D t, 
			final int sizeX, final int sizeY, final int sliceXY, final int sliceYZ, final int verticalLine ) throws IncompatibleTypeException
	{
		final ImagePlus imp = renderCornerSlices( imp3d, t, sizeX, sizeY, sliceXY, sliceYZ );	
		final Img< UnsignedByteType > input = ImageJFunctions.wrapByte( imp );
		
		// make binary (0=bg, 1=fg)
		final int imgBg = 5;
		final int bg = 0;
		final int fg = 1;
		for ( final UnsignedByteType b : input )
			if ( b.get() > imgBg )
				b.set( fg );
			else
				b.set( bg );
		
		// flood-fill background (real outside = 2)
		final int outside = 2;
		FloodFiller f = new FloodFiller( imp.getProcessor() );
		imp.getProcessor().setValue( outside );
		f.fill( 0, 0 );
		
		// find and draw outline
		final float[] outlinePx = new float[ imp.getWidth() * imp.getHeight() ];
		final Img< FloatType > outline = ArrayImgs.floats( outlinePx, new long[]{ imp.getWidth(), imp.getHeight() } );//input.factory().imgFactory( new FloatType() ).create( input, new FloatType() );
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
		final FloatProcessor fp = new FloatProcessor( imp.getWidth(), imp.getHeight(), outlinePx );
		f = new FloodFiller( fp );
		fp.setValue( (float)fg / 30.0f );
		f.fill( verticalLine - 1, posL );
		f.fill( verticalLine + 1, posR );
		
		// smooth the outline a little
		Gauss3.gauss( 0.6, Views.extendZero( outline ), outline );
		
		// display
		return new ImagePlus( "outline", fp.convertToByte( true ) );
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
				Interpolation.NN,
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
	
	public static ImagePlus renderInverseCorner( final ImagePlus imp3d, final AffineTransform3D t, 
			final int sizeX, final int sizeY, final int sliceXY, final int sliceYZ )
	{
		final File file = new File( "inversecorner" + makeFileName( sizeX, sizeY, sliceXY, sliceYZ ) + ".tif" );
		
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
				Interpolation.LC,
				0,
				1,
				1.0,
				0.0,
				true );
		
		new FileSaver( omp ).saveAsTiff( file.getAbsolutePath() );

		return omp;
	}
	
	public static ImagePlus combineOutlineImage( final ImagePlus image, final ImagePlus outline )
	{
		final long[] dim = new long[]{ image.getWidth(), image.getHeight() };
		
		final int[] px = new int[ image.getWidth() * image.getHeight() ];
		final Img< ARGBType > out = ArrayImgs.argbs( px, dim );
		
		final Img< UnsignedByteType > img = ArrayImgs.unsignedBytes( (byte[])image.getProcessor().getPixels(), dim );
		final Img< UnsignedByteType > line = ArrayImgs.unsignedBytes( (byte[])outline.getProcessor().getPixels(), dim );
		
		final Cursor< UnsignedByteType > c1 = img.cursor();
		final Cursor< UnsignedByteType > c2 = line.cursor();
		
		for ( final ARGBType t : out )
		{
			int i = c1.next().get();
			float w = (float)c2.next().get()/(float)255;
			
			int r = Math.round( Math.max( i, w*255 ) );
			int g = Math.round( i - i*w );
			int b = Math.round( i - i*w );
			
			t.set( ARGBType.rgba( r, g, b, 0 ) );
			
			//int r = Math.max(0, i-w);
			//t.set( ARGBType.rgba( Math.max( r, w ), r, r, 0 ) );
		}
		
		return new ImagePlus( "combined", new ColorProcessor( image.getWidth(), image.getHeight(), px ) );
	}
	
	final static public void main( final String[] args ) throws Exception
	{
		new ImageJ();
				
		final AffineTransform3D t = new AffineTransform3D();
		
		t.rotate( 1, Math.toRadians( 38 ) );
		t.rotate( 0, Math.toRadians( 15 ) );
		t.scale( 0.7 );
		
		final ImagePlus imp = new ImagePlus( "/Users/preibischs/Desktop/analysis/50-7/groundtruth.tif" );
		
		final int sizeX = 400;
		final int sizeY = 300;
		
		final int sliceXY = 145;
		final int sliceYZ = 145;
		
		final int verticalLine = computeVerticalLine( imp, t, sizeX, sizeY, sliceXY, sliceYZ );
		
		ImagePlus outline = renderOutline( imp, t, sizeX, sizeY, sliceXY, sliceYZ, verticalLine );
		outline.show();
		
		ImagePlus image = renderInverseCorner( imp, t, sizeX, sizeY, sliceXY, sliceYZ );
		image.show();
		
		ImagePlus result = combineOutlineImage( image, outline );
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
