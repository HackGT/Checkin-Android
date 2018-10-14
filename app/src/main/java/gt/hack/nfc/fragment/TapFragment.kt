package gt.hack.nfc.fragment

import java.util.*

import android.app.AlertDialog
import android.content.DialogInterface.OnClickListener
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.nfc.NdefRecord
import android.nfc.tech.Ndef
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Handler

import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.apollographql.apollo.exception.ApolloNetworkException
import gt.hack.nfc.R
import gt.hack.nfc.util.API
import gt.hack.nfc.util.Util

import kotlinx.android.synthetic.main.fragment_tap.*
import kotlinx.coroutines.experimental.*
import kotlin.collections.ArrayList


class TapFragment : Fragment() {

  val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A
  val TAG = "CHECKIN/TAP_FRAGMENT"
  var waitingForTag = true

  companion object {
    fun newInstance(): TapFragment {
      return TapFragment()
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.fragment_tap, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val uuidRegex = Regex("[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}")
    super.onViewCreated(view, savedInstanceState)
    Log.i("checkin-tap", "inside onviewcreated")
    val tagSelect: AutoCompleteTextView = checkin_tag
    val prevTag = tagSelect.text
    tagSelect.setText("Updating tags list...")
    tagSelect.isEnabled = false

    val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
    var error = false
    val allTags = runBlocking {
      try {
        API.getTags(preferences)
      } catch (e: ApolloNetworkException) { // Working way to detect errors inside the network
        // operation call and then make the UI do something in response
        error = true
      }
    } // TODO: this seems to delay showing this screen, so check that out
    Log.i(TAG, "Contents of allTags: " + allTags)

    if (error) {
      tagSelect.setText("No internet, couldn't fetch tags")
    } else {
      tagSelect.text = prevTag
      tagSelect.isEnabled = true

      val autocomplete = ArrayAdapter(context, android.R.layout.simple_expandable_list_item_1, allTags as ArrayList<String>)
      // the cast to ArrayList is kinda icky but so long as the API response is OK, it should work
      tagSelect.threshold = 0
      tagSelect.setAdapter(autocomplete)
      tagSelect.setOnFocusChangeListener { v, hasFocus ->
        if (!hasFocus) {
          Util.hideSoftKeyboard(view, context)
        }
      }

      check_in_out_select.setOnCheckedChangeListener { _, isChecked ->
        check_in_out_select.text = when (isChecked) {
          true -> getString(R.string.switch_check_in)
          false -> getString(R.string.switch_check_out)
        }
      }

      val nfc = NfcAdapter.getDefaultAdapter(activity)

      nfc.enableReaderMode(activity, { tag: Tag ->
        if (waitingForTag) {
          waitingForTag = false
          // get the latest read tag
          val ndef = Ndef.get(tag)
          val record: NdefRecord? = try {
            ndef.connect()
            val message = ndef.ndefMessage
            message.records[0]
          } catch (e: Throwable) {
            Log.i(TAG, "" + e.printStackTrace())
            null
          } finally {
            try {
              ndef.close()
            } catch (e: Throwable) {
              e.printStackTrace()
            }
          }

          val id: Uri? = record?.toUri()
          Log.i(TAG, id.toString())
          if (id?.host == "live.hack.gt" && uuidRegex.containsMatchIn(id.getQueryParameter("user").orEmpty())) {
            val uuid = id.getQueryParameter("user").orEmpty()
            Log.i(TAG, "this valid id is " + uuid)

            val tagName = tagSelect.text.trim().toString()
            val doCheckIn = check_in_out_select.isChecked
            // check in/out the user
            // TODO: we should run these concurrently to save time
//          val job2 = launch {
//            try {
//              val userInfo = async { API.getUserById(preferences, uuid) }
//              Log.i(TAG, "async tags" + userInfo.await())
//            } catch (exception: Exception) {
//              Log.e(TAG, "Problem in coroutine")
//            }
//          }
//          Log.i(TAG, "past job2")
            val userInfo = runBlocking { API.getUserById(preferences, uuid)!!.user().fragments().userFragment() }
            val currentTags = runBlocking { API.getTagsForUser(preferences, uuid) }
            val newTags = when (doCheckIn) {
              true -> runBlocking { API.checkInTag(preferences, uuid, tagName) }
              else -> runBlocking { API.checkOutTag(preferences, uuid, tagName) }
            }

            val checkInData = CheckInData(userInfo, currentTags, newTags!!)

            Log.i(TAG, "hi")
            drawCheckInFinish(checkInData, tagName)

            Log.i(TAG, checkInData.userInfo.toString())
            Log.i(TAG, checkInData.currentTags.toString())
            Log.i(TAG, checkInData.newTags.toString())
          } else {
            if (id == null) {
              Log.i(TAG, "this tag's data is null: " + id)
              displayMessageAndReset(false, getString(R.string.badge_data_null), 5000)
              activity?.runOnUiThread {
                showAlert("Invalid user on badge", R.string.badge_data_null)
              }
            } else {
              Log.i(TAG, "this tag's data is formatted incorrectly: " + id)
              activity?.runOnUiThread {
                showAlert("Invalid user on badge", R.string.invalid_badge_id)
              }

            }

            val toneGen1 = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen1.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500)

            waitingForTag = true
          }

        }

      } , READER_FLAGS, null)

    }

  }


