package com.stillwindsoftware.keepyabeat.gui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.EditRhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.RhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.db.RhythmsContentProvider;
import com.stillwindsoftware.keepyabeat.db.TagSqlImpl;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmCommandAdaptor;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Called from the title bar when in play rhythm allowing a new rhythm to be saved or an existing
 * one to be saved with new name or tags. Existing rhythms can also call this dialog from the rhythms dialog_simple_list
 * save card menu option.
 * Then from Edit Rhythm when saving to complete the edit session, this dialog pops up automatically if
 * and only if the rhythm is new and needs a name.
 * Acts as a dialog
 * Created by tomas on 05/07/16.
 */

public class SetRhythmNameAndTagsActivity extends KybActivity implements View.OnClickListener {

    static final String LOG_TAG = "KYB-"+SetRhythmNameAndTagsActivity.class.getSimpleName();

    private static final char COMMA_CHAR = ',';
    private static final String COMMA_STRING = ",";
    private static final String DOUBLE_COMMA_STRING = ",,";

    // the initialization parameters
    private static final String ARG_RHYTHM_KEY = "argRhythmKey";
    private static final String ARG_RHYTHM_NAME = "argRhythmName";
    private static final String ARG_RHYTHM_IS_NEW = "argRhythmIsNew";
    private static final String ARG_IS_EDITOR = "argIsEditor";
    private static final String SAVED_STATE_ADDED_IDS = "savedAddedIds";
    private static final String SAVED_STATE_ADDED_KEYS = "savedAddedKeys";
    private static final String SAVED_STATE_ADDED_NAMES = "savedAddedNames";
    private static final String SAVED_STATE_REMOVED_KEYS = "savedRemovedKeys";

    private String mKey;
    private String mName;
    private boolean mIsNew;
    private Rhythm mRhythm;

    private EditText mNameEditText;

    private ArrayList<Tag> mOriginalTags;
    private HashSet<TagSqlImpl> mAddedTags = new HashSet<>();
    private HashSet<String> mRemovedTags = new HashSet<>();
    private boolean mIsEditRhythm;
    private ViewGroup mTagsContainer;

    /**
     * Use this factory method to create a new instance of the intent to launch this activity
     *
     * @param context
     * @param key Parameter 1.
     * @param name Parameter 2.
     * @param isNew Parameter 3. used to detect the need for the new rhythm title
     * @return
     */
    public static Intent newInstance(Activity context, String key, String name, boolean isNew) {
        Intent intent = new Intent(context, SetRhythmNameAndTagsActivity.class);
        intent.putExtra(ARG_RHYTHM_KEY, key);
        intent.putExtra(ARG_RHYTHM_NAME, name);
        intent.putExtra(ARG_RHYTHM_IS_NEW, isNew);
        intent.putExtra(ARG_IS_EDITOR, context instanceof EditRhythmActivity);
        return intent;
    }

    /**
     * Popup a dialog_simple_list of tags to choose from
     */
    private void chooseTagsFromList() {
        ChooseRhythmTagsDialog.newInstance().show(getSupportFragmentManager(), ChooseRhythmTagsDialog.LOG_TAG);
    }

