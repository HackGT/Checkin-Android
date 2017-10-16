package gt.hack.nfc.fragment

import java.util

import android.net.Uri
import android.nfc.tech.Ndef
import android.nfc.{NfcAdapter, Tag}
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.view.View.OnFocusChangeListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget._
import gt.hack.nfc.R
import gt.hack.nfc.util.{API, Util}

import scala.concurrent.{Future, blocking}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


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
      getActivity().runOnUiThread(() => {
        tagSelect.setText(prevTag)
        tagSelect.setEnabled(true)
      })
      // check the  result to see if
      result match {
        case Success(tags) => {
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
        }
        case Failure(e) => {
          // TODO: print exception
          Util.makeSnackbar(getActivity.findViewById(R.id.content_frame), R.string.get_tags_failed, Snackbar.LENGTH_SHORT).show()
        }
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
      val record = try
        ndef.connect()
        val message = ndef.getNdefMessage
        // first record is garaunteed to be there:
        // https://developer.android.com/reference/android/nfc/NdefMessage.html#getRecords()
        Some(message.getRecords()(0))
      catch {
        case e: Any => {
          e.printStackTrace()
          None
        }
      }
      finally try
        ndef.close()
      catch {
        case e: Any => e.printStackTrace()
      }

      val id = record
        .flatMap(text => Option(text.toUri()))
        .filter(uri => uri.getHost == "live.hack.gt")
        .flatMap(uri => Option(uri.getQueryParameter("user")))
        .filter(user => uuidRegex.findFirstIn(user).isDefined)

      val checkInTag = tagSelect.getText.toString.trim
      val doCheckIn = checkInOrOut.isChecked

      id match {
        case Some(userId) => checkInUser(userId, doCheckIn, checkInTag)
        case None => invalidTagMessage()
      }
    }, READER_FLAGS, null)
  }

  def checkInUser(id: String, checkIn: Boolean, tag: String): Unit = {
    // get username & pass
    val preferences = PreferenceManager.getDefaultSharedPreferences(getActivity)

    // Make all our API calls out of band
    val doCheckIn = Future {
      val userInfo = Option(API.getUserId(preferences, id))
      val currentTags = Option(API.getTagsForUser(preferences, id))
      val newTags = Option(checkIn match {
        case true => API.checkInTag(preferences, id, tag)
        case false => API.checkOutTag(preferences, id, tag)
      })
      CheckInData(userInfo, currentTags, newTags)
    }

    // set fields when complete
    doCheckIn onComplete {
      case Success(data) => getActivity.runOnUiThread(() => {
        drawCheckInFinish(data)
      })
      case Failure(e) => {
        // TODO: notify user of failure
      }
    }
  }

  def drawCheckInFinish(checkInData: CheckInData): Unit = {
    val userName: TextView = getActivity.findViewById(R.id.track_name)
    val userBranch: TextView = getActivity.findViewById(R.id.track_type)
    val userShirtSize: TextView = getActivity.findViewById(R.id.track_tshirt_size)
    val userDietaryRestrictions: TextView = getActivity.findViewById(R.id.track_dietary_restrictions)

    checkInData.userInfo.foreach(userInfo => {
      userName.setText(userInfo.name);

      if (userInfo.application != null) {
        userBranch.setText(userInfo.application.type)
      }

      userInfo.questions.forEach(question => {
        if (question.name.equals("tshirt-size")) {
          userShirtSize.setText(question.value);
        } else if (question.name.equals("dietary-restrictions")) {
          userDietaryRestrictions.setText(question.value);
        }
      })
    })
  }

  def invalidTagMessage() {
    Util.makeSnackbar(
      getActivity.findViewById(R.id.content_frame),
      R.string.invalid_nfc_tag,
      Snackbar.LENGTH_SHORT
    ).show()
  }

  // Wait for NFC read
