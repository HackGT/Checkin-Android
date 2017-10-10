package gt.hack.nfc.util;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v4.app.Fragment;

import com.apollographql.apollo.exception.ApolloException;

import java.util.ArrayList;
import java.util.HashMap;

import gt.hack.nfc.R;
import gt.hack.nfc.fragment.TagFragment;


/**
 * AsyncTask for checking a user into a tag
 */

public class CheckInAsyncTask extends AsyncTask<String, String, HashMap<String,TagFragment>> {
    private ProgressDialog dialog;
    private SharedPreferences preferences;
    private Fragment fragment;

    public CheckInAsyncTask(SharedPreferences preferences, Fragment fragment) {
        this.preferences = preferences;
        this.fragment = fragment;
    }
    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        this.dialog = new ProgressDialog(fragment.getActivity());
        this.dialog.setMessage(fragment.
                getActivity().getResources().getString(R.string.tag_checkin));
        this.dialog.show();
    }

    @Override
    protected HashMap<String,TagFragment> doInBackground(String... strings) {
        this.publishProgress("Show dialog");
        String id = strings[0];
        String tag = strings[1];
        try {
            return API.checkInTag(preferences, id, tag);
        } catch (ApolloException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onPostExecute(HashMap<String, TagFragment> stringTagFragmentHashMap) {
        super.onPostExecute(stringTagFragmentHashMap);
        dialog.dismiss();
    }
}
