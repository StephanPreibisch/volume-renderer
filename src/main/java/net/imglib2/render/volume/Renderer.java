/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package net.imglib2.render.volume;

import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.interpolation.randomaccess.LanczosInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.Perspective3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.numeric.ARGBDoubleType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.AbstractARGBDoubleType;
import net.imglib2.type.numeric.NativeARGBDoubleType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeView;
import net.imglib2.view.composite.RealComposite;

/**
 * 
 *
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>
 */
public class Renderer
{
	public enum Interpolation { NN, NL, LC };
	public enum Anaglyph { RedCyan, RedGreen, GreenMagenta };
	
	static protected < T extends NumericType< ? > > void render(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< T > target,
			final long minZ,
			final long maxZ,
			final long stepSize,
			final double bg,
			final RowAccumulator< T > accumulator )
	{
		final RandomAccess< T > pixel = target.randomAccess( target );
		final RandomAccess< T > poxel = source.randomAccess();
		
		pixel.setPosition( target.min( 0 ), 0 );
		pixel.setPosition( target.min( 1 ), 0 );

		poxel.setPosition( pixel.getLongPosition( 0 ), 0 );
		poxel.setPosition( pixel.getLongPosition( 1 ), 1 );
		poxel.setPosition( maxZ, 2 );
		
		while ( pixel.getLongPosition( 1 ) <= target.max( 1 ) )
		{
			pixel.setPosition( target.min( 0 ), 0 );
			poxel.setPosition( pixel.getLongPosition( 0 ), 0 );
			while ( pixel.getLongPosition( 0 ) <= target.max( 0 ) )
			{
				poxel.setPosition( maxZ, 2 );
				accumulator.accumulateRow( pixel.get(), poxel, minZ, maxZ, stepSize, 2 );
				
				pixel.fwd( 0 );
				poxel.fwd( 0 );
			}
			
			pixel.fwd( 1 );
			poxel.fwd( 1 );
		}
	}
	
	
	static protected < T extends AbstractARGBDoubleType< T > > void renderARGBDouble(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< ARGBType > target,
			final long minZ,
			final long maxZ,
			final long stepSize,
			final T bg,
			final RowAccumulator< T > accumulator )
	{
		final RandomAccess< ARGBType > pixel = target.randomAccess( target );
		final RandomAccess< T > poxel = source.randomAccess();
		final T accumulate = source.randomAccess().get().createVariable();
		
		pixel.setPosition( target.min( 0 ), 0 );
		pixel.setPosition( target.min( 1 ), 0 );

		poxel.setPosition( pixel.getLongPosition( 0 ), 0 );
		poxel.setPosition( pixel.getLongPosition( 1 ), 1 );
		poxel.setPosition( maxZ, 2 );
		
		while ( pixel.getLongPosition( 1 ) <= target.max( 1 ) )
		{
			pixel.setPosition( target.min( 0 ), 0 );
			poxel.setPosition( pixel.getLongPosition( 0 ), 0 );
			while ( pixel.getLongPosition( 0 ) <= target.max( 0 ) )
			{
				poxel.setPosition( maxZ, 2 );
				accumulate.set( bg.getA(), bg.getR(), bg.getG(), bg.getB() );
				accumulator.accumulateRow( accumulate, poxel, minZ, maxZ, stepSize, 2 );
				pixel.get().set( accumulate.toARGBInt() );
				
				pixel.fwd( 0 );
				poxel.fwd( 0 );
			}
			
			pixel.fwd( 1 );
			poxel.fwd( 1 );
		}
	}
	
	
	final static public double accelerate( final double x )
	{
		return 0.5 - 0.5 * Math.cos( Math.PI * x );
	}
	
	final static public double accelerate2( final double x )
	{
		return Math.sin( 2 * Math.PI * accelerate( x ) );
	}
	
	/**
	 * 
	 * @param affine
	 * @param animation a value between 0 and 1 that specifies the camera position along a predefined path
	 */
	final static public void appendCamera1(
			final AffineTransform3D affine,
			final double animation )
	{
		final AffineTransform3D rotation = new AffineTransform3D();
		rotation.rotate( 0, animation * Math.PI * 2 );
		
		affine.preConcatenate( rotation );
	}
	
	
	/**
	 * 
	 * @param affine
	 * @param animation a value between 0 and 1 that specifies the camera position along a predefined path
	 */
	final static public void appendCamera2(
			final AffineTransform3D affine,
			final double animation )
	{
		final AffineTransform3D rotation = new AffineTransform3D();
		rotation.rotate( 1, animation * Math.PI * 2 );
		
		affine.preConcatenate( rotation );
	}
	
	
	/**
	 * 
	 * @param affine
	 * @param animation a value between 0 and 1 that specifies the camera position along a predefined path
	 */
	final static public void appendCamera3(
			final AffineTransform3D affine,
			final double animation )
	{
		final AffineTransform3D rotation = new AffineTransform3D();
		rotation.rotate( 2, animation * Math.PI * 2 );
		
		affine.preConcatenate( rotation );
	}
	
	
	/**
	 * 
	 * @param affine
	 * @param animation a value between 0 and 1 that specifies the camera position along a predefined path
	 */
	final static public void appendCamera4(
			final AffineTransform3D affine,
			final double animation )
	{
		final double l = accelerate( animation );
		
		final AffineTransform3D rotation = new AffineTransform3D();
		rotation.rotate( 0, -l * Math.PI * 2 * 2 );
		rotation.rotate( 1, animation * Math.PI * 2 );
		
		affine.preConcatenate( rotation );
	}
	
