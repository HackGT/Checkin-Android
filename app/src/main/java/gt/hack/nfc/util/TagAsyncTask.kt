package gt.hack.nfc.util

import android.app.ProgressDialog
import android.content.SharedPreferences
import android.os.AsyncTask
import com.apollographql.apollo.exception.ApolloException
import kotlinx.coroutines.runBlocking
import java.util.*


/**
 * AsyncTask for checking a user into a tag
 */

class TagAsyncTask(private val preferences: SharedPreferences, private val fragment: androidx.fragment.app.Fragment) : AsyncTask<String, String, ArrayList<String>>() {
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
