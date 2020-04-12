package firstReboundGame.sound;

import java.io.*;
import java.util.*;
import java.net.URL;

//import javax.sound.sampled.Clip;
//import javax.sound.sampled.FloatControl;
import java.applet.Applet;
import java.applet.AudioClip;

public class SoundManager {

	private static int DEFAULT_VOLUME = 60;
	private static SoundManager instance = new SoundManager();
	private static final String SOUNDS_DIR = "sounds" + System.getProperty("file.separator");
	private static final String SOUNDS_JARDIR = "sounds/";
	private static String[] CLIP_FILE_NAMES = new String[] {
		"ding.au"
		,"hit.au"
		,"clap.wav"
		,"crowdc.wav"
		,"slide_whistle_down.wav"
	};
	private static int[] CLIP_CACHE_SIZES = new int[] {
		1
		,3
		,2
		,1
		,2
	};
	public static int STRIKER_STRIKE = 0;
	public static int WALL_BOUNCE = 1;
	public static int SCORE_GOAL = 2;
	public static int EXTRA_LIFE = 3; 
	public static int PENALTY_GOAL = 4;
//	private HashMap<String, Clip> clips = new HashMap<String, Clip>();
	private HashMap<String, ClipCache> clips = new HashMap<String, ClipCache>();
	private boolean playSounds = false;

	private SoundManager() {}
	
	public static SoundManager getInstance() {
		return instance;
	}
	
	public void playSound(int soundIdx) {
		if (this.playSounds) {
			String fileName = CLIP_FILE_NAMES[soundIdx];
			try {
				AudioClip clip = getClip(fileName);
				if (clip != null) {
					clip.play();
				}
			} 
			catch (Exception e) {
				System.out.println("SoundFileReader.playSound: exception trying to read sound file "+fileName);
			}
		}
	}
	
	public void toggleSound() {
		this.playSounds = !this.playSounds;
	}
	
	public void preloadSounds() {
	    try {
	    	for (int i = 0; i < CLIP_FILE_NAMES.length; i++) {
	    		// keep a cache of copies of the files as predetermined... eg. wall bounce needs at least 3 copies
	    		// so can keep up with the demand... strike puck only needs 1
	    		clips.put(CLIP_FILE_NAMES[i], new ClipCache(CLIP_CACHE_SIZES[i], CLIP_FILE_NAMES[i]));
	    	}
		} 
	    catch (Exception e) {
			System.out.println("SoundFileReader.preloadSounds: exception trying to read sound file");
		}
	}
		
	private AudioClip getClip(String fileName) throws Exception {
		return clips.get(fileName).getNext();
		
	}

	private class ClipCache {
		private int lastUsed = -1;
		private AudioClip[] cache;
		
		private ClipCache(int howMany, String fileName) {
			this.cache = new AudioClip[howMany];
			for (int i = 0; i < cache.length; i++) {
				try {
					cache[i] = getClip(fileName);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}
		
		private AudioClip getClip(String fileName) throws Exception {
			try {
				URL soundUrl = getClass().getClassLoader().getResource(SOUNDS_DIR + fileName);
				if (soundUrl == null) // try the jar version
					soundUrl = getClass().getClassLoader().getResource(SOUNDS_JARDIR + fileName);
				if (soundUrl == null)
					throw new Exception("SoundFileReader.getClip: Unable to load "+SOUNDS_DIR + fileName);
				else 
					System.out.println("SoundFileReader.getClip: Loaded "+soundUrl);
	
				return Applet.newAudioClip(soundUrl);
			}
			catch (Exception e) {
				throw e;
			}
		}
		private AudioClip getNext() {
			if (++lastUsed == cache.length)
				lastUsed = 0;
			return cache[lastUsed];
		}
	}
	
	
	public boolean isPlayingSounds() { return this.playSounds; }
	
/*
	public void playSound(int soundIdx) {
		try {
			Clip clip = getClip(CLIP_FILE_NAMES[soundIdx]);
			System.out.println("play sound "+clip);
			if (clip != null) {
				clip.stop();
				clip.setFramePosition(0);
				clip.start();
			}
		} 
		catch (Exception e) {
			System.out.println("SoundFileReader: exception trying to read sound file");
		}
		
	}
	
	public void preloadSounds() {
	    try {
	    	for (int i = 0; i < CLIP_FILE_NAMES.length; ) {
	    		this.getClip(CLIP_FILE_NAMES[i++]);
	    	}
		} 
	    catch (Exception e) {
			System.out.println("SoundFileReader: exception trying to read sound file");
		}
	}
		
	private Clip getClip(String fileName) throws Exception {
		
		if (!clips.containsKey(fileName)) {
			try {
				URL soundUrl = getClass().getResource(SOUNDS_DIR + fileName);
				if (soundUrl == null)
					throw new Exception("Unable to load "+SOUNDS_DIR + fileName);
				else 
					System.out.println("found it in "+soundUrl);
	
	//			URL fileURL = FileLocator.find(bundle, new Path(FILE_SEPARATOR+"sounds"+FILE_SEPARATOR+name+".wav"), null);
				File file = new File(soundUrl.getFile());
				ClipPlayer player = new ClipPlayer(file);
				Clip clip = player.getClip();
				clips.put(fileName, clip);

//				this.applyVolume(clip);
			}
			catch (Exception e) {
				e.printStackTrace(); 
				throw e;
			}
		}
		return clips.get(fileName);
		
	}

	private void applyVolume(Clip clip) {
		double dbgain = 20.0 * Math.log10(DEFAULT_VOLUME / 100.0);
				
		if(clip.isControlSupported(FloatControl.Type.MASTER_GAIN)){
			FloatControl vol = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
			float min = vol.getMinimum();
			if(dbgain < min){dbgain = min;}
			float max = vol.getMaximum();
			if(dbgain > max){dbgain = max;}
			vol.setValue((float) dbgain);
		}	
	}
*/ 
}