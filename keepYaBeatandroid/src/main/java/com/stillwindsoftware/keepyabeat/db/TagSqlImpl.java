package com.stillwindsoftware.keepyabeat.db;

import android.content.Context;
import android.database.Cursor;

import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.TagRhythm;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;

public class TagSqlImpl extends AbstractSqlImpl implements Tag {

	public TagSqlImpl(Context context, KybSQLiteHelper library, Cursor csr) {
		super(library
				, csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY))
				, csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_TAG_NAME))
				, csr.getLong(csr.getColumnIndex(KybSQLiteHelper.COLUMN_ID)));
	}

    /**
     * Specially allowed for creating tag objects to pass to RhythmSaveCommand (see SetRhythmNameAndTagsDialog.onClick())
     * @param library
     * @param key
     * @param value
     * @param id
     */
    public TagSqlImpl(KybSQLiteHelper library, String key, String value, long id) {
        super(library, key, value, id);
    }

    /**
     * For undoing delete tag
     * @param library
     * @param key
     * @param name
     */
	public TagSqlImpl(KybSQLiteHelper library, String key, String name) {
		super(library, key, name);
	}

	@Override
	public void setName(String name) {
		super.setName(Function.getBlankContext(mLibrary.getResourceManager()), name);
	}

	/**
	 * It's possible the return type is never used in the app... return null
	 * for now.
	 */
	@Override
	public TagRhythm addRhythm(Rhythm rhythm) {
		((TagsContentProvider)mLibrary.getTags()).addRhythmToTag(this, (RhythmSqlImpl) rhythm);
		return null;
	}

    @Override
    public String getListenerKey() {
        return getKey();
    }
}
