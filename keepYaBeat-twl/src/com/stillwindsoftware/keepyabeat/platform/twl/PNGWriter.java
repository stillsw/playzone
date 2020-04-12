/*
 * PNGWriter.java
 *
 * Copyright (c) 2007 Matthias Mann - www.matthiasmann.de
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */
 
package com.stillwindsoftware.keepyabeat.platform.twl;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.lwjgl.opengl.GL11;

import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation.Key;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.utils.StringUtils;
 
/**
 * A small PNG writer to save RGB data.
 *
 * @author Matthias Mann
 * @author tomas stubbs - cobbled together all from Matthias' code, made Runnable to simply be able to run this class
 * in ExecutorService or Thread
 */
public class PNGWriter implements Runnable {

    private static final byte[] SIGNATURE = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
    private static final int IHDR = (int)0x49484452;
    private static final int IDAT = (int)0x49444154;
    private static final int IEND = (int)0x49454E44;
    private static final byte COLOR_TRUECOLOR = 2;
    private static final byte COMPRESSION_DEFLATE = 0;
    private static final byte FILTER_NONE = 0;
    private static final byte INTERLACE_NONE = 0;
    private static final byte PAETH = 4;
	public static final String PNG = "png";
 
	private int width;
	private int height;
	private ByteBuffer byteBuffer;
	private File dir;
	private File outFile;

	private PlatformResourceManager resourceManager = TwlResourceManager.getInstance();

	public PNGWriter(File dir) {
		this.dir = dir;
	}

	/**
	 * Takes a screenshot and writes into the specified file, then outputs a message to say so
	 * @param dir
	 */
	public static void takeScreenShot(File dir) {
		// create a png writer and set its data
		PNGWriter pngWriter = new PNGWriter(dir);

		// grab the data
		int width = pngWriter.resourceManager.getGuiManager().getScreenWidth();
		int height = pngWriter.resourceManager.getGuiManager().getScreenHeight();
		ByteBuffer bb = ByteBuffer.allocateDirect(width*height*3);
		GL11.glReadBuffer(GL11.GL_BACK);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, bb);

		pngWriter.setData(width, height, bb);
		