	/**
	 * 
	 * @param affine
	 * @param animation a value between 0 and 1 that specifies the camera position along a predefined path
	 */
	final static public void appendCamera5(
			final AffineTransform3D affine,
			final double animation )
	{
		final double l1 = accelerate( animation );
		final double l2 = accelerate2( animation );
		
		final AffineTransform3D rotation = new AffineTransform3D();
		rotation.rotate( 0, -l1 * Math.PI * 2 * 2 );
		rotation.rotate( 1, l2 * Math.PI / 4 );
		
		affine.preConcatenate( rotation );
	}
	
	
	final static protected Img< FloatType > floatCopyImagePlus( final ImagePlus imp )
	{
		final Img< FloatType > img;
		final int nPixels = imp.getWidth() * imp.getHeight();
		final long nVoxels =
				( long )nPixels *
				( long )imp.getStack().getSize(); 
		if ( nVoxels > Integer.MAX_VALUE )
			img = new CellImgFactory< FloatType >( 256 ).create(
					new long[]{
							imp.getWidth(),
							imp.getHeight(),
							imp.getNSlices() },
					new FloatType() );
		else
			img = new ArrayImgFactory< FloatType >().create(
					new long[]{
							imp.getWidth(),
							imp.getHeight(),
							imp.getNSlices() },
					new FloatType() );
		
		for ( int z = 0; z < imp.getNSlices(); ++z )
		{
			final ImageProcessor ip = imp.getStack().getProcessor( z + 1 );
			final RandomAccessibleInterval< FloatType > slice = Views.hyperSlice( img, 2, z );
			
			final Cursor< FloatType > cursor = Views.flatIterable( slice ).cursor(); 
			for ( int i = 0; i < nPixels; ++i )
				cursor.next().set( ip.getf( i ) );
		}
		return img;
	}
	
	
	final static protected Img< FloatType > floatCopyCompositeImage( final ImagePlus imp )
	{
		final Img< FloatType > img;
		final int nPixels = imp.getWidth() * imp.getHeight();
		final long nVoxels =
				( long )nPixels *
				( long )imp.getStack().getSize(); 
		if ( nVoxels > Integer.MAX_VALUE )
			img = new CellImgFactory< FloatType >( 256 ).create(
					new long[]{
							imp.getWidth(),
							imp.getHeight(),
							imp.getNChannels(),
							imp.getNSlices() },
					new FloatType() );
		else
			img = new ArrayImgFactory< FloatType >().create(
					new long[]{
							imp.getWidth(),
							imp.getHeight(),
							imp.getNChannels(),
							imp.getNSlices() },
					new FloatType() );
		
		for ( int c = 0; c < imp.getNChannels(); ++c )
		{
			final RandomAccessibleInterval< FloatType > channel = Views.hyperSlice( img, 2, c );
			for ( int z = 0; z < imp.getNSlices(); ++z )
			{
				final ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex( c + 1, z + 1, 1 ) );
				final RandomAccessibleInterval< FloatType > slice = Views.hyperSlice( channel, 2, z );
				
				final Cursor< FloatType > cursor = Views.flatIterable( slice ).cursor(); 
				for ( int i = 0; i < nPixels; ++i )
					cursor.next().set( ip.getf( i ) );
			}
		}
		return img;
	}
	
	
	final static Img< NativeARGBDoubleType > convert(
			final RandomAccessible< RealComposite< FloatType > > composite,
			final Interval box,
			final RealCompositeARGBDoubleConverter< FloatType > composite2ARGBDouble )
	{
		final RandomAccessible< ARGBDoubleType > argbComposite = Converters.convert(
				composite,
				composite2ARGBDouble,
				new ARGBDoubleType() );
		
		/* copy it as on-the-fly conversion isn't the quickest thing in the world */
		final Img< NativeARGBDoubleType > argbCopy;
		if ( box.dimension( 0 ) * box.dimension( 1 ) * box.dimension( 2 ) * 4 > Integer.MAX_VALUE )
			argbCopy = new CellImgFactory< NativeARGBDoubleType >( 256 ).create( box, new NativeARGBDoubleType() );
		else
			argbCopy = new ArrayImgFactory< NativeARGBDoubleType >().create( box, new NativeARGBDoubleType() );
		
		final IterableInterval< ARGBDoubleType > sourceIterable = Views.flatIterable( Views.interval( argbComposite, box ) );
		final IterableInterval< NativeARGBDoubleType > targetIterable = Views.flatIterable( Views.interval( argbCopy, box ) );
		final Cursor< ARGBDoubleType > sourceCursor = sourceIterable.cursor();
		final Cursor< NativeARGBDoubleType > targetCursor = targetIterable.cursor();
		while ( targetCursor.hasNext() )
			targetCursor.next().set( sourceCursor.next() );
		
		return argbCopy;
	}
	
	
	final static protected AffineTransform3D buildAffineTransform(
			final AffineGet orientation,
			final long width,
			final long height,
			final long depth,
			final double distance )
	{
		/* affine */
		final AffineTransform3D affine = new AffineTransform3D();
		affine.set(
				1, 0, 0, -width / 2.0,
				0, 1, 0, -height / 2.0,
				0, 0, 1, -depth / 2.0 );
		
		affine.preConcatenate( orientation );
		
		affine.preConcatenate( new Translation3D( 0, 0, distance * width ) );
		
		return affine;
	}
	
	final static protected void appendCamera(
			final InvertibleRealTransformSequence transformSequence,
			final long width,
			final long height,
			final long depth,
			final double f,
			final Translation3D offset )
	{
		final Translation3D centerUnshiftXY = new Translation3D( width / 2.0, height / 2.0, 0 );
		centerUnshiftXY.preConcatenate( offset );
		
		/* camera */
		final Perspective3D perspective = Perspective3D.getInstance();
		final Scale scale = new Scale( f * width, f * width, 1 );
		
		/* add all to sequence */
		transformSequence.add( perspective );
		transformSequence.add( scale );
		transformSequence.add( centerUnshiftXY );
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	final static protected < T extends NumericType< T > > RandomAccessible< T > buildTransformedSource(
			final RandomAccessible< T > source,
			final InvertibleRealTransform transform,
			final Interpolation interpolationMethod )
	{
		final RealRandomAccessible< T > interpolant;
		switch ( interpolationMethod )
		{
			case LC:
			{
				if ( source.randomAccess().get() instanceof RealType )
					interpolant = Views.interpolate( source, new LanczosInterpolatorFactory() );
				else
					throw new RuntimeException( "LanczosInterpolation only possible for RealTypes." );
				break;
			}
			case NL:
				interpolant = Views.interpolate( source, new NLinearInterpolatorFactory< T >() );
				break;
			default:
				interpolant = Views.interpolate( source, new NearestNeighborInterpolatorFactory< T >() );
		}
		
//		ImageJFunctions.show( Views.interval( Views.raster( RealViews.transform( interpolant, transform ) ), new long[]{ 0, 0, 390 / 2 }, new long[]{ 928, 390, 390 + 390 / 2} ) );
		return RealViews.transform( interpolant, transform );
	}
	
	final static protected < T extends NumericType< T > > RandomAccessible< T > buildTransformedSource(
			final RandomAccessibleInterval< T > source,
			final InvertibleRealTransform transform,
			final Interpolation interpolationMethod )
	{
		final ExtendedRandomAccessibleInterval< T, RandomAccessibleInterval< T > > extendedImg = Views.extendValue( source, source.randomAccess().get().createVariable() );
		return buildTransformedSource( extendedImg, transform, interpolationMethod );
	}
	
	
	final static public void mixRedGreenAnaglyph(
			final IterableInterval< ARGBType > red,
			final IterableInterval< ARGBType > green,
			final double scale )
	{
		final Cursor< ARGBType > cRed = red.cursor();
		final Cursor< ARGBType > cGreen = green.cursor();
		final double f03 = 0.3 * scale;
		final double f06 = 0.6 * scale;
		final double f01 = 0.1 * scale;
		while ( cRed.hasNext() )
		{
			final ARGBType argbRed = cRed.next();
			final ARGBType argbGreen = cGreen.next();
			
			final int argbr = argbRed.get();
			final int argbg = argbGreen.get();
			
			final int rr = ( argbr >> 16 ) & 0xff;
			final int gr = ( argbr >> 8 ) & 0xff;
			final int br = argbr & 0xff;
			
			final int rg = ( argbg >> 16 ) & 0xff;
			final int gg = ( argbg >> 8 ) & 0xff;
			final int bg = argbg & 0xff;
			
			final int r = Math.max( 0, Math.min( 255, ( int )Math.round( f03 * rr + f06 * gr + f01 * br ) ) );
			final int g = Math.max( 0, Math.min( 255, ( int )Math.round( f03 * rg + f06 * gg + f01 + bg ) ) );
			
			argbRed.set( ( ( ( r << 8 ) | g ) << 8 ) | 0xff000000 );
		}
	}
	
	
	final static public void mixRedCyanAnaglyph(
			final IterableInterval< ARGBType > red,
			final IterableInterval< ARGBType > cyan,
			final double scale )
	{
		final Cursor< ARGBType > cRed = red.cursor();
		final Cursor< ARGBType > cCyan = cyan.cursor();
		final double f05 = 0.5 * scale;
		final double f025 = 0.25 * scale;
		final double f075 = 0.75 * scale;
		while ( cRed.hasNext() )
		{
			final ARGBType argbRed = cRed.next();
			final ARGBType argbCyan = cCyan.next();
			
			final int argbr = argbRed.get();
			final int argbc = argbCyan.get();
			
			final int rr = ( argbr >> 16 ) & 0xff;
			final int gr = ( argbr >> 8 ) & 0xff;
			final int br = argbr & 0xff;
			
			final int rc = ( argbc >> 16 ) & 0xff;
			final int gc = ( argbc >> 8 ) & 0xff;
			final int bc = argbc & 0xff;
			
			final int r = Math.max( 0, Math.min( 255, ( int )Math.round( f05 * rr + f025 * gr + f025 * br ) ) );
			final int g = Math.max( 0, Math.min( 255, ( int )Math.round( f025 * rc + f075 * gc ) ) );
			final int b = Math.max( 0, Math.min( 255, ( int )Math.round( f025 * rc + f075 * bc ) ) );
			
			argbRed.set( ( ( ( r << 8 ) | g ) << 8 ) | b | 0xff000000 );
		}
	}
	
	
	final static public void mixGreenMagentaAnaglyph(
			final IterableInterval< ARGBType > green,
			final IterableInterval< ARGBType > magenta,
			final double scale )
	{
		final Cursor< ARGBType > cGreen = green.cursor();
		final Cursor< ARGBType > cMagenta = magenta.cursor();
		final double f05 = 0.5 * scale;
		final double f025 = 0.25 * scale;
		final double f075 = 0.75 * scale;
		while ( cGreen.hasNext() )
		{
			final ARGBType argbGreen = cGreen.next();
			final ARGBType argbMagenta = cMagenta.next();
			
			final int argbg = argbGreen.get();
			final int argbm = argbMagenta.get();
			
			final int rg = ( argbg >> 16 ) & 0xff;
			final int gg = ( argbg >> 8 ) & 0xff;
			final int bg = argbg & 0xff;
			
			final int rm = ( argbm >> 16 ) & 0xff;
			final int gm = ( argbm >> 8 ) & 0xff;
			final int bm = argbm & 0xff;
			
			final int r = Math.max( 0, Math.min( 255, ( int )Math.round( f075 * rm + f025 * gm ) ) );
			final int g = Math.max( 0, Math.min( 255, ( int )Math.round( f025 * rg + f05 * gg + f025 * bg ) ) );
			final int b = Math.max( 0, Math.min( 255, ( int )Math.round( f025 * gm + f075 * bm ) ) );
			
			argbGreen.set( ( ( ( r << 8 ) | g ) << 8 ) | b | 0xff000000 );
		}
	}
	
	
	/**
	 * Create an AlphaIntensity rendering of a 3D stack.  No composites or
	 * time series supported.
	 *  
	 * @param impSource 3d image, will be converted to singel channle float even if it is ARGB-color
	 * @param width width of the target canvas
	 * @param height height of the target canvas
	 * @param min minimum intensity
	 * @param max maximum intensity
	 * @param orientation initial transformation assuming that the 3d volume is centered (e.g. export of Interactive Stack Rotation)
	 * @param f focal length in multiples of width
	 * @param offset from camera center (useful to distance-normalize stereo-projections)
	 * @param stepSize z-stepping for the volume renderer higher is faster but less beautiful
	 * @param bg background intensity
	 * @param interpolationMethod 0 NN, 1 NL
	 * @param alphaScale scale factor for linear intensity to alpha transfer 
	 * @param alphaOffset offset for linear intensity to alpha transfer
	 * @param antiArtifactRendering Render two images with a slight z-offset to reduce artifacts on flat surfaces
	 * 
	 * @return
	 */
	final static public ImagePlus runGray(
			final ImagePlus impSource,
			//final RandomAccessibleInterval< UnsignedByteType > byteCanvas,
			final int width,
			final int height,
			final AffineTransform3D orientation,
			final double distance,
			final double f,
			final Translation3D offset,
			final long stepSize,
			final double bg,
			final Interpolation interpolationMethod,
			final double min,
			final double max,
			final double alphaScale,
			final double alphaOffset, 
			final boolean antiArtifactRendering )
	{
		/* copy contents into most appropriate container */
		final Img< FloatType > img = floatCopyImagePlus( impSource );
		//final ImagePlusImg< FloatType, ? > img = ImagePlusImgs.from( impSource );
		
//		final int width = ( int )byteCanvas.dimension( 0 );
//		final int height = ( int )byteCanvas.dimension( 1 );
		
		System.out.println(
				img.dimension( 0 ) + " " + 
				img.dimension( 1 ) + " " +
				img.dimension( 2 ) );
		
		final double[] offsets;
		
		if ( antiArtifactRendering )
			offsets = new double[]{ -0.25, +0.25 };
		else
			offsets = new double[]{ 0.0 };
		
		/* build target */
		final float[][] floatPixels = new float[ offsets.length ][ width * height ];

		for ( int o = 0; o < offsets.length; ++o )
		{		
			/* build transformation */
			final AffineTransform3D affine = buildAffineTransform(
					orientation,
					img.dimension( 0 ),
					img.dimension( 1 ),
					img.dimension( 2 ),
					f );
			
			final InvertibleRealTransformSequence transformSequence = new InvertibleRealTransformSequence();
			
			transformSequence.add( affine );
			
			appendCamera(
					transformSequence,
					width,
					height,
					img.dimension( 2 ),
					f,
					offset );
			
			if ( offsets[ o ] != 0.0 )
				transformSequence.add( new Translation3D( 0, 0, offsets[ o ] ) );
			
			/* build source */
			final RandomAccessible< FloatType > rotated = buildTransformedSource( img, transformSequence, interpolationMethod );
			
			/* accumulator */
			final AlphaIntensityLayers< FloatType > accumulator = new AlphaIntensityLayers< FloatType >( alphaScale, alphaOffset );
			
			/* calculate boundaries */
			final FinalRealInterval bounds = affine.estimateBounds( img );
			final long minZ	= ( long )Math.floor( bounds.realMin( 2 ) );
			final long maxZ	= ( long )Math.ceil( bounds.realMax( 2 ) );
			
			final ArrayImg< FloatType, FloatArray > floatCanvas = ArrayImgs.floats( floatPixels[ o ], width, height );
			
			/* render */
			render( rotated, floatCanvas, minZ, maxZ, stepSize, bg, accumulator );
		}
		
		
		if ( offsets.length > 1 )
		{
			for ( int o = 1; o < offsets.length; ++o )
				for ( int i = 0; i < floatPixels[ 0 ].length; ++i )
					floatPixels[ 0 ][ i ] += floatPixels[ o ][ i ];
			
			for ( int i = 0; i < floatPixels[ 0 ].length; ++i )
				floatPixels[ 0 ][ i ] /= (float)offsets.length;
		}
		
		final FloatProcessor fp = new FloatProcessor( width, height, floatPixels[ 0 ] );
//		new ImagePlus( impSource.getTitle(), fp ).show();
		fp.setMinAndMax( min, max );
		final ByteProcessor bp = ( ByteProcessor )fp.convertToByte( true );
		
		return new ImagePlus( impSource.getTitle(), bp );
	}
	
	
	/**
	 * Create an ARGB rendering of a 3D composite stack.  No time series
	 * supported.
	 *  
	 * @param impSource 3d image, will be converted to multi-channel float
	 * @param width width of the target canvas
	 * @param height height of the target canvas
	 * @param orientation initial transformation assuming that the 3d volume is centered (e.g. export of Interactive Stack Rotation)
	 * @param distance between camera and origin in multiples of width
	 * @param f focal length in multiples of width
	 * @param offset from camera center (useful to distance-normalize stereo-projections)
	 * @param stepSize z-stepping for the volume renderer higher is faster but less beautiful
	 * @param bg background color
	 * @param interpolationMethod 0 NN, 1 NL
	 * @param alphaScale scale factor for linear intensity to alpha transfer 
	 * @param alphaOffset offset for linear intensity to alpha transfer
	 * 
	 * @return
	 */
	final static public < T extends AbstractARGBDoubleType< T > > void runARGB(
			final ImagePlus impSource,
			final ArrayImg< ARGBType, IntArray > argbCanvas,
			final AffineTransform3D orientation,
			final double distance,
			final double f,
			final Translation3D offset,
			final long stepSize,
			final T bg,
			final Interpolation interpolationMethod,
			final RealCompositeARGBDoubleConverter< FloatType > composite2ARGBDouble )
	{
		/* copy contents into most appropriate container */
		final Img< FloatType > img = floatCopyCompositeImage( impSource );
		
		final int width = ( int )argbCanvas.dimension( 0 );
		final int height = ( int )argbCanvas.dimension( 1 );
		
		/* collapse composite dimension */
		final RandomAccessibleInterval< FloatType > xyzc = Views.permute( img, 2, 3 );
		final CompositeView< FloatType, RealComposite< FloatType > > composite =
				Views.collapseReal( Views.extendZero( xyzc ), ( int )xyzc.dimension( 3 ) );
		
		final FinalInterval box = new FinalInterval(
				xyzc.dimension( 0 ),
				xyzc.dimension( 1 ),
				xyzc.dimension( 2 ) );
		
		System.out.println(
				box.dimension( 0 ) + " " + 
				box.dimension( 1 ) + " " +
				box.dimension( 2 ) );
		
		/* convert */
		final Img< NativeARGBDoubleType > argbCopy = convert(
				composite,
				box,
				composite2ARGBDouble );
		
		/* build transformation */
		final AffineTransform3D affine = buildAffineTransform(
				orientation,
				xyzc.dimension( 0 ),
				xyzc.dimension( 1 ),
				xyzc.dimension( 2 ),
				distance );
		
		final InvertibleRealTransformSequence transformSequence = new InvertibleRealTransformSequence();
		
		transformSequence.add( affine );
		
		appendCamera(
				transformSequence,
				width,
				height,
				xyzc.dimension( 2 ),
				f,
				offset );
		
		/* build source */
		final RandomAccessible< NativeARGBDoubleType > rotated = buildTransformedSource( argbCopy, transformSequence, interpolationMethod );
		
		/* calculate boundaries */
		final FinalRealInterval bounds = affine.estimateBounds( box );
		final long minZ	= ( long )Math.floor( bounds.realMin( 2 ) );
		final long maxZ	= ( long )Math.ceil( bounds.realMax( 2 ) );
		
		/* accumulator */
		final ARGBDoubleLayers< NativeARGBDoubleType > accumulator = new ARGBDoubleLayers< NativeARGBDoubleType >();
		
		final NativeARGBDoubleType nativeBg = new NativeARGBDoubleType();
		nativeBg.set( bg.getA(), bg.getR(), bg.getG(), bg.getB() );
		
		/* render */
		renderARGBDouble( rotated, argbCanvas, minZ, maxZ, stepSize, nativeBg, accumulator );
	}
	
	
	/**
	 * Create an ARGB rendering of a 3D composite stack.  No time series
	 * supported.
	 *  
	 * @param impSource 3d image, will be converted to multi-channel float
	 * @param width width of the target canvas
	 * @param height height of the target canvas
	 * @param orientation initial transformation assuming that the 3d volume is centered (e.g. export of Interactive Stack Rotation)
	 * @param distance between camera and origin in multiples of width
	 * @param f focal length in multiples of width
	 * @param stepSize z-stepping for the volume renderer higher is faster but less beautiful
	 * @param bg background color
	 * @param interpolationMethod 0 NN, 1 NL
	 * @param alphaScale scale factor for linear intensity to alpha transfer 
	 * @param alphaOffset offset for linear intensity to alpha transfer
	 * 
	 * @return
	 */
	final static public < T extends AbstractARGBDoubleType< T > > ImagePlus runARGB(
			final ImagePlus impSource,
			final int width,
			final int height,
			final AffineTransform3D orientation,
			final double distance,
			final double f,
			final Translation3D offset,
			final long stepSize,
			final T bg,
			final Interpolation interpolationMethod,
			final RealCompositeARGBDoubleConverter< FloatType > composite2ARGBDouble )
	{
		/* build target */
		final int[] argbPixels = new int[ width * height ];
		final ArrayImg< ARGBType, IntArray > argbCanvas = ArrayImgs.argbs( argbPixels, width, height );
		
		/* render */
		runARGB( impSource, argbCanvas, orientation, distance, f, offset, stepSize, bg, interpolationMethod, composite2ARGBDouble );
		
		/* wrap as ImagePlus */
		final ColorProcessor cp = new ColorProcessor( width, height, argbPixels );
		return new ImagePlus( impSource.getTitle(), cp );
	}
	
	
	/**
	 * Create a stereo ARGB rendering of a 3D composite stack.  No time series
	 * supported.
	 *  
	 * @param impSource 3d image, will be converted to singel channle float even if it is ARGB-color
	 * @param width width of the target canvas
	 * @param height height of the target canvas
	 * @param orientation initial transformation assuming that the 3d volume is centered (e.g. export of Interactive Stack Rotation)
	 * @param distance between camera and origin in multiples of width
	 * @param f focal length in multiples of width
	 * @param stereoBase 1/2 distance of the stereo cameras
	 * @param offset from camera center
	 * @param stepSize z-stepping for the volume renderer higher is faster but less beautiful
	 * @param bg background color
	 * @param interpolationMethod 0 NN, 1 NL
	 * @param alphaScale scale factor for linear intensity to alpha transfer 
	 * @param alphaOffset offset for linear intensity to alpha transfer
	 * 
	 * @return
	 */
	final static public < T extends AbstractARGBDoubleType< T > > void runARGBStereo(
			final ImagePlus impSource,
			final ArrayImg< ARGBType, IntArray > argbCanvasLeft,
			final ArrayImg< ARGBType, IntArray > argbCanvasRight,
			final AffineTransform3D orientation,
			final double distance,
			final double f,
			final double stereoBase,
			final Translation3D offset,
			final long stepSize,
			final T bg,
			final Interpolation interpolationMethod,
			final RealCompositeARGBDoubleConverter< FloatType > composite2ARGBDouble,
			final double intensityScale,
			final Anaglyph anaglyph ) throws InterruptedException
	{
		final AffineTransform3D affineLeft = new AffineTransform3D();
		affineLeft.set(
				1, 0, 0, stereoBase,
				0, 1, 0, 0,
				0, 0, 1, 0 );
		affineLeft.concatenate( orientation );
		
		final AffineTransform3D affineRight = orientation.copy();
		affineRight.set(
				1, 0, 0, -stereoBase,
				0, 1, 0, 0,
				0, 0, 1, 0 );
		affineRight.concatenate( orientation );
		
		final Thread tLeft = new Thread(
				new Runnable(){
					@Override
					final public void run()
					{
						runARGB(
								impSource,
								argbCanvasLeft,
								affineLeft,
								distance,
								f,
								offset.inverse(),
								stepSize,
								bg,
								interpolationMethod,
								composite2ARGBDouble );
					}
				} );
		
		final Thread tRight = new Thread(
				new Runnable(){
					@Override
					final public void run()
					{
						runARGB(
								impSource,
								argbCanvasRight,
								affineRight,
								distance,
								f,
								offset,
								stepSize,
								bg,
								interpolationMethod,
								composite2ARGBDouble );
					}
				} );
		tLeft.start();
		tRight.start();
		tLeft.join();
		tRight.join();
		
		switch ( anaglyph )
		{
		case RedGreen:
			mixRedGreenAnaglyph( argbCanvasLeft, argbCanvasRight, intensityScale );
			break;
		case RedCyan:
			mixRedCyanAnaglyph( argbCanvasLeft, argbCanvasRight, intensityScale );
			break;
		case GreenMagenta:
			mixGreenMagentaAnaglyph( argbCanvasLeft, argbCanvasRight, intensityScale );
		}
	}
	
	/**
	 * Create a stereo ARGB rendering of a 3D composite stack.  No time series
	 * supported.
	 *  
	 * @param impSource 3d image, will be converted to singel channle float even if it is ARGB-color
	 * @param width width of the target canvas
	 * @param height height of the target canvas
	 * @param orientation initial transformation assuming that the 3d volume is centered (e.g. export of Interactive Stack Rotation)
	 * @param distance between camera and origin in multiples of width
	 * @param f focal length in multiples of width
	 * @param stereoBase 1/2 distance of the stereo cameras
	 * @param offset from camera center (useful to distance-normalize stereo-projections)
	 * @param stepSize z-stepping for the volume renderer higher is faster but less beautiful
	 * @param bg background color
	 * @param interpolationMethod 0 NN, 1 NL
	 * @param alphaScale scale factor for linear intensity to alpha transfer 
	 * @param alphaOffset offset for linear intensity to alpha transfer
	 * 
	 * @return
	 */
	final static public < T extends AbstractARGBDoubleType< T > > ImagePlus runARGBStereo(
			final ImagePlus impSource,
			final int width,
			final int height,
			final AffineTransform3D orientation,
			final double distance,
			final double f,
			final double stereoBase,
			final Translation3D offset,
			final long stepSize,
			final T bg,
			final Interpolation interpolationMethod,
			final RealCompositeARGBDoubleConverter< FloatType > composite2ARGBDouble,
			final double intensityScale,
			final Anaglyph anaglyph ) throws InterruptedException
	{
		/* build targets */
		final int[] argbPixelsLeft = new int[ width * height ];
		final ArrayImg< ARGBType, IntArray > argbCanvasLeft = ArrayImgs.argbs( argbPixelsLeft, width, height );
		final int[] argbPixelsRight = new int[ width * height ];
		final ArrayImg< ARGBType, IntArray > argbCanvasRight = ArrayImgs.argbs( argbPixelsRight, width, height );
		
		/* wrap as ImagePlus */
		final ColorProcessor cp = new ColorProcessor( width, height, argbPixelsLeft );
		final ImagePlus omp = new ImagePlus( impSource.getTitle() + " anaglyph", cp );
		
		/* render */
		runARGBStereo(
				impSource,
				argbCanvasLeft,
				argbCanvasRight,
				orientation,
				distance,
				f,
				stereoBase,
				offset,
				stepSize,
				bg,
				interpolationMethod,
				composite2ARGBDouble,
				intensityScale,
				anaglyph );
		
		return omp;
	}
	
	final static public void main( final String[] args ) throws Exception
	{
		new ImageJ();
		
		final AffineTransform3D t = new AffineTransform3D();
		t.rotate( 0, 135.0 / 180.0 * Math.PI );
//		t.set(
//				1.0, 0.0, 0.0, 0.0,
//				0.0, 0.6820009, -0.731357, 0.0,
//				0.0, 0.731357, 0.6820009, 0.0);
		t.scale( 0.8 );
		
		/* gray scale */
		//final ImagePlus imp = new ImagePlus( "/home/saalfeld/tmp/valia/2.tif" );
		final ImagePlus imp = new ImagePlus( "/home/saalfeld/tmp/valia/tassos/1-2.tif" );
		final ImagePlus omp = runGray(
				imp,
//				imp.getWidth(),
//				imp.getHeight(),
				400,
				300,
				t,
				0,
				1,
				new Translation3D(),
				1,
				0,
				Interpolation.NL,
				0,
				0.06,
				1.0 / 0.1,
				-0.005,
				false );
		omp.show();
		
		
//		/* color */
//		final ImagePlus imp2 = new ImagePlus( "/home/saalfeld/examples/l1-cns-05-05-5-DPX-9.tif" );
//		
//		final ARGBDoubleType bgARGB = new ARGBDoubleType( 1, 0, 0, 0 );
//		
//		final double s = 2.0 / 4095.0;
//		final double a = 1.0;
//		
//		final RealCompositeARGBDoubleConverter< FloatType > composite2ARGBDouble =
//				new RealCompositeARGBDoubleConverter< FloatType >( imp2.getNChannels() );
//		
//		composite2ARGBDouble.setARGB( new ARGBDoubleType( a, s, 0, 0 ), 0 );
//		composite2ARGBDouble.setARGB( new ARGBDoubleType( 0.35 * a, s, s, s ), 1 );
//		composite2ARGBDouble.setARGB( new ARGBDoubleType( 0, s, s, s ), 2 );
//		composite2ARGBDouble.setARGB( new ARGBDoubleType( a, 0, s, 0 ), 3 );
//		composite2ARGBDouble.setARGB( new ARGBDoubleType( a, 0, 0, s ), 4 );
//		
//		final ImagePlus omp2 = runARGB(
//				imp2,
//				imp2.getWidth(),
//				imp2.getHeight(),
//				new AffineTransform3D(),
//				0,
//				1,
//				bgARGB,
//				Interpolation.NN,
//				composite2ARGBDouble );
//		
//		omp2.show();
		
		
		/* color 2 */
//		final ImagePlus imp2 = new ImagePlus( "/home/saalfeld/examples/l1-cns-05-05-5-DPX-9.tif" );
//		
//		final int width = 1280;
//		final int height = 720;
//		
//		/* orientation */
//		final AffineTransform3D affine = new AffineTransform3D();
//		affine.set(
//				-0.9466575, 0.27144936, -0.17364806, 0.0,
//				-0.2915748, -0.9509912, 0.102940395, imp2.getHeight() / 20.0,
//				-0.13719493, 0.14808047, 0.97941136, 0.0 );
//		appendCamera5( affine, 0.0 );
//		
//		/* camera */
//		final double distance = 1.25;
//		final double f = 1;
//		
//		/* stereo-shifts */
//		final double disp2 = imp2.getWidth() / 15.0;
//		final Translation3D offset = new Translation3D( 0.9 * ( disp2 * width / imp2.getWidth() ), 0, 0 );
//		
//		final ARGBDoubleType bgARGB = new ARGBDoubleType( 1, 0, 0, 0 );
//		
//		final double s = 2.0 / 4095.0;
//		final double a = 1.0;
//		
//		final RealCompositeARGBDoubleConverter< FloatType > composite2ARGBDouble =
//				new RealCompositeARGBDoubleConverter< FloatType >( imp2.getNChannels() );
//		
//		composite2ARGBDouble.setARGB( new ARGBDoubleType( a, s, 0, 0 ), 0 );
//		composite2ARGBDouble.setARGB( new ARGBDoubleType( 0.35 * a, 0, 0, 0 ), 1 );
//		composite2ARGBDouble.setARGB( new ARGBDoubleType( 0, s, s, s ), 2 );
//		composite2ARGBDouble.setARGB( new ARGBDoubleType( a, 0, s, 0 ), 3 );
//		composite2ARGBDouble.setARGB( new ARGBDoubleType( a, 0, 0, s ), 4 );
//		
//		final ImagePlus omp2 = runARGB(				
//				imp2,
//				imp2.getWidth(),
//				imp2.getHeight(),
//				new AffineTransform3D(),
//				0,
//				1,
//				new Translation3D(),
//				1,
//				bgARGB,
//				Interpolation.NN,
//				composite2ARGBDouble );
		
//		final ImagePlus omp2 = runARGBStereo(
//				imp2,
//				width,
//				height,
//				affine,
//				distance,
//				f,
//				disp2,
//				offset,
//				1,
//				bgARGB,
//				Interpolation.NN,
//				composite2ARGBDouble,
//				3,
//				Anaglyph.RedCyan );
		
//		omp2.show();
	}
}
