/*
 * 
 * Copyright 2014 Jules White
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package com.courseracapstone.serverside.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;

import org.imgscalr.Scalr;

import com.courseracapstone.serverside.repository.Gift;

/**
 * This class provides a simple implementation to store gift binary
 * data on the file system in a "gifts" folder. The class provides
 * methods for saving gifts and retrieving their binary data.
 * 
 * @author jules
 * @author xxx xxx - modified to save images and thumbnails
 * and to save the paths into the gift itself, so when the client
 * side needs an image, it can send a string path instead of a full
 * Gift
 *
 */
public class ImageFileManager {

	public static ImageFileManager get() throws IOException {
		return new ImageFileManager();
	}
	
	private Path targetDir_ = Paths.get("gifts");
	
	// The ImageFileManager.get() method should be used
	// to obtain an instance
	private ImageFileManager() throws IOException{
		if(!Files.exists(targetDir_)){
			Files.createDirectories(targetDir_);
		}
	}
	
	// Private helper method for resolving gift file paths
	private Path getGiftPath(Gift g){
		assert(g != null);
		
		return targetDir_.resolve("gift"+g.getId());
	}
	
	// Private helper method for resolving gift file paths
	private Path getGiftThumbnailPath(Gift g){
		assert(g != null);
		
		return targetDir_.resolve("thumb"+g.getId());
	}
	
	public void deleteGiftImages(Gift g) throws IOException {
		assert(g != null);

		Path img = getGiftPath(g);
		if(Files.exists(img)){
			Files.delete(img);
		}
				
		Path thb = getGiftThumbnailPath(g);
		if(Files.exists(thb)){
			Files.delete(thb);
		}
	}
	
	/**
	 * Returns true if the string converts to a path that points to a file
	 * that exists
	 * @param pathStr
	 * @throws URISyntaxException 
	 */
	public void copyPathData(String pathStr, OutputStream out) throws IOException, URISyntaxException {
		Path p = Paths.get(new URI(pathStr));
		
		if(!Files.exists(p)){
			throw new FileNotFoundException("Unable to find the referenced gift image file for pathStr: "+pathStr);
		}
		Files.copy(p, out);
	}
	
	/**
	 * This method reads all of the data in the provided InputStream and stores
	 * it on the file system. The data is associated with the gift object that
	 * is provided by the caller.
	 * 
	 * @param g
	 * @param format 
	 * @param imageData
	 * @throws IOException
	 */
	public void saveGiftData(Gift g, String format, InputStream imageData) throws IOException{
		assert(imageData != null);
		
		Path giftTarget = getGiftPath(g);
		Files.copy(imageData, giftTarget, StandardCopyOption.REPLACE_EXISTING);
		g.setImagePath(giftTarget.toUri().toString());
		g.setImageFormat(format);
		
		// use scalr library to produce a 150 square thumbnail image
		BufferedImage thumbImg = ImageIO.read(giftTarget.toFile());
		// the example suggested other methods, but in fact leaving scalr to do its
		// default worked when the other produced transparent output for gif and png
		thumbImg = Scalr.resize(thumbImg, 150);// Scalr.Method.SPEED, 150, Scalr.OP_ANTIALIAS, Scalr.OP_BRIGHTER);
		Scalr.pad(thumbImg, 4); // border it
		
		// write the image to a a byte stream in the format specified
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(thumbImg, format, baos);
		
		// get an input stream on the bytes
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		// and copy it to the target path
		Path thumbTarget = getGiftThumbnailPath(g);
		Files.copy(bais, thumbTarget, StandardCopyOption.REPLACE_EXISTING);
		g.setThumbnailPath(thumbTarget.toUri().toString());
	}

}
