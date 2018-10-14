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

class CheckInAsyncTask(private val preferences: SharedPreferences, private val fragment: Fragment) : AsyncTask<String, String, HashMap<String, TagFragment>>() {
    private var dialog: ProgressDialog? = null
    override fun onProgressUpdate(vararg values: String) {
        super.onProgressUpdate(*values)
        this.dialog = ProgressDialog(fragment.activity)
        this.dialog!!.setMessage(fragment.activity!!.resources.getString(R.string.tag_checkin))
        this.dialog!!.show()
    }

    override fun doInBackground(vararg strings: String): HashMap<String, TagFragment>? {
        this.publishProgress("Show dialog")
        val id = strings[0]
        val tag = strings[1]
        try {
            return runBlocking { API.checkInTag(preferences, id, tag) }
        } catch (e: ApolloException) {
            e.printStackTrace()
            return null
        }

    }

    override fun onPostExecute(stringTagFragmentHashMap: HashMap<String, TagFragment>) {
        super.onPostExecute(stringTagFragmentHashMap)
        dialog!!.dismiss()
    }
}
