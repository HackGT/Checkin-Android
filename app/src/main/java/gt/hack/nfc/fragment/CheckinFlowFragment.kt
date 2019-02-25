package gt.hack.nfc.fragment


import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.widget.AppCompatButton
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import gt.hack.nfc.BuildConfig
import gt.hack.nfc.R
import gt.hack.nfc.util.API
import gt.hack.nfc.util.NFCHandler
import gt.hack.nfc.util.Util
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.ExecutionException

class CheckinFlowFragment : Fragment() {
  private var uuid: String? = null
  private var name: String? = null
  private var email: String? = null
  private var school: String? = null
  private var branch: String? = null
  private var confirmBranch: String? = null
  private var alreadyCheckedIn = false
  private var wroteBadge = false
  private var confirmButton: AppCompatButton? = null
  private val nfcHandler = NFCHandler()
  private val TAG = "checkin2/CHECKIN_FLOW"

  override fun onCreateView(inflater: LayoutInflater,
                            container: ViewGroup?, savedInstanceState: Bundle?): View? {
    super.onCreateView(inflater, container, savedInstanceState)
    val bundle = this.arguments
    uuid = bundle!!.getString("id")
    name = bundle.getString("name")
    email = bundle.getString("email")
    school = bundle.getString("school")
    branch = bundle.getString("branch")
    confirmBranch = bundle.getString("confirmBranch")

    return inflater.inflate(R.layout.fragment_checkin_confirm, container, false)
  }

  override fun onResume() {
    super.onResume()
    val progressBar = activity!!.findViewById<ProgressBar>(R.id.wait_for_badge_tap)

    val nameView = activity!!.findViewById<TextView>(R.id.hacker_checkin_name)
    nameView.text = name

    val emailView = activity!!.findViewById<TextView>(R.id.hacker_checkin_email)
    emailView.text = email

    val schoolView = view!!.findViewById<TextView>(R.id.hacker_checkin_school)
    if (school != null) {
      schoolView.text = school
    }

    val branchView = view!!.findViewById<TextView>(R.id.hacker_checkin_type)
    if (branch != null) {
      branchView.text = branch
    }

    val confirmBranchView = view!!.findViewById<TextView>(R.id.hacker_confirm_type)
    if (confirmBranch != null) {
      confirmBranchView.text = confirmBranch
    }

    confirmButton = activity!!.findViewById(R.id.confirmCheckin)
    confirmButton!!.setOnClickListener {
      try {
          val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
          val checkinUuid = uuid
          val tagName = "hackgt"
          if (checkinUuid != null) {
            val checkinResult = runBlocking {
              API.checkInTag(preferences, checkinUuid, tagName,
                  true)
            }

            if (checkinResult != null) {
              var checkinDetails: TagFragment? = null
              val checkinTagInfo = checkinResult.tags().findLast { it.fragments().tagFragment().tag.name.equals(tagName) }
              if (checkinTagInfo != null) {
                checkinDetails = checkinTagInfo.fragments().tagFragment()
              }
              val userInfo = checkinResult.user().fragments().userFragment()

              if (checkinDetails != null) {
                if (userInfo.accepted && userInfo.confirmed) {
                  if (checkinDetails.checkin_success) {
                    fragmentManager!!.popBackStack()
                    Util.makeSnackbar(activity!!.findViewById(R.id.content_frame),
                        R.string.checkin_success, Snackbar.LENGTH_SHORT).show()
                  } else {
                    alreadyCheckedIn = true
                    Util.makeSnackbar(activity!!.findViewById(R.id.content_frame),
                        R.string.user_already_checked_in, Snackbar.LENGTH_SHORT).show()
                  }
                } else {
                  Log.i(TAG, "user (uuid: " + checkinUuid + ") not acccepted and confirmed")
                  Util.makeSnackbar(activity!!.findViewById(R.id.content_frame),
                      R.string.user_not_accepted_and_confirmed, Snackbar.LENGTH_SHORT)
                      .show()
                }

              } else {
                Log.i(TAG, "checkin API returned null")
                Util.makeSnackbar(activity!!.findViewById(R.id.content_frame),
                    R.string.checkin_error, Snackbar.LENGTH_SHORT).show()
              }
            }
          }
        } catch (e: InterruptedException) {
          e.printStackTrace()
        Util.makeSnackbar(activity!!.findViewById(R.id.content_frame),
              R.string.checkin_error, Snackbar.LENGTH_SHORT).show()
      } catch (e: ExecutionException) {
          e.printStackTrace()
        Util.makeSnackbar(activity!!.findViewById(R.id.content_frame),
              R.string.checkin_error, Snackbar.LENGTH_SHORT).show()
      }
    }

    // load nfc loading/errors
    if (view != null) {
      loadNFC(view!!)
    }

  }