//  final AtomicBoolean processingBadge = new AtomicBoolean(false);
//  final NfcAdapter nfc = NfcAdapter.getDefaultAdapter(getActivity());
//  if (nfc != null) {
//    nfc.enableReaderMode(getActivity(), new NfcAdapter.ReaderCallback() {
//      @Override
//      public void onTagDiscovered(Tag tag) {
//        Ndef ndef = Ndef.get(tag);
//        try {
//          ndef.connect();
//          NdefMessage message = ndef.getNdefMessage();
//          NdefRecord[] records = message.getRecords();
//          if (records.length == 0) {
//            NfcInvalidTag();
//            return;
//          }
//          Uri encodedURL = records[0].toUri();
//          if (!encodedURL.getHost().equals("live.hack.gt")) {
//            NfcInvalidTag();
//            return;
//          }
//          final String id = encodedURL.getQueryParameter("user");
//          if (id.length() != 36) {
//            NfcInvalidTag();
//            return;
//          }
//          final String selectedTag = tagSelect.getText().toString().trim();
//          final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
//
//          API.AsyncGraphQlTask<UserFragment> getUserInfo = new API.AsyncGraphQlTask<>(
//            getActivity().getApplicationContext(),
//            new API.Consumer<List<UserFragment>>() {
//
//              @Override
//              public void run(final List<UserFragment> users) {
//                API.AsyncGraphQlTask<HashMap<String, TagFragment>> getCurrentState = new API.AsyncGraphQlTask<>(
//                  getActivity().getApplicationContext(),
//                  new API.Consumer<List<HashMap<String, TagFragment>>>() {
//
//                  @Override
//                  public void run(List<HashMap<String, TagFragment>> tags) {
//                    HashMap<String, TagFragment> currentState = tags.get(0);
//                    HashMap<String, TagFragment> APIresult = tags.get(1);
//                    UserFragment userInfo = users.get(0);
//
//                    if (userInfo != null) {
//                      userName.setText(userInfo.name);
//
//                      if (userInfo.application != null) {
//                        userBranch.setText(userInfo.application.type);
//                      }
//
//                      for (UserFragment.Question question : userInfo.questions) {
//                        if (question.name.equals("tshirt-size")) {
//                          userShirtSize.setText(question.value);
//                        } else if (question.name.equals("dietary-restrictions")) {
//                          userDietaryRestrictions.setText(question.value);
//                        }
//                      }
//                    }
//
//                    if (tagSelect.length() == 0) {
//                      Util.makeSnackbar(getActivity().findViewById(R.id.content_frame), R.string.invalid_tag, Snackbar.LENGTH_SHORT).show();
//                    } else if (APIresult == null || APIresult.get(selectedTag) == null || userInfo == null) {
//                      // User doesn't actually exist according to the checkin2 backend
//                      // Could be due to forgery, wrong DB being used, old data
//                      showAlert("Invalid user on badge", R.string.invalid_badge_id);
//                    } else if (currentState.get(selectedTag) != null
//                      && currentState.get(selectedTag).checked_in
//                      && APIresult.get(selectedTag).checked_in)
//                    {
//                      // if we are already checked in and we want to check us in show a warning
//                      showAlert("User already checked in!", R.string.user_already_checked_in);
//                    } else if ((currentState.get(selectedTag) == null ||
//                      !currentState.get(selectedTag).checked_in)
//                      && !APIresult.get(selectedTag).checked_in) {
//                      // if we were already checked out and we wanted to check out
//                      showAlert("User already checked out!", R.string.user_already_checked_out);
//                    }
//
//                    if (APIresult == null) {
//                      processingBadge.set(false);
//                      return;
//                    }
//
//                    final Handler handler = new Handler();
//                    waitingForBadge.setVisibility(View.GONE);
//                    badgeTapped.setVisibility(View.VISIBLE);
//
//                    handler.postDelayed(new Runnable() {
//                      @Override
//                      public void run() {
//                        waitingForBadge.setVisibility(View.VISIBLE);
//                        badgeTapped.setVisibility(View.GONE);
//                        processingBadge.set(false);
//                      }
//                    }, 1000);
//                  }
//                });
//
//                getCurrentState.execute(
//                  new API.Supplier<HashMap<String, TagFragment>>() {
//                    @Override
//                    public HashMap<String, TagFragment> get() throws ApolloException {
//                      return API.getTagsForUser(preferences, id);
//                    }
//                  },
//                  new API.Supplier<HashMap<String, TagFragment>>() {
//                    @Override
//                    public HashMap<String, TagFragment> get() throws ApolloException {
//                      if (checkInOrOut.isChecked()) {
//                        return API.checkInTag(preferences, id, selectedTag);
//                      }
//                      else {
//                        return API.checkOutTag(preferences, id, selectedTag);
//                      }
//                    }
//                  }
//                );
//              }
//            });
//
//          if (processingBadge.get()) {
//            Log.d("NFC", "Skipped processing badge due to in-progress request");
//            return;
//          }
//          processingBadge.set(true);
//          getUserInfo.execute(new API.Supplier<UserFragment>() {
//
//            @Override
//            public UserFragment get() throws ApolloException {
//              return API.getUserById(preferences, id);
//            }
//          });
//        }
//        catch (IOException | FormatException e) {
//          e.printStackTrace();
//        }
//        finally {
//          try {
//            ndef.close();
//          }
//          catch (IOException e) {
//            e.printStackTrace();
//          }
//        }
//      }
//    }, READER_FLAGS, null);
//  }
//}
//
//private void NfcInvalidTag() {
//  Util.makeSnackbar(getActivity().findViewById(R.id.content_frame), R.string.invalid_nfc_tag, Snackbar.LENGTH_SHORT).show();
//}
//
//  private void showAlert(String title, int message) {
//  AlertDialog.Builder builder;
//  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//  builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Dialog_Alert);
//}
//  else {
//  builder = new AlertDialog.Builder(getContext());
//}
//  builder.setTitle(title)
//  .setMessage(message)
//  .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
//  @Override
//  public void onClick(DialogInterface dialogInterface, int i) {
//}
//})
//  .setIcon(android.R.drawable.ic_dialog_alert)
//  .show();
//}

