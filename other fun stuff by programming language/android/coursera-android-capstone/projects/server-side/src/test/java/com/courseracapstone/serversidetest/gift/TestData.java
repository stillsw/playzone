package com.courseracapstone.serversidetest.gift;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.courseracapstone.serverside.repository.Gift;

public class TestData {

	private static String[] PSEUDO_RANDOM_TITLES = new String[] 
			{ "A nice day", "Random title", "Walk in the park", "Busting a groove", 
			  "Another random title", "Traffic", "Night sky", "Landscape", "Beach in July",
			  "Something nice", "Something scary", "Cats and dogs"
			};
	
	private static String[] PSEUDO_RANDOM_TEXT = new String[] 
			{ "", "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.", "Walk in the park", "Busting a groove", 
			  "I WANDERED lonely as a cloud\n"+
          "That floats on high o'er vales and hills,\n"+
          "When all at once I saw a crowd,\n"+
         "A host, of golden daffodils;\n"+
          "Beside the lake, beneath the trees,\n"+
          "Fluttering and dancing in the breeze."
			};
	
	private static SimpleDateFormat FORMAT = 
            new SimpleDateFormat("dd MMM HH:mm:ss");
	
	public static Gift randomGift(String userName, String name) {
		return new Gift(name, "blah blah\nblah de blah de blah", -1L, userName);
	}
	
	public static Gift randomGift(String userName) {
		return randomGift(userName, true);
	}
	
	public static Gift randomGift(String userName, boolean addTimestampToRandomTitles) {
		
		String randomText = PSEUDO_RANDOM_TEXT[(int) ((PSEUDO_RANDOM_TEXT.length - 1) * Math.random())];

		int i = (int) ((PSEUDO_RANDOM_TITLES.length - 1) * Math.random());
		
		if (addTimestampToRandomTitles) {
			// get a random title from the list			
			String title = String.format("%s (%s)"
					, PSEUDO_RANDOM_TITLES[i], FORMAT.format(new Date()));
			
			return new Gift(title, randomText, -1L, userName);
		}
		else {
			return new Gift(PSEUDO_RANDOM_TITLES[i], randomText, -1L, userName);
		}
	}
	
}
