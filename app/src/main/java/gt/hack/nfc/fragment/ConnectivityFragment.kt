package gt.hack.nfc.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import gt.hack.nfc.util.NetworkStateReceiver
import android.content.Intent
import android.provider.Settings
import android.widget.Button
import gt.hack.nfc.R
import gt.hack.nfc.util.Util
import kotlinx.android.synthetic.main.fragment_connectivity.*


class ConnectivityFragment : Fragment(), NetworkStateReceiver.NetworkStateReceiverListener, View.OnClickListener {

    override fun onResume() {
        super.onResume()

        connect_button.setOnClickListener(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_connectivity, container, false)
    }

    override fun networkAvailable() {
        val transaction = fragmentManager?.beginTransaction()?.replace(R.id.content_frame, CheckinFragment())

        if (transaction == null) {
            Util.networkState = NetworkStateReceiver.NetworkState.CONNECTED
        } else {
            transaction.commit()
        }
    }

    override fun networkUnavailable() {
        val transaction = fragmentManager?.beginTransaction()?.replace(R.id.content_frame, this)

        if (transaction == null) {
            Util.networkState = NetworkStateReceiver.NetworkState.DISCONNECTED
        } else {
            transaction.commit()
        }
    }

    override fun onClick(v: View?) {
        if (v is Button && v.id == R.id.connect_button) {
            val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
            activity?.startActivityForResult(settingsIntent, 9003)
        }
    }

}