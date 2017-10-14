package gt.hack.nfc.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import com.apollographql.apollo.exception.ApolloException;
import com.google.android.gms.common.api.Api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import gt.hack.nfc.R;
import gt.hack.nfc.util.API;
import gt.hack.nfc.util.TagAsyncTask;
import gt.hack.nfc.util.Util;

public class TapFragment extends Fragment {
    public static TapFragment newInstance() {
        TapFragment fragment = new TapFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        return inflater.inflate(R.layout.fragment_tap, container, false);
    }

    private AutoCompleteTextView tagSelect;
    private Switch checkInOrOut;
    private ProgressBar waitingForBadge;
    private ImageView badgeTapped;
    private TextView userName;
    private TextView userBranch;
    private TextView userShirtSize;
    private TextView userDietaryRestrictions;
    public static final int READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A;

    @Override
    public void onResume() {
        super.onResume();

        tagSelect = getActivity().findViewById(R.id.checkin_tag);
        checkInOrOut = getActivity().findViewById(R.id.check_in_out_select);
        waitingForBadge = getActivity().findViewById(R.id.wait_for_badge_tap);
        badgeTapped = getActivity().findViewById(R.id.badge_tapped);
        userName = getActivity().findViewById(R.id.track_name);
        userBranch = getActivity().findViewById(R.id.track_type);
        userShirtSize = getActivity().findViewById(R.id.track_tshirt_size);
        userDietaryRestrictions = getActivity().findViewById(R.id.track_dietary_restrictions);

        ArrayList<String> tags;
        try {
            tags = new TagAsyncTask(PreferenceManager.getDefaultSharedPreferences(getActivity()), TapFragment.this).execute().get();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
            Util.makeSnackbar(getActivity().findViewById(R.id.content_frame), R.string.get_tags_failed, Snackbar.LENGTH_SHORT).show();
            tags = new ArrayList<>();
        } catch (ExecutionException e) {
            e.printStackTrace();
            Util.makeSnackbar(getActivity().findViewById(R.id.content_frame), R.string.get_tags_failed, Snackbar.LENGTH_SHORT).show();
            tags = new ArrayList<>();
        }
        ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, tags);
        tagSelect.setThreshold(0);
        tagSelect.setAdapter(autoCompleteAdapter);
        tagSelect.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    Util.hideSoftKeyboard(view, getContext());
                }
            }
        });

        checkInOrOut.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                compoundButton.setText(isChecked ? R.string.switch_check_in : R.string.switch_check_out);
            }
        });

        // Wait for NFC read
        final AtomicBoolean processingBadge = new AtomicBoolean(false);
        final NfcAdapter nfc = NfcAdapter.getDefaultAdapter(getActivity());
        if (nfc != null) {
            nfc.enableReaderMode(getActivity(), new NfcAdapter.ReaderCallback() {
                @Override
                public void onTagDiscovered(Tag tag) {
                    Ndef ndef = Ndef.get(tag);
                    try {
                        ndef.connect();
                        NdefMessage message = ndef.getNdefMessage();
                        NdefRecord[] records = message.getRecords();
                        if (records.length == 0) {
                            NfcInvalidTag();
                            return;
                        }
                        Uri encodedURL = records[0].toUri();
                        if (!encodedURL.getHost().equals("live.hack.gt")) {
                            NfcInvalidTag();
                            return;
                        }
                        final String id = encodedURL.getQueryParameter("user");
                        if (id.length() != 36) {
                            NfcInvalidTag();
                            return;
                        }
                        final String selectedTag = tagSelect.getText().toString().trim();
                        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

                        API.AsyncGraphQlTask<UserFragment> getUserInfo = new API.AsyncGraphQlTask<>(
                                getActivity().getApplicationContext(),
                                new API.Consumer<List<UserFragment>>() {

                            @Override
                            public void run(final List<UserFragment> users) {
                                API.AsyncGraphQlTask<HashMap<String, TagFragment>> getCurrentState = new API.AsyncGraphQlTask<>(
                                        getActivity().getApplicationContext(),
                                        new API.Consumer<List<HashMap<String, TagFragment>>>() {

                                            @Override
                                            public void run(List<HashMap<String, TagFragment>> tags) {
                                                HashMap<String, TagFragment> currentState = tags.get(0);
                                                HashMap<String, TagFragment> APIresult = tags.get(1);
                                                UserFragment userInfo = users.get(0);

                                                if (userInfo != null) {
                                                    userName.setText(userInfo.name);

                                                    if (userInfo.application != null) {
                                                        userBranch.setText(userInfo.application.type);
                                                    }

                                                    for (UserFragment.Question question : userInfo.questions) {
                                                        if (question.name.equals("tshirt-size")) {
                                                            userShirtSize.setText(question.value);
                                                        } else if (question.name.equals("dietary-restrictions")) {
                                                            userDietaryRestrictions.setText(question.value);
                                                        }
                                                    }
                                                }

                                                if (tagSelect.length() == 0) {
                                                    Util.makeSnackbar(getActivity().findViewById(R.id.content_frame), R.string.invalid_tag, Snackbar.LENGTH_SHORT).show();
                                                } else if (APIresult == null || APIresult.get(selectedTag) == null || userInfo == null) {
                                                    // User doesn't actually exist according to the checkin2 backend
                                                    // Could be due to forgery, wrong DB being used, old data
                                                    showAlert("Invalid user on badge", R.string.invalid_badge_id);
                                                } else if (currentState.get(selectedTag) != null
                                                        && currentState.get(selectedTag).checked_in
                                                        && APIresult.get(selectedTag).checked_in)
                                                {
                                                    // if we are already checked in and we want to check us in show a warning
                                                    showAlert("User already checked in!", R.string.user_already_checked_in);
                                                } else if ((currentState.get(selectedTag) == null ||
                                                        !currentState.get(selectedTag).checked_in)
                                                        && !APIresult.get(selectedTag).checked_in) {
                                                    // if we were already checked out and we wanted to check out
                                                    showAlert("User already checked out!", R.string.user_already_checked_out);
                                                }

                                                if (APIresult == null) {
                                                    processingBadge.set(false);
                                                    return;
                                                }

                                                final Handler handler = new Handler();
                                                waitingForBadge.setVisibility(View.GONE);
                                                badgeTapped.setVisibility(View.VISIBLE);

                                                handler.postDelayed(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        waitingForBadge.setVisibility(View.VISIBLE);
                                                        badgeTapped.setVisibility(View.GONE);
                                                        processingBadge.set(false);
                                                    }
                                                }, 1000);
                                            }
                                        });

                                getCurrentState.execute(
                                        new API.Supplier<HashMap<String, TagFragment>>() {
                                            @Override
                                            public HashMap<String, TagFragment> get() throws ApolloException {
                                                return API.getTagsForUser(preferences, id);
                                            }
                                        },
                                        new API.Supplier<HashMap<String, TagFragment>>() {
                                            @Override
                                            public HashMap<String, TagFragment> get() throws ApolloException {
                                                if (checkInOrOut.isChecked()) {
                                                    return API.checkInTag(preferences, id, selectedTag);
                                                }
                                                else {
                                                    return API.checkOutTag(preferences, id, selectedTag);
                                                }
                                            }
                                        }
                                );
                            }
                        });

                        if (processingBadge.get()) {
                            Log.d("NFC", "Skipped processing badge due to in-progress request");
                            return;
                        }
                        processingBadge.set(true);
                        getUserInfo.execute(new API.Supplier<UserFragment>() {

                            @Override
                            public UserFragment get() throws ApolloException {
                                return API.getUserById(preferences, id);
                            }
                        });
                    }
                    catch (IOException | FormatException e) {
                        e.printStackTrace();
                    }
                    finally {
                        try {
                            ndef.close();
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }, READER_FLAGS, null);
        }
    }

    private void NfcInvalidTag() {
        Util.makeSnackbar(getActivity().findViewById(R.id.content_frame), R.string.invalid_nfc_tag, Snackbar.LENGTH_SHORT).show();
    }

    private void showAlert(String title, int message) {
        AlertDialog.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Dialog_Alert);
        }
        else {
            builder = new AlertDialog.Builder(getContext());
        }
        builder.setTitle(title)
                .setMessage(message)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}
