package gt.hack.nfc.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import gt.hack.nfc.R
import gt.hack.nfc.util.NetworkStateReceiver

class ConnectivityFragment : Fragment(), NetworkStateReceiver.NetworkStateReceiverListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: hide/unhide menu bar?
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_connectivity, container, false)
    }

    override fun networkAvailable() {
        fragmentManager?.beginTransaction()?.replace(R.id.content_frame, CheckinFragment())?.commit()
    }

    override fun networkUnavailable() {
        fragmentManager?.beginTransaction()?.replace(R.id.content_frame, this)?.commit()
    }

}