package gt.hack.nfc.fragment

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.apollographql.apollo.exception.ApolloException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.MultiProcessor
import com.google.android.gms.vision.Tracker
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import gt.hack.nfc.R
import gt.hack.nfc.UserGetQuery
import gt.hack.nfc.util.API
import gt.hack.nfc.util.CameraSourcePreview
import gt.hack.nfc.util.NFCHandler
import kotlinx.coroutines.runBlocking
import java.io.IOException

class CheckinFragment : androidx.fragment.app.Fragment() {

    private var mCameraSource: CameraSource? = null
    private var mPreview: CameraSourcePreview? = null
    private var foundTag: Boolean = false
    private var nfcHandler = NFCHandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mPreview = activity!!.findViewById(R.id.preview)
        val rc = ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA)
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource()
        } else {
            requestCameraPermission()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_checkin, container, false)
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private fun createCameraSource() {


        val context = activity!!.applicationContext

        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
        val barcodeDetector = BarcodeDetector.Builder(context).build()
        barcodeDetector.setProcessor(
                MultiProcessor.Builder(BarcodeTrackerFactory()).build())


        if (!barcodeDetector.isOperational) {

            Log.w(TAG, "Detector dependencies are not yet available.")

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            val lowstorageFilter = IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW)
            val hasLowStorage = activity!!.registerReceiver(null, lowstorageFilter) != null

            if (hasLowStorage) {
                Toast.makeText(activity, R.string.low_storage_error, Toast.LENGTH_LONG).show()
                Log.w(TAG, getString(R.string.low_storage_error))
            }
        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.
        mCameraSource = CameraSource.Builder(activity!!.applicationContext, barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1280, 720)
                .setRequestedFps(60.0f)
                .setAutoFocusEnabled(true)
                .build()
    }

    /**
     * Restarts the camera.
     */
    override fun onResume() {
        super.onResume()
        foundTag = false
        startCameraSource()
    }

    /**
     * Stops the camera.
     */
    override fun onPause() {
        super.onPause()
        mPreview!!.stop()
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (mCameraSource != null) {
            mCameraSource!!.release()
        }
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    @Throws(SecurityException::class)
    private fun startCameraSource() {

        // check that the device has play services available.
        val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                activity!!.applicationContext)
        if (code != ConnectionResult.SUCCESS) {
            val dlg = GoogleApiAvailability.getInstance().getErrorDialog(activity, code, RC_HANDLE_GMS)
            dlg.show()
        }

        if (mCameraSource != null) {
            try {
                mPreview!!.start(mCameraSource)
            } catch (e: IOException) {
                Log.e(TAG, "Unable to start camera source.", e)
                mCameraSource!!.release()
                mCameraSource = null
            }

        }
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private fun requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission")

        val permissions = arrayOf(android.Manifest.permission.CAMERA)

        requestPermissions(permissions, RC_HANDLE_CAMERA_PERM)
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on [.requestPermissions].
     *
     *
     * **Note:** It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     *
     *
     * @param requestCode  The request code passed in [.requestPermissions].
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     * which is either [PackageManager.PERMISSION_GRANTED]
     * or [PackageManager.PERMISSION_DENIED]. Never null.
     * @see .requestPermissions
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: $requestCode")
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }

        if (grantResults.size != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source")
            createCameraSource()
            startCameraSource()
            return
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.size +
                " Result code = " + if (grantResults.size > 0) grantResults[0] else "(empty)")
    }

    inner class BarcodeTrackerFactory : MultiProcessor.Factory<Barcode> {

        override fun create(barcode: Barcode): Tracker<Barcode> {
            return UserTracker()
        }
    }

    internal inner class UserTracker : Tracker<Barcode>() {

        /**
         * Start tracking the detected item instance within the item overlay.
         */
        override fun onNewItem(id: Int, item: Barcode?) {
            detectAndLaunchCheckin(item!!.rawValue)
        }


        private fun detectAndLaunchCheckin(value: String) {
            if (value.contains("user:") && !foundTag) {
                foundTag = true
                val id = value.split("user:".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                val toneGen1 = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                GetUser(this@CheckinFragment).execute(id)
            }
        }
    }

    internal inner class GetUser(private val fragment: CheckinFragment) : AsyncTask<String, String, UserGetQuery.User>() {

        private var dialog: ProgressDialog? = null

        override fun onProgressUpdate(vararg values: String) {
            super.onProgressUpdate(*values)
            this.dialog = ProgressDialog(fragment.activity)
            this.dialog!!.setMessage("Searching for user ...")
            this.dialog!!.show()
        }

        override fun doInBackground(vararg strings: String): UserGetQuery.User? {
            this.publishProgress("Show dialog")
            try {
                return runBlocking { API.getUserById(
                        PreferenceManager.getDefaultSharedPreferences(activity), strings[0]) }

            } catch (e: ApolloException) {
                e.printStackTrace()
            }

            return null
        }

        override fun onPostExecute(userData: UserGetQuery.User?) {
            super.onPostExecute(userData)
            this.dialog!!.dismiss()
            if (userData != null &&
                    userData.user().fragments().userFragment().accepted &&
                    userData.user().fragments().userFragment().confirmed) {
                val user = userData.user().fragments().userFragment()
                val tags = API.parseTags(userData.tags())
                if (tags!!.contains("hackgt") && tags["hackgt"]!!.checked_in()) {
                    Toast.makeText(context, "User already has badge issued! Please find a check-in admin (Evan/Ehsan/Ryan).",
                            Toast.LENGTH_LONG).show()
                    val toneGen1 = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    toneGen1.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500)
                    foundTag = false
                } else {
                    val fragment2 = CheckinFlowFragment.newInstance(user)
                    val fragmentManager = fragmentManager
                    val transaction = fragmentManager!!.beginTransaction()
                    transaction.setCustomAnimations(R.anim.enter,
                            R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    transaction.addToBackStack(null)
                    transaction.replace(R.id.content_frame, fragment2)
                    foundTag = false
                    transaction.commit()
                }
            } else {
                Toast.makeText(context, "User not found! Please proceed to help desk.",
                        Toast.LENGTH_LONG).show()
                val toneGen1 = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                toneGen1.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500)
                foundTag = false
            }
        }
    }

    companion object {
        private val TAG = "checkin"
        private val RC_HANDLE_GMS = 9001
        private val RC_HANDLE_CAMERA_PERM = 2
    }
}