    /**
     * Called from the rhythms dialog_simple_list bindView(), each of the views might be being re-used so
     * initialize them as though new (meaning the container may have views inside that should
     * be re-used or removed), also called from RhythmPlayingFragmentHolder and this class and therefore static.
     * @param noTagsVw
     * @param formattedTagsContainer
     * @param layoutInflater
     * @param onClickListener
     */
    public static void setTagViews(TextView noTagsVw, ViewGroup formattedTagsContainer, LayoutInflater layoutInflater, View.OnClickListener onClickListener) {
        String dbEncodedTags = noTagsVw.getText().toString();

        if (dbEncodedTags.isEmpty()) {                      // easy case, just show no tags
            noTagsVw.setVisibility(View.VISIBLE);
            formattedTagsContainer.setVisibility(View.GONE);
            return;
        }

        noTagsVw.setVisibility(View.GONE);                 // have tags to show the right views
        formattedTagsContainer.setVisibility(View.VISIBLE);

        // if there are any views in the container already we will re-purpose them
        for (int i = 0; i < formattedTagsContainer.getChildCount(); i++) {
            View childAt = formattedTagsContainer.getChildAt(i);
//            AndroidResourceManager.logv(LOG_TAG, "setTagViews: found tag view in container, setting to gone "+childAt.getTag());
            childAt.setVisibility(View.GONE);
        }

        // the encoded tags contain commas as separators and double commas to escape real commas in the data

        do {
            // the first comma delimits the key from the remainder
            int endIndex = dbEncodedTags.indexOf(COMMA_CHAR);
            if (endIndex == -1) {
                AndroidResourceManager.loge(LOG_TAG, "setTagViews: no comma between key and name, weird encoded tags = "+dbEncodedTags);
                return;
            }
            String tagKey = dbEncodedTags.substring(0, endIndex);
//            AndroidResourceManager.logv(LOG_TAG, "setTagViews: chopped key="+tagKey+" from encoded tags "+dbEncodedTags);
            dbEncodedTags = dbEncodedTags.substring(endIndex + 1);
//            AndroidResourceManager.logv(LOG_TAG, "setTagViews: new value of string "+dbEncodedTags);

            // the tag name is delimited by a comma or end of string, but may contain escaped pairs of commas too

            // find the first odd (non-paired) comma
            int commaAt = -1;
            for (int i = 0; i < dbEncodedTags.length(); i++) {
                if (commaAt != -1) {                             // so the last char was a comma
                    if (dbEncodedTags.charAt(i) == COMMA_CHAR) { // this one is too, so ignore them
                        commaAt = -1;
                        continue;
                    }
                    else {                                       // last time found the delimiter
                        break;
                    }
                }

                if (dbEncodedTags.charAt(i) == COMMA_CHAR) {     // could be delimiter or start of a new escaped pair
                    commaAt = i;
                }
            }

            String tagName = commaAt == -1 ? dbEncodedTags : dbEncodedTags.substring(0, commaAt);
            if (tagName.contains(COMMA_STRING)) {
                tagName = tagName.replaceAll(DOUBLE_COMMA_STRING, COMMA_STRING);
            }
//            AndroidResourceManager.logv(LOG_TAG, "setTagViews: chopped tag="+tagName+" from encoded tags "+dbEncodedTags);
            dbEncodedTags = commaAt == -1 ? null : dbEncodedTags.substring(commaAt + 1);
//            AndroidResourceManager.logv(LOG_TAG, "setTagViews: remaining string = "+dbEncodedTags);

            // check to see if the tag already is a view in the container, but only if the tag name is the same
            boolean found = false;
            for (int i = 0; i < formattedTagsContainer.getChildCount(); i++) {
                TextView childAt = (TextView) formattedTagsContainer.getChildAt(i);

                if (childAt.getTag().equals(tagKey)) {
                    if (childAt.getText().toString().equals(tagName)) {
                        childAt.setVisibility(View.VISIBLE);
//                        AndroidResourceManager.logv(LOG_TAG, "setTagViews: found tag view in container, reusing it " + tagName);
                        found = true;
                    }
                    else {
                        childAt.setVisibility(View.GONE); // so the addTagViewToContainer() will correctly reuse it
                    }
                    break;
                }
            }

            if (!found) {
//                AndroidResourceManager.logv(LOG_TAG, "setTagViews: not found tag view in container, calling addTV " + tagName);
                addTagViewToContainer(formattedTagsContainer, tagKey, tagName, layoutInflater, R.layout.tag_label_small, onClickListener);
            }

        } while (dbEncodedTags != null);
    }

