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

public class TagAsyncTask extends AsyncTask<String, String, ArrayList<String>> {
    private ProgressDialog dialog;
    private SharedPreferences preferences;
    private Fragment fragment;

    public TagAsyncTask(SharedPreferences preferences, Fragment fragment) {
        this.preferences = preferences;
        this.fragment = fragment;
    }
    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        this.dialog = new ProgressDialog(fragment.getActivity());
        this.dialog.setMessage("Getting Tags...");
        this.dialog.show();
    }

    @Override
    protected ArrayList<String> doInBackground(String... strings) {
        this.publishProgress("Show dialog");
        try {
            return API.getTags(preferences);
        } catch (ApolloException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onPostExecute(ArrayList<String> stringTagFragmentHashMap) {
        super.onPostExecute(stringTagFragmentHashMap);
        dialog.dismiss();
    }
}
