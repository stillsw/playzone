package com.stillwindsoftware.keepyabeat.gui;

import android.app.Activity;
import android.app.Dialog;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.SoundsContentProvider;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.ListenerSupport;
import com.stillwindsoftware.keepyabeat.model.transactions.Transaction;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidSoundResource;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Use the {@link AddOrRepairSoundDialog#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AddOrRepairSoundDialog extends DialogFragment
        implements View.OnClickListener, DialogInterface.OnShowListener {

    static final String LOG_TAG = "KYB-"+AddOrRepairSoundDialog.class.getSimpleName();
    public static final int CHOOSE_SOUND_REQUEST_CODE = 1000;
    private static final String LOAD_SOUND_FRG = "load sound frg";
    private static final String ARG_IS_REPAIR = "for repair";
    private static final String ARG_REPAIR_KEY = "repair key";
    private static final String ARG_REPAIR_NAME = "repair name";

    private BeatsAndSoundsActivity mActivity;
    private AndroidResourceManager mResourceManager;

    private AlertDialog mDialog;
    private Button mUriBtn;
    private EditText mNameEditText;

    private ImportSoundFragment mImportSoundFragment;
    private ImageButton mTryItBtn;
    private boolean mIsRepair;
    private String mRepairSoundKey;
    private String mRepairSoundName;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance
     */
    public static AddOrRepairSoundDialog newInstance(boolean forRepair, String key, String name) {
        AddOrRepairSoundDialog fragment = new AddOrRepairSoundDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_REPAIR, forRepair);
        args.putString(ARG_REPAIR_KEY, key);
        args.putString(ARG_REPAIR_NAME, name);
        fragment.setArguments(args);
        return fragment;
    }

    public static AddOrRepairSoundDialog newInstance() {
        return newInstance(false, null, null);
    }

        public AddOrRepairSoundDialog() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIsRepair = getArguments().getBoolean(ARG_IS_REPAIR);
        mRepairSoundKey = getArguments().getString(ARG_REPAIR_KEY);
        mRepairSoundName = getArguments().getString(ARG_REPAIR_NAME);
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        mActivity = (BeatsAndSoundsActivity) activity;
        mResourceManager = ((KybApplication)mActivity.getApplication()).getResourceManager();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // may already be a loaded sound if this is orientation change
        FragmentManager fm = getFragmentManager();
        mImportSoundFragment = (ImportSoundFragment) fm.findFragmentByTag(LOAD_SOUND_FRG);

        if (mImportSoundFragment == null) {
            mImportSoundFragment = new ImportSoundFragment();
            fm.beginTransaction().add(mImportSoundFragment, LOAD_SOUND_FRG)
                    .commit();
        }
        else if (mImportSoundFragment.mFileName != null) {
            AndroidResourceManager.logd(LOG_TAG, "onCreateDialog: found loaded import sound, must be orientation change");
        }

        final ScrollView sv = (ScrollView) mActivity.getLayoutInflater().inflate(R.layout.add_sound_dialog, null);

        AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
                .setView(sv)
                .setTitle(mIsRepair ? R.string.repairSoundTitle : R.string.addNewSoundTitle)
                .setPositiveButton(R.string.ok_button, null)
                .setNegativeButton(R.string.cancel_button, null);

        mNameEditText = (EditText) sv.findViewById(R.id.sound_name);
        if (mIsRepair) {
            mNameEditText.setText(mRepairSoundName);
            mNameEditText.setEnabled(false);
            sv.findViewById(R.id.textLenIndicator).setVisibility(View.GONE);
        }
        else {
            InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mNameEditText, InputMethodManager.SHOW_FORCED);
            KybDialogFragment.trackTextLength(mNameEditText, (TextView)sv.findViewById(R.id.textLenIndicator), getResources().getInteger(R.integer.maxRhythmNameLen));
        }

        mUriBtn = (Button) sv.findViewById(R.id.uri_btn);
        mUriBtn.setOnClickListener(this);
        if (mImportSoundFragment.mFileName != null) {
            mUriBtn.setText(R.string.soundUriLoadedLabel);
        }
        else {
            mUriBtn.setText(R.string.soundUriHint);
        }

        mTryItBtn = (ImageButton) sv.findViewById(R.id.play_sound_btn);
        mTryItBtn.setEnabled(mImportSoundFragment.mFileName != null);
        mTryItBtn.setOnClickListener(this);

        mDialog = bld.create();
        mDialog.setCanceledOnTouchOutside(false);  // to prevent keyboard open when click outside
        mDialog.setOnShowListener(this);           // use onShow() to set this as click listener, this prevents auto-close
        return mDialog;
    }

    @Override
    public void onShow(DialogInterface dialogInterface) {
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this);
        mDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {

        // keypad showing only applies when not a repair dialog
        if (!mIsRepair) {
            InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (view.getId() == R.id.sound_name) {
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0); // show/hide
            }
            else {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0); // any other view, hide keypad
            }
        }

        if (view instanceof Button && getString(R.string.ok_button).equals(((Button)view).getText().toString())) {
            validateAndSaveChanges();
        }

        else if (view instanceof Button && getString(R.string.cancel_button).equals(((Button)view).getText().toString())) {
            clearLoadedSound(true); // also delete the file
            dismiss();
        }

        else if (view.getId() == R.id.uri_btn) { // launch intent to find it
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/x-wav");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            Intent ci = Intent.createChooser(intent, null);
            mActivity.startActivityForResult(ci, CHOOSE_SOUND_REQUEST_CODE);
        }

        else if (view.getId() == R.id.play_sound_btn) { // get the file that was written
            File f = mActivity.getFileStreamPath(mImportSoundFragment.mFileName);
            if (f == null) {
                AndroidResourceManager.loge(LOG_TAG, String.format("onClick: file not found ", mImportSoundFragment.mFileName));
            }
            else {
                final Uri uri = Uri.fromFile(f);
                try {
                    new AsyncTask<Void, Void, Void>() {

                        @Override
                        protected Void doInBackground(Void... params) {
                            MediaPlayer mp = MediaPlayer.create(mActivity, uri);
                            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                                @Override
                                public boolean onError(MediaPlayer mp, int what, int extra) {
                                    AndroidResourceManager.loge(LOG_TAG, "playSound: media player onError what="+what+" extra="+extra);
                                    return false;
                                }
                            });
                            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mp) {
                                    AndroidResourceManager.logd(LOG_TAG, "playSound: media player onCompletion");
                                    mp.reset();
                                    mp.release();
                                }
                            });
                            mp.start();
                            return null;
                        }

                    }.execute((Void)null);
                } catch (Exception e) {
                    AndroidResourceManager.loge(LOG_TAG, "playSound: media player", e);
                }
            }


        }
    }

    /**
     * Called onClick cancel btn, also when user hits back button via activity.onBackPressed()
     * @param deleteFile
     */
    public void clearLoadedSound(boolean deleteFile) {
        if (deleteFile && mImportSoundFragment.mFileName != null) {
            mActivity.deleteFile(mImportSoundFragment.mFileName);
            mImportSoundFragment.mFileName = null;
        }
        mTryItBtn.setEnabled(false);
        mUriBtn.setText(R.string.soundUriHint);
    }

    private void validateAndSaveChanges() {
        // validation on ok button

        final KybSQLiteHelper library = (KybSQLiteHelper) mResourceManager.getLibrary();
        final SoundsContentProvider sounds = (SoundsContentProvider) library.getSounds();

        // get values and changes
        final String newName = RhythmEncoder.sanitizeString(mResourceManager, mNameEditText.getText().toString(), RhythmEncoder.SANITIZE_STRING_REPLACEMENT_CHAR);

        if (newName.isEmpty()) {     // name cannot be null
            Toast.makeText(mActivity, R.string.enterUniqueName, Toast.LENGTH_SHORT).show();
        }
        else { // validate name is unique and make changes
            // db validation and changes in background thread, success or error is shown in ui thread
            mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                    new ParameterizedCallback(true) {
                        @Override
                        public Object runForResult() {

                            String excludeKey = mIsRepair ? mRepairSoundKey : "xx";
                            boolean exists = !mIsRepair && sounds.nameExists(newName, excludeKey);

                            if (exists) {
                                return getString(R.string.nameUsed);
                            }

                            if (mImportSoundFragment.mFileName == null) {
                                return getString(R.string.errorSelectFile);
                            }

                            // get the file and update the resource
                            File f = mActivity.getFileStreamPath(mImportSoundFragment.mFileName);

                            mImportSoundFragment.mSoundResource.setProposedSoundNameAndFileAndUriPath(newName, mImportSoundFragment.mFileName, f);

                            if (mIsRepair) { // no sense in repair being undoable
                                mResourceManager.startTransaction();
                                sounds.repairSound(mRepairSoundKey, mImportSoundFragment.mSoundResource);
                                Transaction.saveTransaction(mResourceManager, ListenerSupport.SOUND, sounds, library.getBeatTypes());
                            }
                            else {
                                new BeatTypesAndSoundsCommand.AddCustomSound(library, mImportSoundFragment.mSoundResource).execute();
                            }

                            // done, clear the fragment data in case of re-use
                            mImportSoundFragment.mFileName = null;
                            mImportSoundFragment.mSoundResource = null;

                            return null; // null means success as there's no error message to display
                        }
                    },
                    new ParameterizedCallback(false) {
                        @Override
                        public void run() {
                            if (mParam == null) { // no error message means success
                                dismiss();
                            } else { // invalid because the name exists
                                Toast.makeText(mActivity, (String) mParam, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
            );
        }
    }

    /**
     * Called from onActivityResult() when the user has successfully chosen
     * a sound file
     * @param data
     */
    public void passActivityResult(Intent data) {

        // open an input stream to grab the file into
        Uri uri = data.getData();

        if (uri != null) {
            try {
                clearLoadedSound(false);
                InputStream is = mActivity.getContentResolver().openInputStream(uri);
                mImportSoundFragment.loadSoundFromInputStream(mActivity, this, is, mResourceManager);
            }
            catch (FileNotFoundException e) {
                AndroidResourceManager.loge(LOG_TAG, "passActivityResult: file not found", e);
                Throwable t = e.getCause();
                if (t != null) {
                    AndroidResourceManager.loge(LOG_TAG, "passActivityResult: cause", e);
                }

                showErrorAlert(mActivity, getString(R.string.unexpectedErrorTitle), getString(e.getMessage().contains("authentication_failure")
                        ? R.string.open_chosen_sound_auth_error : R.string.open_chosen_sound_error));
            }
        }
        else {
            AndroidResourceManager.loge(LOG_TAG, "passActivityResult: no uri on data intent");
        }
    }

    private void notifySoundLoaded() {
        mTryItBtn.setEnabled(true);
        mUriBtn.setText(R.string.soundUriLoadedLabel);
    }

    private static void showErrorAlert(Activity activity, String title, String msg) {
        AlertDialog.Builder bld = new AlertDialog.Builder(activity)
                .setMessage(msg)
                .setTitle(title);

        bld.create().show();
    }

    public static class ImportSoundFragment extends Fragment {

        private String mFileName;
        private AndroidSoundResource mSoundResource;

        public ImportSoundFragment() {}

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }

        void loadSoundFromInputStream(final Activity activity, final AddOrRepairSoundDialog dlg, final InputStream is, final AndroidResourceManager resourceManager) {
            resourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                    new ParameterizedCallback(true) {
                        @Override
                        public Object runForResult() {
                            try {
                                // delete any previously loaded file that wasn't saved
                                if (mFileName != null) {
                                    activity.deleteFile(mFileName);
                                    mFileName = null;
                                }

                                mSoundResource = (AndroidSoundResource) resourceManager.loadAndTestSoundFromInputStream(activity, is, false, false);

                                // ready to write out the file, need a name, use library key
                                mFileName = resourceManager.getLibrary().getNextKey();

                                // write out the file
                                FileOutputStream fos = activity.openFileOutput(mFileName, Context.MODE_PRIVATE);
                                fos.write(mSoundResource.getBytes());
                                fos.close();

                                return null;
                            }
                            catch (IOException e) {
                                AndroidResourceManager.loge(LOG_TAG, "passActivityResult: IO error reading data", e);
                                return new String[] { activity.getString(R.string.unexpectedErrorTitle), activity.getString(R.string.open_chosen_sound_error) };
                            }
                            catch (IllegalArgumentException e) {
                                return new String[] { activity.getString(R.string.sound_file_compat_error_title), e.getMessage() };
                            }
                            finally {
                                if (is != null) {
                                    try {
                                        is.close();
                                    }
                                    catch (Exception e) {}
                                }
                            }
                        }
                    }, new ParameterizedCallback(false) {
                        @Override
                        public void run() {
                            if (mParam instanceof String[]) {
                                String[] alerts = (String[])mParam;
                                showErrorAlert(activity, alerts[0], alerts[1]);
                            }
                            else {
                                dlg.notifySoundLoaded();
                            }
                        }
                    });
        }
    }
}