		// start the thread that will create the file and write to it
		new Thread(pngWriter).start();
	}

	/**
	 * Grab initial data
	 * @param width
	 * @param height
	 * @param byteBuffer
	 */
	private void setData(int width, int height, ByteBuffer byteBuffer) {
		this.width = width;
		this.height = height;
		this.byteBuffer = byteBuffer;
	}	

	/**
	 * Create the output file and write to it, output success to user, or failure
	 */
	private void createPNG() {
		try {
			// create a new file
			String outFileName = String.format("kyb screenshot %s.%s", StringUtils.getFileNameTimestamp(), PNG);
			outFile = new File(dir, outFileName);

			// write out the png
			write();

			resourceManager.log(LOG_TYPE.info, this, String.format(
					"PNGWriter.createOutputFile: successfully wrote file =%s", outFile.getAbsolutePath()));

			// success, let the user know
			resourceManager.getGuiManager().showNotification(
					resourceManager.getLocalisedString(Key.SCREENSHOT_GENERATED));

		} catch (Exception e) {
			resourceManager.log(LOG_TYPE.error, this, "PNGWriter.createOutputFile: unexpected error");
			e.printStackTrace();
			reportError(e.getMessage());
		}
	}

	@Override
	public void run() {
    	createPNG();
	}

	/**
	 * Standard format of an error reported to the user
	 * @param key
	 */
	private void reportError(String msg) {
		resourceManager.getGuiManager().warnOnErrorMessage(String.format(
				resourceManager.getLocalisedString(Key.TAKE_SCREENSHOT_ERROR), msg)
			, resourceManager.getLocalisedString(Key.TAKE_SCREENSHOT_ERROR_TITLE), false, null);
	}

    /**
     * Writes an image in OpenGL GL_RGB format to an OutputStream.
     *
     * @param os The output stream where the PNG should be written to
     */
    public void write(OutputStream os) throws IOException {

        // duplicate the ByteBuffer to preserve position in case of concurrent access
        ByteBuffer bb = byteBuffer.duplicate();
 
        DataOutputStream dos = new DataOutputStream(os);
        dos.write(SIGNATURE);
 
        @SuppressWarnings("resource")
		Chunk cIHDR = new Chunk(IHDR);
        cIHDR.writeInt(width);
        cIHDR.writeInt(height);
        cIHDR.writeByte(8); // 8 bit per component
        cIHDR.writeByte(COLOR_TRUECOLOR);
        cIHDR.writeByte(COMPRESSION_DEFLATE);
        cIHDR.writeByte(FILTER_NONE);
        cIHDR.writeByte(INTERLACE_NONE);
        cIHDR.writeTo(dos);
 
        Chunk cIDAT = new Chunk(IDAT);
        @SuppressWarnings("resource")
		DeflaterOutputStream dfos = new DeflaterOutputStream(
            cIDAT, new Deflater(Deflater.BEST_COMPRESSION));
 
        int lineLen = width * 3;
        byte[] lineOut = new byte[lineLen];
        byte[] curLine = new byte[lineLen];
        byte[] prevLine = new byte[lineLen];
 
        for(int line=0 ; line<height ; line++) {
            bb.position((height - line - 1)*lineLen);
            bb.get(curLine);
 
            lineOut[0] = (byte)(curLine[0] - prevLine[0]);
            lineOut[1] = (byte)(curLine[1] - prevLine[1]);
            lineOut[2] = (byte)(curLine[2] - prevLine[2]);
 
            for(int x=3 ; x<lineLen ; x++) {
                int a = curLine[x-3] & 255;
                int b = prevLine[x] & 255;
                int c = prevLine[x-3] & 255;
                int p = a + b - c;
                int pa = p - a; if(pa < 0) pa = -pa;
                int pb = p - b; if(pb < 0) pb = -pb;
                int pc = p - c; if(pc < 0) pc = -pc;
                if(pa<=pb && pa<=pc)
                    c = a;
                else if(pb<=pc)
                    c = b;
                lineOut[x] = (byte)(curLine[x] - c);
            }
 
            dfos.write(PAETH);
            dfos.write(lineOut);
 
            // swap the line buffers
            byte[] temp = curLine;
            curLine = prevLine;
            prevLine = temp;
        }
 
        dfos.finish();
        try {
            cIDAT.writeTo(dos);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
 
        @SuppressWarnings("resource")
		Chunk cIEND = new Chunk(IEND);
        cIEND.writeTo(dos);
 
        dos.flush();
    }
 
    /**
     * Writes an image in OpenGL GL_RGB format to a File.
     *
     * @param file The file where the PNG should be written to.
                   Existing files will be overwritten.
     * @param t The Texture object. Contains a ByteBuffer with
     *          compact RGB data (no padding between lines)
     */
    private void write() throws IOException {
        FileOutputStream fos = new FileOutputStream(outFile);
        try {
            write(fos);
        } finally {
            fos.close();
        }
    }
 
    private static class Chunk extends DataOutputStream {
        final CRC32 crc;
        final ByteArrayOutputStream baos;
 
        Chunk(int chunkType) throws IOException {
            this(chunkType, new ByteArrayOutputStream(), new CRC32());
        }
        private Chunk(int chunkType, ByteArrayOutputStream baos,
                      CRC32 crc) throws IOException {
            super(new CheckedOutputStream(baos, crc));
            this.crc = crc;
            this.baos = baos;
 
            writeInt(chunkType);
        }
 
        public void writeTo(DataOutputStream out) throws IOException {
            flush();
            out.writeInt(baos.size() - 4);
            baos.writeTo(out);
            out.writeInt((int)crc.getValue());
        }
    }

}