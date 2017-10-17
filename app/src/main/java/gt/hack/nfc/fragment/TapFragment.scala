package gt.hack.nfc.fragment

import java.util

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.graphics.Color
import android.nfc.tech.Ndef
import android.nfc.{NfcAdapter, Tag}
import android.os.{Build, Bundle, Handler}
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget._
import gt.hack.nfc.R
import gt.hack.nfc.util.{API, Util}

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.language.postfixOps


class TapFragment
  extends Fragment {

  val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    super.onCreateView(inflater, container, savedInstanceState)
    inflater.inflate(R.layout.fragment_tap, container, false)
  }

  override def onResume() {
    super.onResume()
    val preferences = PreferenceManager.getDefaultSharedPreferences(getActivity)
    val uuidRegex = "[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}".r

    //
    // Fetch Latest Tags
    //
    // get the text view, save the last text and notify we are updating the tags list
    val tagSelect: AutoCompleteTextView = getActivity.findViewById(R.id.checkin_tag)
    val prevTag = tagSelect.getText
    tagSelect.setText("Updating tags list...")
    tagSelect.setEnabled(false)

    // asynchronously get the latest tags
    val allTags = Future {
      API.getTags(preferences)
    }

    // when we are done (failure or not)
    allTags onComplete(result => {
      // re-enable the text view and restore the last put tag
      getActivity.runOnUiThread(() => {
        tagSelect.setText(prevTag)
        tagSelect.setEnabled(true)
      })
      // check the  result to see if
      result match {
        case Success(tags) =>
          val autocomplete = new ArrayAdapter[String](getContext, android.R.layout.simple_expandable_list_item_1, tags)

          getActivity.runOnUiThread(() => {
            tagSelect.setThreshold(0)
            tagSelect.setAdapter(autocomplete)
            tagSelect.setOnFocusChangeListener((view: View, hasFocus: Boolean) => {
              if (!hasFocus) {
                Util.hideSoftKeyboard(view, getContext)
              }
            })
          })

        case Failure(e) =>
          e.printStackTrace()
          Util.makeSnackbar(getActivity.findViewById(R.id.content_frame), R.string.get_tags_failed, Snackbar.LENGTH_SHORT).show()
      }
    })

    //
    // Handle Checkin Settings
    //
    // only one setting so far, set to either checkin or checkout:
    val checkInOrOut: Switch = getActivity.findViewById(R.id.check_in_out_select)
    checkInOrOut.setOnCheckedChangeListener((compoundButton: CompoundButton, isChecked: Boolean) => {
      compoundButton.setText(isChecked match {
        case true => R.string.switch_check_in
        case false => R.string.switch_check_out
      })
    })

    //
    // Read NFC Tags
    //
    // loop and read tags
    val nfc = NfcAdapter.getDefaultAdapter(getActivity)
    nfc.enableReaderMode(getActivity, (tag: Tag) => {
      // get the latest read tag
      val ndef = Ndef.get(tag)
      val record = try {
        ndef.connect()
        val message = ndef.getNdefMessage
        // first record is garaunteed to be there:
        // https://developer.android.com/reference/android/nfc/NdefMessage.html#getRecords()
        Some(message.getRecords()(0))
      }
      catch {
        case e: Any =>
          e.printStackTrace()
          None
      }
      finally try ndef.close() catch {
        case e: Any => e.printStackTrace()
      }

      val id = record
        .flatMap(text => Option(text.toUri))
        .filter(uri => uri.getHost == "live.hack.gt")
        .flatMap(uri => Option(uri.getQueryParameter("user")))
        .filter(user => uuidRegex.findFirstIn(user).isDefined)

      val checkInTag = tagSelect.getText.toString.trim
      val doCheckIn = checkInOrOut.isChecked

      val finished = id match {
        case Some(userId) => checkInUser(userId, doCheckIn, checkInTag)
        case None =>
          invalidTagMessage()
          Future.successful(())
      }

      // show some messages in case of an error
      finished onComplete {
        case Success(()) => ()
        case Failure(e) =>
          e.printStackTrace()
          Toast.makeText(getActivity, e.getMessage, Toast.LENGTH_LONG).show()
      }
      // wait for us to be done (and don't scan anything else while we're at it!)
      Await.ready(finished, 10000 millis)
    }, READER_FLAGS, null)
  }

  def checkInUser(id: String, checkIn: Boolean, tag: String): Future[Unit] = {
    // get username & pass
    val preferences = PreferenceManager.getDefaultSharedPreferences(getActivity)

    // Make all our API calls out of band
    val doCheckIn = Future {
      val userInfo = Option(API.getUserById(preferences, id))
      val currentTags = Option(API.getTagsForUser(preferences, id))
      val newTags = Option(checkIn match {
        case true => API.checkInTag(preferences, id, tag)
        case false => API.checkOutTag(preferences, id, tag)
      })
      CheckInData(userInfo, currentTags, newTags)
    }

    // set fields when complete
    doCheckIn
      .flatMap(data => runOnUi(() => drawCheckInFinish(data, tag)))
      .flatMap(identity)
  }

  def drawCheckInFinish(checkInData: CheckInData, tagName: String): Future[Unit] = {
    val waitingForBadge: ProgressBar = getActivity.findViewById(R.id.wait_for_badge_tap)
    val badgeTapped: ImageView = getActivity.findViewById(R.id.badge_tapped)
    val userName: TextView = getActivity.findViewById(R.id.track_name)
    val userBranch: TextView = getActivity.findViewById(R.id.track_type)
    val userShirtSize: TextView = getActivity.findViewById(R.id.track_tshirt_size)
    val userDietaryRestrictions: TextView = getActivity.findViewById(R.id.track_dietary_restrictions)
    val tagSelect: AutoCompleteTextView = getActivity.findViewById(R.id.checkin_tag)
    val complete = Promise[Unit]()

    // Handle displaying user data or make an error message
    checkInData.userInfo match {
      case Some(userInfo) =>
        userName.setText(userInfo.name)

        if (userInfo.application != null) {
          userBranch.setText(userInfo.application.`type`)
        }

        userInfo.questions.forEach(question => {
          if (question.name.equals("tshirt-size")) {
            userShirtSize.setText(question.value)
          } else if (question.name.equals("dietary-restrictions")) {
            userDietaryRestrictions.setText(question.value)
          }
        })

      case None => showAlert("Invalid user on badge", R.string.invalid_badge_id)
    }

    val prevTagState = checkInData.currentTags.flatMap(tags => Option(tags.get(tagName)))
    val currTagState = checkInData.newTags.flatMap(tags => Option(tags.get(tagName)))

    // Handle already checked in / checked out messages
    (prevTagState, currTagState) match {
      case (prevTag, Some(currTag)) if prevTag.exists(t => t.checked_in) == currTag.checked_in =>
        currTag.checked_in match {
          case true => showAlert("User already checked in!", R.string.user_already_checked_in)
          case false => showAlert("User already checked out!", R.string.user_already_checked_out)
        }

      case _ => ()
    }

    // Finish up, show the check mark or invalid tag
    currTagState match {
      case Some(_) =>
        // we successfully scanned the tag!
        // set the scanning image to a check mark
        val handler = new Handler()
        waitingForBadge.setVisibility(View.GONE)
        badgeTapped.setVisibility(View.VISIBLE)
        tagSelect.setBackgroundColor(Color.TRANSPARENT)
        // pause one second (debounce)
        delay(() => {
          waitingForBadge.setVisibility(View.VISIBLE)
          badgeTapped.setVisibility(View.GONE)
        }, 1000)

      case None =>
        tagSelect.setBackgroundColor(R.color.lightRed)
        Util.makeSnackbar(getActivity.findViewById(R.id.content_frame), R.string.invalid_tag, Snackbar.LENGTH_SHORT).show()
        Future.successful(())
    }
  }

  def invalidTagMessage() {
    Util.makeSnackbar(
      getActivity.findViewById(R.id.content_frame),
      R.string.invalid_nfc_tag,
      Snackbar.LENGTH_SHORT
    ).show()
  }

  def showAlert(title: String, message: Int) {
    val builder: AlertDialog.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      new AlertDialog.Builder(getContext, android.R.style.Theme_Material_Dialog_Alert)
    }
    else {
      new AlertDialog.Builder(getContext)
    }

    builder.setTitle(title)
      .setMessage(message)
      .setNeutralButton(android.R.string.ok, new OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = {
          ()
        }
      })
      .setIcon(android.R.drawable.ic_dialog_alert)
      .show()
  }

  case class CheckInData(userInfo: Option[UserFragment], currentTags: Option[util.HashMap[String, TagFragment]], newTags: Option[util.HashMap[String, TagFragment]])

  //
  // Important Utilities!
  //
  def runOnUi[F](fn: () => F): Future[F] = {
    val complete = Promise[F]()
    getActivity.runOnUiThread(() => {
      complete success fn()
    })
    complete.future
  }

  def delay[F](fn: () => F, millis: Int): Future[F] = {
    val complete = Promise[F]()
    val handler = new Handler()
    handler.postDelayed(() => {
      complete success fn()
    }, millis)
    complete.future
  }
}