//    waitingForBadge = getActivity.findViewById(R.id.wait_for_badge_tap)
//    badgeTapped = getActivity.findViewById(R.id.badge_tapped)
//    // Wait for NFC read
//          var APIresult_temp: util.HashMap[String, TagFragment] = null
//          val currentState: util.HashMap[String, TagFragment] = API.getTagsForUser(preferences, id)
//          if (checkInOrOut.isChecked) APIresult_temp = API.checkInTag(preferences, id, tagSelect.getText.toString.trim)
//          else APIresult_temp = API.checkOutTag(preferences, id, tagSelect.getText.toString.trim)
//          // Java is really stupid
//          // The API result has to be marked final to be accessed in the inner class below
//          val APIresult: util.HashMap[String, TagFragment] = APIresult_temp
//          getActivity.runOnUiThread(new Runnable() {
//            def run() {
//              if (APIresult == null) {
//                // User doesn't actually exist according to the checkin2 backend
//                // Could be due to forgery, wrong DB being used, old data
//                var builder: AlertDialog.Builder = null
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) builder = new AlertDialog.Builder(getContext, android.R.style.Theme_Material_Dialog_Alert)
//                else builder = new AlertDialog.Builder(getContext)
//                builder.setTitle("Invalid user on badge").setMessage(R.string.invalid_badge_id).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
//                  def onClick(dialogInterface: DialogInterface, i: Int) {
//                  }
//                }).setIcon(android.R.drawable.ic_dialog_alert).show
//                return
//              }
//              else if (currentState.get(tagSelect.getText.toString.trim) == null || (currentState.get(tagSelect.getText.toString.trim).checked_in && APIresult.get(tagSelect.getText.toString.trim).checked_in)) {
//                var builder: AlertDialog.Builder = null
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) builder = new AlertDialog.Builder(getContext, android.R.style.Theme_Material_Dialog_Alert)
//                else builder = new AlertDialog.Builder(getContext)
//                builder.setTitle("User already checked in!").setMessage(R.string.user_already_checked_in).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
//                  def onClick(dialogInterface: DialogInterface, i: Int) {
//                  }
//                }).setIcon(android.R.drawable.ic_dialog_alert).show
//                return
//              }
//              else if (currentState.get(tagSelect.getText.toString.trim) == null || (!currentState.get(tagSelect.getText.toString.trim).checked_in && !APIresult.get(tagSelect.getText.toString.trim).checked_in)) {
//                var builder: AlertDialog.Builder = null
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) builder = new AlertDialog.Builder(getContext, android.R.style.Theme_Material_Dialog_Alert)
//                else builder = new AlertDialog.Builder(getContext)
//                builder.setTitle("User already checked out!").setMessage(R.string.user_already_checked_out).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
//                  def onClick(dialogInterface: DialogInterface, i: Int) {
//                  }
//                }).setIcon(android.R.drawable.ic_dialog_alert).show
//                return
//              }
//              val handler: Handler = new Handler
//              waitingForBadge.setVisibility(View.GONE)
//              badgeTapped.setVisibility(View.VISIBLE)
//              handler.postDelayed(new Runnable() {
//                def run() {
//                  waitingForBadge.setVisibility(View.VISIBLE)
//                  badgeTapped.setVisibility(View.GONE)
//                }
//              }, 1000)
//            }
//          })
//
//        catch {
//          case e: Any => {
//            e.printStackTrace()
//          }
//          case e: ApolloException => {
//            Util.makeSnackbar(getActivity.findViewById(R.id.content_frame), R.string.server_or_network_error, Snackbar.LENGTH_SHORT).show()
//            e.printStackTrace()
//          }
//        } finally try
//          ndef.close()
//
//        catch {
//          case e: IOException => {
//            e.printStackTrace()
//          }
//        }
//      }
//    }, READER_FLAGS, null)
//  }

  case class CheckInData(userInfo: Option[UserFragment], currentTags: Option[util.HashMap[String, TagFragment]], newTags: Option[util.HashMap[String, TagFragment]])

}
