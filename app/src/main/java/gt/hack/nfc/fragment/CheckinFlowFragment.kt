package gt.hack.nfc.fragment

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import gt.hack.nfc.BuildConfig
import gt.hack.nfc.R
import gt.hack.nfc.util.API
import gt.hack.nfc.util.NFCHandler
import gt.hack.nfc.util.Util
import kotlinx.android.synthetic.main.fragment_checkin_confirm.*
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.ExecutionException

class CheckinFlowFragment : androidx.fragment.app.Fragment() {
  private var uuid: String? = null
  private var name: String? = null
  private var email: String? = null
  private var school: String? = null
  private var branch: String? = null
  private var confirmBranch: String? = null
  private var alreadyCheckedIn = false
  private var wroteBadge = false
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
    if (BuildConfig.DEBUG) {
      unlocked_tag_dev_version_icon.show()
      unlocked_tag_dev_version_text.show()
      lock_tag_notice_icon.hide()
      lock_tag_notice_text.hide()
      no_tag_lock_checkbox.hide()
    } else {
      no_tag_lock_checkbox.setOnCheckedChangeListener { _, checked ->
        if (checked) {
          lock_tag_notice_icon.hide()
          lock_tag_notice_text.hide()
          unlocked_tag_warning_icon.show()
          unlocked_tag_warning_text.show()
        } else {
          lock_tag_notice_icon.show()
          lock_tag_notice_text.show()
          unlocked_tag_warning_icon.hide()
          unlocked_tag_warning_text.hide()
        }
      }
    }

    hacker_checkin_name.text = name
    hacker_checkin_email.text = email

    if (school != null) {
      hacker_checkin_school.text = school
    }

    if (branch != null) {
      hacker_checkin_type.text = branch
    }

    if (confirmBranch != null) {
      hacker_confirm_type.text = confirmBranch
    }

    confirmCheckin.setOnClickListener {
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

  private fun shouldLockTag(keepUnlocked: Boolean, isDebug: Boolean): Boolean {
    if (isDebug || keepUnlocked) {
      return false
    }

    return true

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
              "https://info.hack.gt/?user=" + uuid)
          val ndefMessage = NdefMessage(
              arrayOf(uriRecord))

          try {
            val lockTag = shouldLockTag(no_tag_lock_checkbox.isChecked, BuildConfig.DEBUG)
            ndef.writeNdefMessage(ndefMessage)
            Log.i(TAG, "Should tag be locked? ${lockTag}")

            if (ndef.canMakeReadOnly()) {
              if (lockTag) {
                ndef.makeReadOnly()
              } else if (BuildConfig.DEBUG) {
                Util.makeSnackbar(activity!!.findViewById(R.id.content_frame), R.string.permanent_badge_locking_option_disabled_debug_build, Snackbar.LENGTH_SHORT).show()
              } else {
                Util.makeSnackbar(activity!!.findViewById(R.id.content_frame), R.string.permanent_badge_locking_option_disabled, Snackbar.LENGTH_SHORT).show()
              }
            } else if (!ndef.canMakeReadOnly() && lockTag) {
              Util.makeSnackbar(activity!!.findViewById(R.id.content_frame), R.string.unlockable_tag, Snackbar.LENGTH_SHORT).show()
            }

            activity!!.runOnUiThread {
              wait_for_badge_tap.hide()
              nfcInstructions.hide()
              badgeWritten.show()
              confirmCheckin.show()
              no_tag_lock_checkbox.hide()
            }
            wroteBadge = true
          } catch (e: IOException) {
            Util.makeSnackbar(activity!!.findViewById(R.id.content_frame), R.string.badge_write_error, Snackbar.LENGTH_LONG).show()
          }


        } else if (!ndef.isWritable) {
          // Tag already locked or unwritable NFC device like a Buzzcard was tapped
          Util.makeSnackbar(activity!!.findViewById(R.id.content_frame), R.string.unwritable_tag, Snackbar.LENGTH_LONG).show()
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
      var school = Util.getValueOfQuestion(user.questions, "school")
      if (school.isNullOrBlank()) {
        school = Util.getValueOfQuestion(user.questions, "university")
      }
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

  private fun View.show() {
    this.visibility = View.VISIBLE
  }

  private fun View.hide() {
    this.visibility = View.GONE
  }
}