    /**
     * Needed in other places too
     * @param tagsContainer
     * @param tagKey
     * @param tagName
     * @param layoutInflater
     * @param viewResId
     * @param onClickListener
     */
    public static void addTagViewToContainer(ViewGroup tagsContainer, String tagKey, String tagName, LayoutInflater layoutInflater, int viewResId, View.OnClickListener onClickListener) {

        // look for any unused views in the container already (when in a dialog_simple_list they should be re-used)
        TextView reuseView = null;

        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        int insertAt = -1;

        // find the insertion point so it's in order, and perhaps the tag is already in the dialog_simple_list

        for (int i = 0; i < tagsContainer.getChildCount(); i++) {
            View tagVw = tagsContainer.getChildAt(i);

            if (tagVw.getId() == R.id.choose_tags_btn) {                // ignore the lov button
                continue;
            }

            String text = ((TextView)tagVw).getText().toString();

            if (tagVw.getTag().equals(tagKey)) {                        // already in the dialog_simple_list
                if (text.equals(tagName)) {                             // name is the same, nothing to do
                    tagVw.setVisibility(View.VISIBLE);
                    return;
                }
                else {                                                  // name changed, will need to be re-ordered
                    reuseView = (TextView) tagVw;                       // assign it to the reuse view
                    tagsContainer.removeView(tagVw);
//                    AndroidResourceManager.logv(LOG_TAG, "addTagViewToContainer: reusing view with ("+text+") for new name = "+ tagName);
                    continue;
                }
            }

            // insertion point is the first occurrence only
            if (insertAt == -1 && collator.compare(tagName, text) < 0) {
//                AndroidResourceManager.logv(LOG_TAG, "addTagViewToContainer: putting tag ("+tagName+") in front of = "+ text);
                insertAt = i;
            }
        }

        if (reuseView == null) {                                        // didn't find it in the dialog_simple_list already, see if any other unused views
            for (int i = 0; i < tagsContainer.getChildCount(); i++) {
                TextView child = (TextView) tagsContainer.getChildAt(i);
                if (child.getVisibility() == View.GONE) {
//                    AndroidResourceManager.logv(LOG_TAG, "addTagViewToContainer: reusing view (was for " + child.getText() + ") for tag = " + tagName);
                    reuseView = child;
                    tagsContainer.removeView(child);
                }
            }
        }

        // only inflate if don't already have the button
        TextView newTagView = reuseView == null ? (TextView) layoutInflater.inflate(viewResId, null) : reuseView;
        if (reuseView == null) {
//            AndroidResourceManager.logv(LOG_TAG, "addTagViewToContainer: making new tag ("+tagName+") insertAt = "+ insertAt);
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            newTagView.setLayoutParams(params);
            if (onClickListener != null) {
                newTagView.setOnClickListener(onClickListener);
            }
        }
        newTagView.setText(tagName);
        newTagView.setTag(tagKey);
        newTagView.setVisibility(View.VISIBLE);

        if (insertAt == -1 || insertAt >= tagsContainer.getChildCount()) {
//            AndroidResourceManager.logv(LOG_TAG, "addTagViewToContainer: add to end ("+tagName+")");
            tagsContainer.addView(newTagView);
        }
        else {
//            AndroidResourceManager.logv(LOG_TAG, "addTagViewToContainer: add to insert point ("+tagName+") at "+insertAt);
            tagsContainer.addView(newTagView, insertAt);
        }
    }

