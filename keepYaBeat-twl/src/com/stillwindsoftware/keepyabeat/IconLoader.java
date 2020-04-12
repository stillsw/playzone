package com.stillwindsoftware.keepyabeat;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import com.stillwindsoftware.keepyabeat.data.DataFolder;

/*****************************************************************************
 * A convenience class for loading icons from images.
 * 
 * Icons loaded from this class are formatted to fit within the required
 * dimension (16x16, 32x32, or 128x128). If the source image is larger than the
 * target dimension, it is shrunk down to the minimum size that will fit. If it
 * is smaller, then it is only scaled up if the new scale can be a per-pixel
 * linear scale (i.e., x2, x3, x4, etc). In both cases, the image's width/height
 * ratio is kept the same as the source image.
 * 
 * @author Chris Molini
 *
 * Modified to load several images from a particular folder each of which is already
 * the size required, no scaling is needed.
 * @author tomas stubbs
 */
public class IconLoader {

	private static BufferedImage getImage(String fileName) {
		
		// the loader is the same as for the DataLoader class (same folder)
		BufferedImage image = null;
		try {
			InputStream is = DataFolder.class.getResourceAsStream(fileName);
			image = ImageIO.read(is);
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		
		return image;
	}
	
	public static ByteBuffer[] load() {
		
		ByteBuffer[] buffers = null;
		String OS = System.getProperty("os.name").toUpperCase();
		
		if(OS.contains("WIN")) {
			buffers = new ByteBuffer[] {
					loadInstance("kyb icon 16x16.png", 16),
					loadInstance("kyb icon 32x32.png", 32),
					loadInstance("kyb icon 128x128.png", 128) };
		}
		else if(OS.contains("MAC")) {
			buffers = new ByteBuffer[] {
					loadInstance("kyb icon 128x128.png", 128) };
		}
		else {
			buffers = new ByteBuffer[] {
					loadInstance("kyb icon 128x128.png", 128),
					loadInstance("kyb icon 32x32.png", 32) };
		}
		
		return buffers;
	}

	private static ByteBuffer loadInstance(String fileName, int dimension) {
		return loadInstance(getImage(fileName), dimension);
	}

	/*************************************************************************
	 * Copies the supplied image into a square icon at the indicated size.
	 * 
	 * @param image
	 *            The image to place onto the icon.
	 * @param dimension
	 *            The desired size of the icon.
	 * 
	 * @return A ByteBuffer of pixel data at the indicated size.
	 *************************************************************************/
	private static ByteBuffer loadInstance(BufferedImage image, int dimension) {
		
		BufferedImage scaledIcon = new BufferedImage(dimension, dimension,
				BufferedImage.TYPE_INT_ARGB_PRE);
		Graphics2D g = scaledIcon.createGraphics();
		double ratio = getIconRatio(image, scaledIcon);
		double width = image.getWidth() * ratio;
		double height = image.getHeight() * ratio;
		g.drawImage(image, (int) ((scaledIcon.getWidth() - width) / 2),
				(int) ((scaledIcon.getHeight() - height) / 2), (int) (width),
				(int) (height), null);
		g.dispose();

		return convertToByteBuffer(scaledIcon);
	}

	/*************************************************************************
	 * Gets the width/height ratio of the icon. This is meant to simplify
	 * scaling the icon to a new dimension.
	 * 
	 * @param src
	 *            The base image that will be placed onto the icon.
	 * @param icon
	 *            The icon that will have the image placed on it.
	 * 
	 * @return The amount to scale the source image to fit it onto the icon
	 *         appropriately.
	 *************************************************************************/
	private static double getIconRatio(BufferedImage src, BufferedImage icon)
	{
		double ratio = 1;
		if (src.getWidth() > icon.getWidth())
			ratio = (double) (icon.getWidth()) / src.getWidth();
		else
			ratio = (int) (icon.getWidth() / src.getWidth());
		if (src.getHeight() > icon.getHeight())
		{
			double r2 = (double) (icon.getHeight()) / src.getHeight();
			if (r2 < ratio)
				ratio = r2;
		}
		else
		{
			double r2 = (int) (icon.getHeight() / src.getHeight());
			if (r2 < ratio)
				ratio = r2;
		}
		return ratio;
	}

	/*************************************************************************
	 * Converts a BufferedImage into a ByteBuffer of pixel data.
	 * 
	 * @param image
	 *            The image to convert.
	 * 
	 * @return A ByteBuffer that contains the pixel data of the supplied image.
	 *************************************************************************/
	public static ByteBuffer convertToByteBuffer(BufferedImage image)
	{
		byte[] buffer = new byte[image.getWidth() * image.getHeight() * 4];
		int counter = 0;
		for (int i = 0; i < image.getHeight(); i++)
			for (int j = 0; j < image.getWidth(); j++)
			{
				int colorSpace = image.getRGB(j, i);
				buffer[counter + 0] = (byte) ((colorSpace << 8) >> 24);
				buffer[counter + 1] = (byte) ((colorSpace << 16) >> 24);
				buffer[counter + 2] = (byte) ((colorSpace << 24) >> 24);
				buffer[counter + 3] = (byte) (colorSpace >> 24);
				counter += 4;
			}
		return ByteBuffer.wrap(buffer);
	}
}
