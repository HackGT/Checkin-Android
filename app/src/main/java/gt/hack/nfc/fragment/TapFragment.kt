package gt.hack.nfc.fragment

import android.media.MediaPlayer
import android.net.Uri
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.annotation.RawRes
import gt.hack.nfc.R
import gt.hack.nfc.util.API
import gt.hack.nfc.util.NFCHandler
import gt.hack.nfc.util.Util
import kotlinx.android.synthetic.main.fragment_tap.*
import kotlinx.coroutines.runBlocking
import org.threeten.bp.*
import org.threeten.bp.format.DateTimeFormatter
import java.text.ParseException
import java.util.*

class TapFragment : androidx.fragment.app.Fragment() {

  val TAG = "CHECKIN/TAP_FRAGMENT"
  var waitingForTag = true
  private var nfcHandler = NFCHandler()
  private var mediaPlayer: MediaPlayer? = null

  companion object {
    fun newInstance(): TapFragment {
      return TapFragment()
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.fragment_tap, container, false)
  }

  override fun onResume() {
    super.onResume()

    // in case NFC becomes enabled in settings
    loadNFC()
  }

  fun playSound(@RawRes rawResId: Int) {
    val assetFileDescriptor = context!!.resources.openRawResourceFd(rawResId) ?: return
    mediaPlayer?.run {
      reset()
      setDataSource(assetFileDescriptor.fileDescriptor,
          assetFileDescriptor.startOffset, assetFileDescriptor.declaredLength)
      prepareAsync()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    Log.i("checkin-tap", "inside onviewcreated")
    mediaPlayer = MediaPlayer().apply {
      setOnPreparedListener { start() }
      setOnCompletionListener { reset() }
    }

    val tagSelect: AutoCompleteTextView = checkin_tag
    val prevTag = tagSelect.text
    tagSelect.setText("Updating tags list...")
    tagSelect.isEnabled = false

    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    var allTags = runBlocking { API.getTags(preferences) } // TODO: this seems to delay showing this screen, so check that out
    val currentCheckinTags = runBlocking { API.getTags(preferences, true) }

    allTags = allTags as ArrayList<String>
    allTags.sortWith(kotlin.Comparator { t1, t2 ->
      val t1SortVal = when (currentCheckinTags?.contains(t1)) {
        true -> -1
        else -> 0
      }
      val t2SortVal = when (currentCheckinTags?.contains(t2)) {
        true -> -1
        else -> 0
      }
      t1SortVal - t2SortVal
    })
    Log.i(TAG, "Contents of sorted allTags: " + allTags)

    tagSelect.text = prevTag
    tagSelect.isEnabled = true

    val autocomplete = ArrayAdapter(context!!, android.R.layout.simple_expandable_list_item_1, allTags)
    // the cast to ArrayList is kinda icky but so long as the API response is OK, it should work
    tagSelect.threshold = 1
    tagSelect.setAdapter(autocomplete)
    tagSelect.setOnFocusChangeListener { v, hasFocus ->
      if (!hasFocus) {
        Util.hideSoftKeyboard(view, context!!)
      } else {
        tagSelect.showDropDown() // make sure the suggestions show even if no characters have been entered
      }
    }

    check_in_out_select.setOnCheckedChangeListener { _, isChecked ->
      check_in_out_select.text = when (isChecked) {
        true -> getString(R.string.switch_check_in)
        false -> getString(R.string.switch_check_out)
      }
    }

    nfcHandler.activity = activity
    nfcHandler.context = context
    nfcHandler.callback = NfcAdapter.ReaderCallback { tag: Tag ->
      val uuidRegex = Regex("[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}")
      val tagSelect: AutoCompleteTextView = checkin_tag
      val preferences = PreferenceManager.getDefaultSharedPreferences(activity)

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
        if (id?.host == "info.hack.gt" && uuidRegex.containsMatchIn(id.getQueryParameter("user").orEmpty())) {
          val uuid = id.getQueryParameter("user").orEmpty()
          Log.i(TAG, "this valid id is " + uuid)

          var tagName = tagSelect.text.trim().toString()
          tagName = tagName.toLowerCase()
          val doCheckIn = check_in_out_select.isChecked

          val checkinResult = runBlocking { API.checkInTag(preferences, uuid, tagName, doCheckIn) }

          if (checkinResult != null) {
            var checkinDetails: TagFragment? = null
            val checkinTagInfo = checkinResult.tags().findLast { it.fragments().tagFragment().tag.name.equals(tagName) }
            if (checkinTagInfo != null) {
              checkinDetails = checkinTagInfo.fragments().tagFragment()
            }

            val userInfo = checkinResult.user().fragments().userFragment()

            val checkInData = CheckInData(userInfo, checkinDetails)

            drawCheckInFinish(checkInData)
          } else {
            Log.i(TAG, "checkin API returned null")
            displayMessageAndReset(false, getString(R.string.uuid_or_tag_not_found), 2500)
          }
        } else {
          if (id == null) {
            Log.i(TAG, "this tag's data is null: " + id)
            displayMessageAndReset(false, getString(R.string.badge_data_null), 2500)
          } else {
            Log.i(TAG, "this tag's data is formatted incorrectly: " + id)
            displayMessageAndReset(false, getString(R.string.invalid_badge_id), 4000)
          }
        }

      }

    }

    loadNFC()
  }