    /**
     * Needed in other places
     * @param tagsContainer
     * @param tagKey
     */
    public static void removeTagButtonFromContainer(ViewGroup tagsContainer, String tagKey) {

        for (int i = 0; i < tagsContainer.getChildCount(); i++) {
            View tagVw = tagsContainer.getChildAt(i);

            if (tagVw.getId() == R.id.choose_tags_btn) {               // ignore the lov button
                continue;
            }

            if (tagVw.getTag().equals(tagKey)) {
                tagsContainer.removeView(tagVw);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // would be a program bug to not be there
        Intent intent = getIntent();
        mKey = intent.getStringExtra(ARG_RHYTHM_KEY);
        mName = intent.getStringExtra(ARG_RHYTHM_NAME);
        mIsNew = intent.getBooleanExtra(ARG_RHYTHM_IS_NEW, false);
        mIsEditRhythm = intent.getBooleanExtra(ARG_IS_EDITOR, false);

        setResult(mIsEditRhythm ? RESULT_CANCELED : RESULT_OK); // editActivity defaults to cancelled so the edit rhythm is only closed on success
                                                                // otherwise default to ok so only explicit error is treated as a cancel (see PlayRhythmsActivity)

        // screen changes will save away tags state
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(SAVED_STATE_ADDED_KEYS)) {

                KybSQLiteHelper library = (KybSQLiteHelper) mResourceManager.getLibrary();

                long[] addedIds = savedInstanceState.getLongArray(SAVED_STATE_ADDED_IDS);
                String[] addedKeys = savedInstanceState.getStringArray(SAVED_STATE_ADDED_KEYS);
                String[] addedNames = savedInstanceState.getStringArray(SAVED_STATE_ADDED_NAMES);

                for (int i = 0; i < addedNames.length; i++) {
                    TagSqlImpl tag = new TagSqlImpl(library, addedKeys[i], addedNames[i], addedIds[i]);
                    mAddedTags.add(tag);
                }
            }

            if (savedInstanceState.containsKey(SAVED_STATE_REMOVED_KEYS)) {
                String[] removedKeys = savedInstanceState.getStringArray(SAVED_STATE_REMOVED_KEYS);

                for (int i = 0; i < removedKeys.length; i++) {
                    mRemovedTags.add(removedKeys[i]);
                }
            }
        }

        // set up the content
        setContentView(R.layout.set_rhythm_name_and_tags_activity);

        setTitle(mIsNew ? R.string.saveNewRhythm : R.string.menuSetNameRhythm);

        mNameEditText = (EditText) findViewById(R.id.rhythm_name);
        KybDialogFragment.trackTextLength(mNameEditText, (TextView)findViewById(R.id.textLenIndicator), getResources().getInteger(R.integer.maxRhythmNameLen));
        if (mName != null) {
            mNameEditText.setText(mName);
        }
        else {
            mNameEditText.requestFocus(); // now want the keyboard to show
            showKeypad(null);
        }

        mTagsContainer = (ViewGroup) findViewById(R.id.chosen_tags);

        findViewById(R.id.choose_tags_btn).setOnClickListener(this);
        findViewById(R.id.ok_btn).setOnClickListener(this);
        findViewById(R.id.cancel_btn).setOnClickListener(this);

        // load the rhythm and tags, once done, kick off loading the tags
        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        final Library library = mResourceManager.getLibrary();
                        final RhythmsContentProvider rhythms = (RhythmsContentProvider) library.getRhythms();

                        try {
                            while (!rhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                                AndroidResourceManager.logd(LOG_TAG, "onCreate: waiting for lock on player rhythm");
                            }

                            RhythmSqlImpl testRhythm = rhythms.getPlayerEditorRhythm();

                            if (mIsEditRhythm) {
                                if (testRhythm instanceof EditRhythmSqlImpl) {
                                    mRhythm = testRhythm;
                                }
                                else {
                                    AndroidResourceManager.loge(LOG_TAG, "onCreate: called as edit rhythm, but there isn't one!");
                                    return; // activity will abort in the UI thread
                                }
                            }
                            else if (testRhythm != null && testRhythm.getKey().equals(mKey)) {
                                mRhythm = testRhythm;
                            }
                            else {
                                mRhythm = rhythms.lookup(mKey);

                                if (mRhythm == null) { // should be a new rhythm if lookup failed
                                    mRhythm = rhythms.getExistingNewRhythm(); // try for the last used new rhythm
                                }
                            }

                            mOriginalTags = library.getTags().getRhythmTags(mRhythm);

                        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
                            AndroidResourceManager.loge(LOG_TAG, "onCreate: program error", e);
                        } finally {
                            rhythms.releaseReadLockOnPlayerEditorRhythm();
                        }

                    }
                },
                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        // detect an error where no rhythm found, pop up an error and abort the activity
                        if (mRhythm == null) {
                            AndroidResourceManager.loge(LOG_TAG, "onCreate: no rhythm found, should not ever happen");
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                        updateRhythmTagsList();
                    }
                }
        );
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // changes to the dialog_simple_list of tags are not saved during screen changes (the name is)
        if (!mAddedTags.isEmpty()) {
            long[] addedIds = new long[mAddedTags.size()];
            String[] addedKeys = new String[mAddedTags.size()];
            String[] addedNames = new String[mAddedTags.size()];

            int i = 0;
            for (TagSqlImpl tag : mAddedTags) {
                addedIds[i] = tag.getInternalId();
                addedKeys[i] = tag.getKey();
                addedNames[i] = tag.getName();
                i++;
            }

            outState.putLongArray(SAVED_STATE_ADDED_IDS, addedIds);
            outState.putStringArray(SAVED_STATE_ADDED_KEYS, addedKeys);
            outState.putStringArray(SAVED_STATE_ADDED_NAMES, addedNames);
        }

        if (mRemovedTags.size() > 0) {
            String[] removedKeys = new String[mRemovedTags.size()];
            mRemovedTags.toArray(removedKeys);
            outState.putStringArray(SAVED_STATE_REMOVED_KEYS, removedKeys);
        }

        super.onSaveInstanceState(outState);
    }

    /**
     * Called after the loading has completed, and when there's an update to the data
     */
    private void updateRhythmTagsList() {

        // if there are any views in the container already we will re-purpose them
        for (int i = 0; i < mTagsContainer.getChildCount(); i++) {
            View childAt = mTagsContainer.getChildAt(i);

            if (childAt.getId() == R.id.choose_tags_btn) {
                continue;
            }

            AndroidResourceManager.logv(LOG_TAG, "updateRhythmTagsList: found tag view in container, setting to gone "+childAt.getTag());
            childAt.setVisibility(View.GONE);
        }

        LayoutInflater layoutInflater = getLayoutInflater();

        // add all the original tags to the results
        for (int i = 0; i < mOriginalTags.size(); i++) {
            Tag tag = mOriginalTags.get(i);
            String key = tag.getKey();
            if (!mRemovedTags.contains(key)) { // unless removed
                addTagViewToContainer(mTagsContainer, key, tag.getName(), layoutInflater, R.layout.tag_label_small, null);
            }
        }

        // and the added ones
        for (TagSqlImpl tag : mAddedTags) {
            addTagViewToContainer(mTagsContainer, tag.getKey(), tag.getName(), layoutInflater, R.layout.tag_label_small, null);
        }

    }


    // ------------------------ OnClickListener

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        hideKeypad(view);

        if (viewId == R.id.tag_choose_row) {
            reactToTagSelection((RelativeLayout) view);
        }
        else if (viewId == R.id.choose_tags_btn) {
            chooseTagsFromList();
        }
        else if (viewId == R.id.ok_btn) {
            validateAndSaveChanges();
        }
        else if (viewId == R.id.cancel_btn) {
            hideKeypad(view);
            if (!mIsEditRhythm) { // way of telling player that there was no error, but for edit rhythm should be set to cancelled (default)
                setResult(RESULT_OK);
            }
            finish();
        }
    }

    protected void hideKeypad(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    protected void showKeypad(View view) {
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void reactToTagSelection(RelativeLayout layout) {
        // only for the tags rows, should be a relative layout
        final TextView tv = (TextView) layout.findViewById(R.id.tag_name);
        final TextView tagKey = (TextView) layout.findViewById(R.id.tag_key);
        final TextView tagId = (TextView) layout.findViewById(R.id.internal_id);
        final long id = Long.parseLong(tagId.getText().toString());
        final String key = tagKey.getText().toString();
        final String name = tv.getText().toString();
        final TagSqlImpl tag = new TagSqlImpl((KybSQLiteHelper) mResourceManager.getLibrary(), key, name, id);

        final CheckBox selectBox = (CheckBox) layout.findViewById(R.id.tag_checkbox);
        final boolean prevSelected = selectBox.isChecked();
        selectBox.setChecked(!prevSelected);

        if (prevSelected) {
            // remove rhythm tags row
            if (mAddedTags.contains(tag)) {
                mAddedTags.remove(tag);
            }
            else if (!mRemovedTags.contains(key)) {
                mRemovedTags.add(key);
            }
        } else {
            // add rhythm tags row
            if (!mAddedTags.contains(tag)) {
                mAddedTags.add(tag);
            }
        }

        updateRhythmTagsList();
    }

    private void validateAndSaveChanges() {
        // validation on ok button

        final String newName = RhythmEncoder.sanitizeString(mResourceManager, mNameEditText.getText().toString(), RhythmEncoder.SANITIZE_STRING_REPLACEMENT_CHAR);

        if (newName.isEmpty()) {     // name cannot be null
            Toast.makeText(this, R.string.enterUniqueName, Toast.LENGTH_SHORT).show();
        }
        else { // validate name is unique and make changes

            // db validation and changes in background thread, success or error is shown in ui thread
            mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                    new ParameterizedCallback(true) {
                        @Override
                        public Object runForResult() {
                            final KybSQLiteHelper library = (KybSQLiteHelper) mResourceManager.getLibrary();

                            boolean isNameChanged = !newName.equals(mName);

                            if (isNameChanged) {
                                boolean exists = ((RhythmsContentProvider) library.getRhythms()).nameExists(newName, mRhythm.getKey());

                                if (exists) {
                                    return getString(R.string.nameUsed);
                                }
                            }

                            boolean tagsChanged = !mRemovedTags.isEmpty() || !mAddedTags.isEmpty();

                            if (!isNameChanged && !tagsChanged) {
                                AndroidResourceManager.logd(LOG_TAG, "onClick.runForResult: nothing changed, no db changes made");
                                return null; // do nothing, neither name nor tags have changed
                            }

                            // valid and there's something to do, make the db changes

                            // remove tags that have been deselected
                            for (int i = mOriginalTags.size() -1; i >= 0; i--) { // could be deleting from the dialog_simple_list, so iterate backwards
                                if (mRemovedTags.contains(mOriginalTags.get(i).getKey())) {
                                    mOriginalTags.remove(i);
                                }
                            }

                            // add new ones
                            for (TagSqlImpl tag : mAddedTags) {
                                mOriginalTags.add(tag);
                            }

                            // execute the command
                            if (mIsEditRhythm) {
                                // this dialog is only used in rhythm editing if the rhythm is new
                                new RhythmCommandAdaptor.RhythmNewSaveEditCommand((EditRhythm)mRhythm, newName, mOriginalTags).execute();
                            }
                            else {
                                new RhythmCommandAdaptor.RhythmSaveCommand(mRhythm, newName, mOriginalTags).execute();
                            }

                            return null; // null means success as there's no error message to display
                        }
                    },
                    new ParameterizedCallback(false) {
                        @Override
                        public void run() {
                            if (mParam == null) { // no error message means success
                                hideKeypad(mNameEditText);
                                setResult(RESULT_OK);
                                finish();
                            }
                            else { // invalid because the name exists
                                Toast.makeText(SetRhythmNameAndTagsActivity.this, (String)mParam, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
            );
        }
    }

    public boolean isSelectedTag(String selection) {

        for (int i = 0; i < mOriginalTags.size(); i++) {
            Tag tag = mOriginalTags.get(i);
            String key = tag.getKey();
            if (key.equals(selection) && !mRemovedTags.contains(key)) { // unless removed
                return true;
            }
        }

        // and the added ones
        for (TagSqlImpl tag : mAddedTags) {
            if (tag.getKey().equals(selection)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Called from AddNewOrRenameTagDialog (which was called from ChooseRhythmTagsDialog)
     * let the SetName fragment know about the new tag
     * so it can be added to the list to be made
     * @param tag
     */
    public void newTagAddedForRhythm(TagSqlImpl tag) {

        mAddedTags.add(tag);
        updateRhythmTagsList();
    }
}