  private fun loadNFC(view: View) {
    val nfcInfo = view.findViewById<TextView>(R.id.nfcInstructions)
    val progressBar = view.findViewById<ProgressBar>(R.id.wait_for_badge_tap)
    val warningIcon = view.findViewById<ImageView>(R.id.nfc_error)
    val nfcEnableButton = view.findViewById<Button>(R.id.enable_nfc_button)

    nfcHandler.activity = activity
    nfcHandler.context = context
    nfcHandler.callback = NfcAdapter.ReaderCallback { tag ->
      val ndef = Ndef.get(tag)
      Log.d("NFC", tag.toString())
      try {
        ndef.connect()
        if (ndef.isWritable && !wroteBadge) {

          val type = "badge"

          val uriRecord = NdefRecord.createUri(
              "https://live.hack.gt/?user=" + uuid)
          val ndefMessage = NdefMessage(
              arrayOf(uriRecord))

          try {
            ndef.writeNdefMessage(ndefMessage)

            if (ndef.canMakeReadOnly() && Util.nfcLockEnabled && !BuildConfig.DEBUG) {
              ndef.makeReadOnly()
            } else if (Util.nfcLockEnabled && BuildConfig.DEBUG) {
              Util.makeSnackbar(activity!!.findViewById(R.id.content_frame), R.string.permanent_badge_locking_option_disabled_debug_build, Snackbar.LENGTH_SHORT).show()
            } else if (!Util.nfcLockEnabled) {
              Util.makeSnackbar(activity!!.findViewById(R.id.content_frame), R.string.permanent_badge_locking_option_disabled, Snackbar.LENGTH_SHORT).show()
            } else {
              Util.makeSnackbar(activity!!.findViewById(R.id.content_frame), R.string.unlockable_tag, Snackbar.LENGTH_SHORT).show()
            }

            activity!!.runOnUiThread {
              progressBar.visibility = View.GONE
              val check = activity!!
                  .findViewById<ImageView>(R.id.badgeWritten)
              check.visibility = View.VISIBLE
              confirmButton!!.visibility = View.VISIBLE
            }
            wroteBadge = true
          } catch (e: IOException) {
            Util.makeSnackbar(activity!!.findViewById(R.id.content_frame), R.string.badge_write_error, Snackbar.LENGTH_LONG).show()
          }


        } else if (!ndef.isWritable) {
          // Tag already locked or unwritable NFC device like a Buzzcard was tapped
          Util.makeSnackbar(activity!!.findViewById(R.id.content_frame), R.string.unwritable_tag, Snackbar.LENGTH_SHORT).show()
        }
      } catch (e: IOException) {
        e.printStackTrace()
      } catch (e: FormatException) {
        e.printStackTrace()
      } finally {
        try {
          ndef.close()
        } catch (e: IOException) {
          e.printStackTrace()
        }

      }
    }

    nfcHandler.loadNFC(nfcInfo, progressBar, warningIcon, nfcEnableButton)
  }

  companion object {
    var READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A

    fun newInstance(user: UserFragment): CheckinFlowFragment {
      val f = CheckinFlowFragment()
      // Supply index input as an argument.
      val args = Bundle()
      args.putString("id", user.id)
      args.putString("name", user.name)
      args.putString("email", user.email)
      val school = Util.getValueOfQuestion(user.questions, "school")
      args.putString("school", school)

      if (user.application != null) {
        args.putString("branch", user.application.type)
      }

      if (user.confirmation != null) {
        args.putString("confirmBranch", user.confirmation.type)
      }

      f.arguments = args

      return f
    }
  }
}