  fun loadNFC() {
    nfcHandler.loadNFC(nfcInstructions, wait_for_badge_tap, nfc_error, enable_nfc_button)
  }

  fun drawCheckInFinish(checkInData: CheckInData) {
    val waitingForBadge = wait_for_badge_tap
    val userName = track_name
    val userBranch = track_type
    val userShirtSize = track_tshirt_size
    val userDietaryRestrictions = track_dietary_restrictions

    val userInfo = checkInData.userInfo
    var userShirtSizeVal: String? = ""
    var userDietaryRestrictionsVal: String? = ""
    if (userInfo != null && checkInData.checkInResult != null) {

      userInfo.questions.forEach { question: UserFragment.Question? ->
        if (question?.name.equals("tshirt-size")) {
          userShirtSizeVal = question?.value
        } else if (question?.name.equals("dietary-restrictions")) {
          userDietaryRestrictionsVal = question?.values?.toString()
        }
      }


      activity?.runOnUiThread {
        userName.text = userInfo.name
        if (userInfo.application != null) {
          userBranch.text = userInfo.application.type
        }

        userShirtSize.text = userShirtSizeVal
        try {
          userDietaryRestrictions.text = userDietaryRestrictionsVal?.substring(1)?.dropLast(1)
          if (userDietaryRestrictions.text.equals("null")) {
            userDietaryRestrictions.text = ""
          }
        } catch (e: Throwable) {
          userDietaryRestrictions.text = ""
        }

        waitingForBadge.visibility = View.GONE
        nfcInstructions.visibility = View.GONE
      }

      val validOperation: Boolean = checkInData.checkInResult.checkin_success

      if (validOperation) {
        displayMessageAndReset(true, "", 750)
      } else { // invalid check in/out
        val lastCheckInTimeString = getTimeSinceCheckinEvent(checkInData.checkInResult)
        val checkInUser = checkInData.checkInResult.last_successful_checkin()?.checked_in_by
        val checkInType = checkInData.checkInResult.checked_in

        val checkInString = when (checkInType) {
          true -> getString(R.string.user_already_checked_in)
          false -> getString(R.string.user_already_checked_out)
        }

        displayMessageAndReset(false, checkInString, 3000, checkin = checkInType, lastSuccessTime = lastCheckInTimeString, lastSuccessUser = checkInUser)
      }

    } else { // checkInData is null, ie invalid user
      displayMessageAndReset(false, getString(R.string.invalid_badge_id), 4000)
    }
  }

  private fun getTimeSinceCheckinEvent(checkInResult: TagFragment): CharSequence? {
    val checkinDate = checkInResult.last_successful_checkin?.checked_in_date
    if (checkinDate == null) {
      return null
    }
    val localTimeZone = ZoneId.systemDefault()
    val utc = ZoneOffset.UTC
    val iso = DateTimeFormatter.ISO_DATE_TIME
    try {
      val parsedDate = LocalDateTime.parse(checkinDate, iso)

      val dateInUtc = ZonedDateTime.of(parsedDate, utc).toInstant()
      val dateInLocalTime = OffsetDateTime.ofInstant(dateInUtc, localTimeZone)

      return DateUtils.getRelativeTimeSpanString(dateInLocalTime.toInstant().toEpochMilli(), Instant.now(Clock.systemDefaultZone()).toEpochMilli(), DateUtils.SECOND_IN_MILLIS)
    } catch (e: ParseException) {
      Log.e(TAG, "Unable to parse last successful check-in date returned by server: " + checkinDate)
      e.printStackTrace()
      return null
    }
  }

  private fun displayMessageAndReset(validTag: Boolean, message: String, duration: Long, checkin: Boolean? = null, lastSuccessTime: CharSequence? = "", lastSuccessUser: String? = "") {
    activity?.runOnUiThread {
      val waitingForBadge = wait_for_badge_tap
      val badgeTapped = badge_tapped

      waitingForBadge.gone()
      nfcInstructions.gone()

      if (validTag) {
        badgeTapped.show()
        playSound(R.raw.success)
      } else {
        playSound(R.raw.alert_simple)

        invalid_tap_msg.text = message
        invalid_tap.show()
        invalid_tap_msg.show()
        if (checkin != null) {
          if (lastSuccessTime != null) {
            last_successful_checkin_date.text = lastSuccessTime
            last_successful_checkin_date.show()
          }
          if (lastSuccessUser != null) {
            last_successful_checkin_user.text = lastSuccessUser
            last_successful_checkin_user.show()
          }
        }
      }

      Handler().postDelayed({
        waitingForBadge.show()
        nfcInstructions.show()

        badgeTapped.gone()
        invalid_tap.gone()
        invalid_tap_msg.gone()

        last_successful_checkin_date.hide()
        last_successful_checkin_user.hide()

        waitingForTag = true
      }, duration)
    }
  }

  private fun View.show() {
    this.visibility = View.VISIBLE
  }

  private fun View.hide() {
    this.visibility = View.INVISIBLE
  }

  private fun View.gone() {
    this.visibility = View.GONE
  }


  data class CheckInData(val userInfo: UserFragment?, val checkInResult: TagFragment?)
}