  fun drawCheckInFinish(checkInData: CheckInData, tagName: String) {
    val waitingForBadge = wait_for_badge_tap
    val badgeTapped = badge_tapped
    val userName = track_name
    val userBranch = track_type
    val userShirtSize = track_tshirt_size
    val userDietaryRestrictions = track_dietary_restrictions

    val userInfo = checkInData.userInfo
    var userShirtSizeVal: String? = ""
    var userDietaryRestrictionsVal: String? = ""
    var delayTime: Long = 1000


    if (userInfo != null) {

      userInfo.questions.forEach { question: UserFragment.Question? ->
        if (question?.name.equals("tshirt-size")) {
          userShirtSizeVal = question?.value
        } else if (question?.name.equals("dietary-restrictions")) {
          userDietaryRestrictionsVal = question?.value
        }
      }

      Log.i(TAG, ""+ checkInData.currentTags)
      var prevTagState: Boolean? = null
      var newTagState: Boolean? = null
      var unseenTag = false
      if (checkInData.currentTags!!.get(tagName) != null) {
        prevTagState = checkInData.currentTags.get(tagName)!!.checked_in
        newTagState = checkInData.newTags.get(tagName)!!.checked_in
      } else {
        unseenTag = true
      }


      Log.i(TAG, "prevTagState: " + prevTagState)
      Log.i(TAG, "newTagState: " + newTagState)

      val validOperation = (prevTagState != newTagState && !unseenTag)
                            || (newTagState == null && check_in_out_select.isChecked)

      activity?.runOnUiThread {
        userName.text = userInfo.name
        if (userInfo.application != null) {
          userBranch.text = userInfo.application.type
        }

        userShirtSize.text = userShirtSizeVal
        userDietaryRestrictions.text = userDietaryRestrictionsVal

        waitingForBadge.visibility = View.GONE
      }

      if (validOperation) {
        displayMessageAndReset(true, "", 1000)
      } else {
        if (prevTagState != null && prevTagState) { // indicates checkin/out state
         displayMessageAndReset(false, getString(R.string.user_already_checked_in), 5000)
        } else if (newTagState == null && !check_in_out_select.isChecked) {
          displayMessageAndReset(false, getString(R.string.cannot_checkout_not_checked_in), 5000)
        } else {
          displayMessageAndReset(false, getString(R.string.user_already_checked_out), 5000)
        }
      }


    } else { // checkInData is null, ie invalid user
        displayMessageAndReset(false, getString(R.string.invalid_badge_id),5000)
      }
    }

  fun displayMessageAndReset(validTag: Boolean, message: String, duration: Long ) {
    activity?.runOnUiThread {
      val waitingForBadge = wait_for_badge_tap
      val badgeTapped = badge_tapped

      waitingForBadge.visibility = View.GONE

      if (validTag) {
        badgeTapped.visibility = View.VISIBLE
      } else {
        val toneGen1 = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGen1.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500)

        invalid_tap_msg.text = message
        invalid_tap.visibility = View.VISIBLE
        invalid_tap_msg.visibility = View.VISIBLE
      }

      Handler().postDelayed({
        waitingForBadge.visibility = View.VISIBLE
        badgeTapped.visibility = View.GONE
        invalid_tap.visibility = View.GONE
        invalid_tap_msg.visibility = View.GONE
        waitingForTag = true
      }, duration)
    }
  }

  fun showAlert(title: String, message: Int) {
    val builder: AlertDialog.Builder
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
    } else {
      builder = AlertDialog.Builder(context)
    }


    builder.setTitle(title)
        .setMessage(message)
        .setNeutralButton(android.R.string.ok, OnClickListener({ dialog, which -> }))
        .setIcon(android.R.drawable.ic_dialog_alert)
        .show()
  }

  data class CheckInData(val userInfo: UserFragment?, val currentTags: HashMap<String, TagFragment>?, val newTags: HashMap<String, TagFragment>)
}