package gt.hack.nfc.fragment;


import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatButton;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.IOException;

import gt.hack.nfc.R;

public class CheckinFlowFragment extends Fragment {
    public static int READER_FLAGS =
            NfcAdapter.FLAG_READER_NFC_A;
    private String id = "ehsanmasdar@gmail.com";
    private final String packageName = "gt.hack.nfc";
    private AppCompatButton confirmButton;
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        return inflater.inflate(R.layout.fragment_checkin_confirm, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        confirmButton = getActivity().findViewById(R.id.confirmCheckin);
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getFragmentManager().popBackStack();
            }
        });
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(getActivity());
        if (nfc != null) {
            nfc.enableReaderMode(getActivity(), new NfcAdapter.ReaderCallback() {
                @Override
                public void onTagDiscovered(Tag tag) {
                    Ndef ndef = Ndef.get(tag);
                    Log.d("NFC", tag.toString());
                    try {
                        ndef.connect();
                        if (ndef.isWritable()) {
                            String type = "badge";
                            NdefRecord extRecord = NdefRecord.createExternal(packageName, type, id.getBytes());
                            NdefMessage ndefMessage = new NdefMessage(new NdefRecord[] { extRecord });
                            ndef.writeNdefMessage(ndefMessage);
                            getActivity().runOnUiThread(new Runnable() {
                                public void run() {
                                    ProgressBar progressBar = getActivity().findViewById(R.id.waitForBadge);
                                    progressBar.setVisibility(View.GONE);
                                    ImageView check = getActivity().findViewById(R.id.badgeWritten);
                                    check.setVisibility(View.VISIBLE);
                                    confirmButton.setVisibility(View.VISIBLE);
                                }
                            });

                        }
                    } catch (IOException | FormatException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            ndef.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }, READER_FLAGS, null);
        }
    }
}
