package com.stillwindsoftware.keepyabeat.twlModel;

import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.model.Tag;


public class TagMemento {
	private String key;
	private String name;
	private ArrayList<String> fkRhythmKeys;

	public TagMemento(Tag tag, ArrayList<String> rhythmTags) {
		key = tag.getKey();
		name = tag.getName();
		fkRhythmKeys = rhythmTags;
	}

	public String getKey() {
		return key;
	}

	public String getName() {
		return name;
	}

	public ArrayList<String> getFkRhythmKeys() {
		return fkRhythmKeys;
	}

}

