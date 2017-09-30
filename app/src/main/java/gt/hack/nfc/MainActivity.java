package gt.hack.nfc;

import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.WindowManager;

import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;

import gt.hack.nfc.fragment.CheckinFragment;
import gt.hack.nfc.fragment.SearchFragment;


/**
 * Main application functions
 */

public class MainActivity extends AppCompatActivity {
    private CharSequence mTitle;

    private Drawer result = null;
    private AccountHeader headerResult;
    private PendingIntent pendingIntent;
    private IntentFilter[] intentFiltersArray;
    private NfcAdapter mAdapter;
    private String[][] techLists;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final IProfile profile = new ProfileDrawerItem().withName("Ehsan Asdar")
                .withEmail("ehsanmasdar@gmail.com").withIdentifier(100);
        headerResult = new AccountHeaderBuilder()
                .withActivity(this)
                .withTranslucentStatusBar(true)
                .addProfiles(profile)
                .withSavedInstance(savedInstanceState)
                .build();

        PrimaryDrawerItem checkin = new PrimaryDrawerItem().withName("Scan QR Code");
        PrimaryDrawerItem checkinManual = new PrimaryDrawerItem().withName("Manual User Search");

        result = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(toolbar)
                .withHasStableIds(true)
                .withAccountHeader(headerResult)
                .addDrawerItems(checkin, checkinManual)
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {

                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        if (drawerItem != null) {
                            FragmentManager fragmentManager = getSupportFragmentManager();
                            if (position == 1) {
                                CheckinFragment fragment = new CheckinFragment();
                                fragmentManager.beginTransaction()
                                        .replace(R.id.content_frame, fragment).commit();
                            } else if (position == 2) {
                                SearchFragment fragment = new SearchFragment();
                                fragmentManager.beginTransaction()
                                        .replace(R.id.content_frame, fragment).commit();
                            }
                            setTitle(((PrimaryDrawerItem) drawerItem).getName().toString());
                        }
                        return false;
                    }
                })
                .build();
        if (savedInstanceState == null) {
            result.setSelection(checkin, true);
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getSupportActionBar().setTitle(mTitle);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Tag t = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        android.util.Log.v("NFC", "Discovered tag");
        android.util.Log.v("NFC", "{"+t+"}");
    }
}
