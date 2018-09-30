package gt.hack.nfc.fragment

import java.util.*

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.graphics.Color
import android.net.Uri
import android.nfc.NdefRecord
import android.nfc.tech.Ndef
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Bundle
import android.os.Handler

import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
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
import kotlinx.android.synthetic.main.fragment_tap.view.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.coroutines.experimental.runBlocking
import java.lang.RuntimeException
import kotlin.collections.ArrayList


class TapFragment : Fragment() {

  val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A
  val TAG = "CHECKIN/TAP_FRAGMENT"

  companion object {
    fun newInstance(): TapFragment {
      return TapFragment()
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.fragment_tap, container, false)
    //return super.onCreateView(inflater, container, savedInstanceState)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val uuidRegex = Regex("[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}")
    super.onViewCreated(view, savedInstanceState)
    Log.i("checkin-tap", "inside onviewcreated")
    val tagSelect: AutoCompleteTextView = checkin_tag
    val prevTag = tagSelect.text
    tagSelect.setText("Updating tags list...")
    tagSelect.isEnabled = false

    val preferences = PreferenceManager.getDefaultSharedPreferences(getActivity())
    var error = false;
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
      tagSelect.setText(prevTag)
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

      check_in_out_select.setOnCheckedChangeListener { buttonView, isChecked ->
        check_in_out_select.text = when (isChecked) {
          true -> getString(R.string.switch_check_in)
          false -> getString(R.string.switch_check_out)
        }
      }

      val nfc = NfcAdapter.getDefaultAdapter(activity)
      nfc.enableReaderMode(activity, { tag: Tag ->
        // get the latest read tag
        val ndef = Ndef.get(tag)
        val record: NdefRecord? = try {
          ndef.connect()
          val message = ndef.ndefMessage
          message.records[0]
        } catch (e: Throwable) {
          Log.i(TAG,"" + e.printStackTrace())
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
        if (id?.host == "live.hack.gt"  && uuidRegex.containsMatchIn(id.getQueryParameter("user").orEmpty())) {
          val uuid = id.getQueryParameter("user").orEmpty()
          Log.i(TAG, "this valid id is " + uuid)

          val tagName = tagSelect.text.trim().toString()
          val doCheckIn = check_in_out_select.isChecked
            // check in/out the user
            // TODO: extract this to a function
            val userInfo = runBlocking { API.getUserById(preferences, uuid) }
            val currentTags = runBlocking { API.getTagsForUser(preferences, uuid) }
            val newTags = when (doCheckIn) {
              true -> runBlocking { API.checkInTag(preferences, uuid, tagName) }
              else -> runBlocking { API.checkOutTag(preferences, uuid, tagName) }
            }

            val checkInData = CheckInData(userInfo, currentTags, newTags!!)


          Log.i(TAG, checkInData.userInfo.toString())
          Log.i(TAG, checkInData.currentTags.toString())
          Log.i(TAG, checkInData.newTags.toString())
        } else {
          Log.i(TAG, "this tag's data is formatted incorrectly: " + id)
          //invalidTagMessage(), probs a toast
        }



      }, READER_FLAGS, null)

    }
    Util.makeSnackbar(activity?.findViewById(R.id.content_frame), R.string.tags_api_made_past, Snackbar.LENGTH_SHORT).show()
  }

