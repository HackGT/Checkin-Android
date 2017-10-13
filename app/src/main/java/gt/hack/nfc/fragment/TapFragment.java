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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;

import com.apollographql.apollo.exception.ApolloException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

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
    public static final int READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A;

    @Override
    public void onResume() {
        super.onResume();

        tagSelect = getActivity().findViewById(R.id.checkin_tag);
        checkInOrOut = getActivity().findViewById(R.id.check_in_out_select);
        waitingForBadge = getActivity().findViewById(R.id.wait_for_badge_tap);
        badgeTapped = getActivity().findViewById(R.id.badge_tapped);

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
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(getActivity());
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
                        String id = encodedURL.getQueryParameter("user");
                        if (id.length() != 36) {
                            NfcInvalidTag();
                            return;
                        }
                        if (tagSelect.getText().toString().trim().length() == 0) {
                            Util.makeSnackbar(getActivity().findViewById(R.id.content_frame), R.string.invalid_tag, Snackbar.LENGTH_SHORT).show();
                            return;
                        }
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        HashMap<String, TagFragment> APIresult_temp;

                        final HashMap<String, TagFragment> currentState = API.getTagsForUser(preferences, id);
                        if (checkInOrOut.isChecked()) {
                            APIresult_temp = API.checkInTag(preferences, id, tagSelect.getText().toString().trim());
                        }
                        else {
                            APIresult_temp = API.checkOutTag(preferences, id, tagSelect.getText().toString().trim());
                        }
                        // Java is really stupid
                        // The API result has to be marked final to be accessed in the inner class below
                        final HashMap<String, TagFragment> APIresult = APIresult_temp;

                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (APIresult == null) {
                                    // User doesn't actually exist according to the checkin2 backend
                                    // Could be due to forgery, wrong DB being used, old data
                                    AlertDialog.Builder builder;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Dialog_Alert);
                                    }
                                    else {
                                        builder = new AlertDialog.Builder(getContext());
                                    }
                                    builder.setTitle("Invalid user on badge")
                                            .setMessage(R.string.invalid_badge_id)
                                            .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {

                                                }
                                            })
                                            .setIcon(android.R.drawable.ic_dialog_alert)
                                            .show();
                                    return;
                                } else if (currentState.get(tagSelect.getText().toString().trim()) == null ||
                                        (currentState.get(tagSelect.getText().toString().trim()).checked_in && APIresult.get(tagSelect.getText().toString().trim()).checked_in)) {
                                    AlertDialog.Builder builder;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Dialog_Alert);
                                    }
                                    else {
                                        builder = new AlertDialog.Builder(getContext());
                                    }
                                    builder.setTitle("User already checked in!")
                                            .setMessage(R.string.user_already_checked_in)
                                            .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {

                                                }
                                            })
                                            .setIcon(android.R.drawable.ic_dialog_alert)
                                            .show();
                                    return;
                                } else if (currentState.get(tagSelect.getText().toString().trim()) == null ||
                                        (!currentState.get(tagSelect.getText().toString().trim()).checked_in && !APIresult.get(tagSelect.getText().toString().trim()).checked_in)) {
                                    AlertDialog.Builder builder;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Dialog_Alert);
                                    }
                                    else {
                                        builder = new AlertDialog.Builder(getContext());
                                    }
                                    builder.setTitle("User already checked out!")
                                            .setMessage(R.string.user_already_checked_out)
                                            .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {

                                                }
                                            })
                                            .setIcon(android.R.drawable.ic_dialog_alert)
                                            .show();
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
                                    }
                                }, 1000);
                            }
                        });
                    }
                    catch (IOException | FormatException e) {
                        e.printStackTrace();
                    }
                    catch (ApolloException e) {
                        Util.makeSnackbar(getActivity().findViewById(R.id.content_frame), R.string.server_or_network_error, Snackbar.LENGTH_SHORT).show();
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
}
