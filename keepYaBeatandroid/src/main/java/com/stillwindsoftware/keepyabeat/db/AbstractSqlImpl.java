package com.stillwindsoftware.keepyabeat.db;

import android.content.res.Resources;

import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;

/**
 * Superclass for the sql implementations of the main entity classes
 * Sound, BeatType, Rhythm, Tag
 * @author tomas stubbs
 */
public abstract class AbstractSqlImpl {

	protected final KybSQLiteHelper mLibrary;
	protected long mId = Library.INT_NOT_CHANGED;
	protected String mKey;
	protected String mName;

	public AbstractSqlImpl(KybSQLiteHelper library, String key, String name) {
		this.mLibrary = library;
		this.mKey = key;
		this.mName = name;
	}

	public AbstractSqlImpl(KybSQLiteHelper library, String key, String name, long id) {
		this(library, key, name);
		mId = id;
	}
	
	void setInternalId(long id) {
		mId = id;
	}

	public long getInternalId() {
		return mId;
	}

	public Library getLibrary() {
		return mLibrary;
	}

	public void setKey(Function context, String key) {
		mKey = key;
	}
	
	public String getKey() {
		return mKey;
	}

	public void setName(Function context, String newName) {
		mName = newName;
	}

	public String getName() {
		return mName;
	}

	/**
	 * Called when constructing a SqlImpl which contains a localised name in the db
	 * and which now needs its local value
	 * @param localisedName
	 * @param resources 
	 * @return -1 if called with a null name, or the value from resources lookup (could be 0 if nothing found)
	 */
	public static int getLocalisedResIdFromName(String localisedName, Resources resources) {
		if (localisedName != null) {
			return resources.getIdentifier(localisedName, "string", AndroidResourceManager.PACKAGE_NAME);
		}
		else {
			return -1;
		}
	}

	/**
	 * Called when constructing a SqlImpl which contains a localised name in the db
	 * and which now needs its local value
	 * @param name
	 * @param resources 
	 * @return -1 if called with a null name, or the value from resources lookup (could be 0 if nothing found)
	 */
	protected int getRawResIdFromName(String name, Resources resources) {
		if (name != null) {
			return resources.getIdentifier(name, "raw", AndroidResourceManager.PACKAGE_NAME);
		}
		else {
			return -1;
		}
	}


}