  override fun onResume() {
    super.onResume()
    Log.i(TAG, "Inside onResume")
//
//      activity?.runOnUiThread(Runnable {
//          tagSelect.setText(prevTag)
//          tagSelect.setEnabled(true)
//      })
//
//      val autocomplete = ArrayAdapter<String>(getContext(), android.R.layout.simple_expandable_list_item_1, allTags)
//
//      activity?.runOnUiThread(Runnable {
//          tagSelect.setThreshold(0)
//          tagSelect.setAdapter(autocomplete)
//          tagSelect.setOnFocusChangeListener { v, hasFocus -> if (!hasFocus) {
//                Util.hideSoftKeyboard(view, getContext())
//            }
//          }
//      })
//
//
//      Util.makeSnackbar(activity?.findViewById(R.id.content_frame), R.string.get_tags_failed, Snackbar.LENGTH_SHORT).show()
//

    //
    // Handle Checkin Settings
    //
    // only one setting so far, set to either checkin or checkout:
//    val checkInOrOut = activity?.check_in_out_select
//    checkInOrOut?.setOnCheckedChangeListener { compoundButton, isChecked -> when(isChecked) {
//      true -> compoundButton.setText(R.string.switch_check_in)
//      false -> compoundButton.setText(R.string.switch_check_out)
//    } }

    //
    // Read NFC Tags
    //
    // loop and read tags
//    val nfc = NfcAdapter.getDefaultAdapter(getActivity)
//    nfc.enableReaderMode(getActivity, (tag: Tag) => {
//      // get the latest read tag
//      val ndef = Ndef.get(tag)
//      val record = try {
//        ndef.connect()
//        val message = ndef.getNdefMessage
//        // first record is garaunteed to be there:
//        // https://developer.android.com/reference/android/nfc/NdefMessage.html#getRecords()
//        Some(message.getRecords()(0))
//      }
//      catch {
//        case e: Any =>
//          e.printStackTrace()
//          None
//      }
//      finally try ndef.close() catch {
//        case e: Any => e.printStackTrace()
//      }
//
//      val id = record
//        .flatMap(text => Option(text.toUri))
//        .filter(uri => uri.getHost == "live.hack.gt")
//        .flatMap(uri => Option(uri.getQueryParameter("user")))
//        .filter(user => uuidRegex.findFirstIn(user).isDefined)
//
//      val checkInTag = tagSelect.getText.toString.trim
//      val doCheckIn = checkInOrOut.isChecked
//
//      val finished = id match {
//        case Some(userId) => checkInUser(userId, doCheckIn, checkInTag)
//        case None =>
//          invalidTagMessage()
//          Future.successful(())
//      }
//
//      // show some messages in case of an error
//      finished onComplete {
//        case Success(()) => ()
//        case Failure(e) =>
//          e.printStackTrace()
//          Toast.makeText(getActivity, e.getMessage, Toast.LENGTH_LONG).show()
//      }
//      // wait for us to be done (and don't scan anything else while we're at it!)
//      Await.ready(finished, 10000 millis)
//    }, READER_FLAGS, null)
  }

//  fun checkInUser(id: String, checkIn: Boolean, tag: String): Future[Unit] = {
//    // get username & pass
//    val preferences = PreferenceManager.getDefaultSharedPreferences(getActivity)
//
//    // Make all our API calls out of band
//    val doCheckIn = Future {
//      val userInfo = API.getUserById(preferences, id))
//      val currentTags = Option(API.getTagsForUser(preferences, id))
//      val newTags = Option(checkIn match {
//        case true => API.checkInTag(preferences, id, tag)
//        case false => API.checkOutTag(preferences, id, tag)
//      })
//      CheckInData(userInfo, currentTags, newTags)
//    }
//
//    // set fields when complete
//    doCheckIn
//      .flatMap(data => runOnUi(() => drawCheckInFinish(data, tag)))
//      .flatMap(identity)
//  }

//  fun drawCheckInFinish(checkInData: CheckInData, tagName: String): Future[Unit] = {
//    val waitingForBadge: ProgressBar = getActivity.findViewById(R.id.wait_for_badge_tap)
//    val badgeTapped: ImageView = getActivity.findViewById(R.id.badge_tapped)
//    val userName: TextView = getActivity.findViewById(R.id.track_name)
//    val userBranch: TextView = getActivity.findViewById(R.id.track_type)
//    val userShirtSize: TextView = getActivity.findViewById(R.id.track_tshirt_size)
//    val userDietaryRestrictions: TextView = getActivity.findViewById(R.id.track_dietary_restrictions)
//    val tagSelect: AutoCompleteTextView = getActivity.findViewById(R.id.checkin_tag)
//    val complete = Promise[Unit]()
//
//    // Handle displaying user data or make an error message
//    checkInData.userInfo match {
//      case Some(userInfo) =>
//        userName.setText(userInfo.name)
//
//        if (userInfo.application != null) {
//          userBranch.setText(userInfo.application.`type`)
//        }
//
//        userInfo.questions.forEach(question => {
//          if (question.name.equals("tshirt-size")) {
//            userShirtSize.setText(question.value)
//          } else if (question.name.equals("dietary-restrictions")) {
//            userDietaryRestrictions.setText(question.value)
//          }
//        })
//
//      case None => showAlert("Invalid user on badge", R.string.invalid_badge_id)
//    }
//
//    val prevTagState = checkInData.currentTags.flatMap(tags => Option(tags.get(tagName)))
//    val currTagState = checkInData.newTags.flatMap(tags => Option(tags.get(tagName)))
//
//    // Handle already checked in / checked out messages
//    (prevTagState, currTagState) match {
//      case (prevTag, Some(currTag)) if prevTag.exists(t => t.checked_in) == currTag.checked_in =>
//        currTag.checked_in match {
//          case true => showAlert("User already checked in!", R.string.user_already_checked_in)
//          case false => showAlert("User already checked out!", R.string.user_already_checked_out)
//        }
//
//      case _ => ()
//    }
//
//    // Finish up, show the check mark or invalid tag
//    currTagState match {
//      case Some(_) =>
//        // we successfully scanned the tag!
//        // set the scanning image to a check mark
//        val handler = new Handler()
//        waitingForBadge.setVisibility(View.GONE)
//        badgeTapped.setVisibility(View.VISIBLE)
//        tagSelect.setBackgroundColor(Color.TRANSPARENT)
//        // pause one second (debounce)
//        delay(() => {
//          waitingForBadge.setVisibility(View.VISIBLE)
//          badgeTapped.setVisibility(View.GONE)
//        }, 1000)
//
//      case None =>
//        tagSelect.setBackgroundColor(R.color.lightRed)
//        Util.makeSnackbar(getActivity.findViewById(R.id.content_frame), R.string.invalid_tag, Snackbar.LENGTH_SHORT).show()
//        Future.successful(())
//    }
//  }

  fun invalidTagMessage() {
    Util.makeSnackbar(
        activity?.content_frame,
        R.string.invalid_nfc_tag,
        Snackbar.LENGTH_SHORT
    ).show()
  }

  fun showAlert(title: String, message: Int) {
    val builder: AlertDialog.Builder
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder = AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Dialog_Alert)
    } else {
      builder = AlertDialog.Builder(getContext())
    }


    builder.setTitle(title)
        .setMessage(message)
        .setNeutralButton(android.R.string.ok, OnClickListener({ dialog, which -> }))
        .setIcon(android.R.drawable.ic_dialog_alert)
        .show()
  }

  data class CheckInData(val userInfo: UserFragment?, val currentTags: HashMap<String, TagFragment>?, val newTags: HashMap<String, TagFragment>)
}

//
// Important Utilities!
//
//  fun runOnUi[F](fn: () => F): Future[F] = {
//    val complete = Promise[F]()
//    getActivity.runOnUiThread(() => {
//      complete success fn()
//    })
//    complete.future
//  }
//
//  fun delay[F](fn: () => F, millis: Int): Future[F] = {
//    val complete = Promise[F]()
//    val handler = new Handler()
//    handler.postDelayed(() => {
//      complete success fn()
//    }, millis)
//    complete.future
//  }
//}
