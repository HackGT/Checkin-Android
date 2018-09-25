package gt.hack.nfc.util

import android.app.ProgressDialog
import android.content.SharedPreferences
import android.os.AsyncTask
import android.support.v4.app.Fragment

import com.apollographql.apollo.exception.ApolloException

import java.util.ArrayList
import java.util.HashMap

import gt.hack.nfc.R
import gt.hack.nfc.fragment.TagFragment
import kotlinx.coroutines.experimental.runBlocking


/**
 * AsyncTask for checking a user into a tag
 */

class TagAsyncTask(private val preferences: SharedPreferences, private val fragment: Fragment) : AsyncTask<String, String, ArrayList<String>>() {
    private var dialog: ProgressDialog? = null
    override fun onProgressUpdate(vararg values: String) {
        super.onProgressUpdate(*values)
        this.dialog = ProgressDialog(fragment.activity)
        this.dialog!!.setMessage("Getting Tags...")
        this.dialog!!.show()
    }

    override fun doInBackground(vararg strings: String): ArrayList<String>? {
        this.publishProgress("Show dialog")
        try {
            return runBlocking { API.getTags(preferences) }
        } catch (e: ApolloException) {
            e.printStackTrace()
            return null
        }

    }

    override fun onPostExecute(stringTagFragmentHashMap: ArrayList<String>) {
        super.onPostExecute(stringTagFragmentHashMap)
        dialog!!.dismiss()
    }
}
