package gt.hack.nfc.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import android.view.View
import android.widget.*
import gt.hack.nfc.R
import gt.hack.nfc.fragment.CheckinFlowFragment

class NFCHandler: View.OnClickListener {

    var nfcIsLoaded = false
    var callback: NfcAdapter.ReaderCallback? = null
    var activity: Activity? = null
    var context: Context? = null

    // given UI elements, will configure the UI and run the required functions for hiding/showing errors and buttons
    fun loadNFC(info: TextView, progressBar: ProgressBar, warningIcon: ImageView, enableButton: Button) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)

        if (nfcAdapter == null) {

            info.text = context?.getString(R.string.nfc_unsupported_device)

            progressBar.visibility = View.GONE
            warningIcon.visibility = View.VISIBLE
            enableButton.visibility = View.GONE

        } else if (!nfcAdapter.isEnabled) {

            info.text = context?.getString(R.string.nfc_disabled)

            progressBar.visibility = View.GONE
            warningIcon.visibility = View.VISIBLE
            enableButton.visibility = View.VISIBLE

            enableButton.setOnClickListener(this)

        } else {

            info.text = context?.getString(R.string.nfc_ready)

            progressBar.visibility = View.VISIBLE
            warningIcon.visibility = View.GONE
            enableButton.visibility = View.GONE

            if (nfcIsLoaded) {
                return
            }

            nfcIsLoaded = true

            nfcAdapter.enableReaderMode(activity, callback, CheckinFlowFragment.READER_FLAGS, null)
        }
    }

    override fun onClick(view: View?) {
        if (view == null) {
            return
        }

        if (view.id == R.id.enable_nfc_button) {
            context?.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }
    }

}
