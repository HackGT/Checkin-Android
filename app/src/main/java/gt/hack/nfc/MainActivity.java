package gt.hack.nfc;

import
        android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.CompoundButton;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.typeface.IIcon;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.interfaces.OnCheckedChangeListener;
import com.mikepenz.materialdrawer.model.AbstractDrawerItem;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.SwitchDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;

import java.util.ArrayList;

import gt.hack.nfc.fragment.CheckinFragment;
import gt.hack.nfc.fragment.SearchFragment;
import gt.hack.nfc.fragment.TapFragment;
import gt.hack.nfc.util.Util;


/**
 * Main application functions
 */

public class MainActivity extends AppCompatActivity {
    private CharSequence mTitle;

    private Drawer result = null;
    private AccountHeader headerResult;


    private enum DrawerItem {
        SCAN ("Scan QR code", "Scan", GoogleMaterial.Icon.gmd_camera),
        SEARCH ("Search for user", "Search", GoogleMaterial.Icon.gmd_search),
        TAP ("Tap to track event", "Track event", GoogleMaterial.Icon.gmd_nfc),
        LOGOUT ("Log out", "Log out", GoogleMaterial.Icon.gmd_exit_to_app);

        private String label;
        private String title;
        private IIcon icon;
        private PrimaryDrawerItem drawerItem;

        DrawerItem(String label, String title, IIcon icon) {
            this.label = label;
            this.title = title;
            this.icon = icon;
            this.drawerItem = new PrimaryDrawerItem().withName(label).withIcon(icon);
        }

        public PrimaryDrawerItem getDrawerItem() {
            return drawerItem;
        }
        public String getLabel() {
            return label;
        }
        public String getTitle() {
            return title;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        final IProfile profile = new ProfileDrawerItem()
                .withName(preferences.getString("username", "HackGT User"))
                .withEmail(preferences.getString("url", Util.DEFAULT_SERVER))
                .withIcon(R.drawable.empty)
                .withIdentifier(100);
        headerResult = new AccountHeaderBuilder()
                .withActivity(this)
                .withTranslucentStatusBar(true)
                .addProfiles(profile)
                .withSavedInstance(savedInstanceState)
                .withHeaderBackground(R.drawable.header)
                .withSelectionListEnabledForSingleProfile(false)
                .build();
        String username = preferences.getString("username", null);
        ArrayList<AbstractDrawerItem> drawerItems = new ArrayList<>();
        if (username != null && !username.contains("sponsor")) {
            drawerItems.add(DrawerItem.SCAN.getDrawerItem());

        }
        if (username.equals("ehsan") || username.equals("petschekr") || username.equals("andrew") || username.equals("michael") || username.equals("kexin")
                || username.equals("evan")) {
            drawerItems.add(DrawerItem.SEARCH.getDrawerItem());
        }
        drawerItems.add(DrawerItem.TAP.getDrawerItem());
        drawerItems.add(new DividerDrawerItem());
        drawerItems.add(new SwitchDrawerItem().withChecked(Util.nfcLockEnabled).withName("NFC locking enabled").withOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(IDrawerItem drawerItem, CompoundButton buttonView, boolean isChecked) {
                Util.nfcLockEnabled = isChecked;
                if (isChecked) {
                    Util.makeSnackbar(findViewById(R.id.content_frame), R.string.nfc_locking_enabled, Snackbar.LENGTH_SHORT).show();
                }
                else {
                    Util.makeSnackbar(findViewById(R.id.content_frame), R.string.nfc_locking_disabled, Snackbar.LENGTH_SHORT).show();
                }
            }
        }).withSelectable(false));
        drawerItems.add(new DividerDrawerItem());
        drawerItems.add(DrawerItem.LOGOUT.getDrawerItem());
        drawerItems.add(new SecondaryDrawerItem().withName("Version " + getApplicationContext().getString(R.string.app_version)).withSelectable(false));

        result = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(toolbar)
                .withHasStableIds(true)
                .withAccountHeader(headerResult)
                .addDrawerItems(
                        drawerItems.toArray(new AbstractDrawerItem[drawerItems.size()])
                )
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {

                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        if (drawerItem != null && drawerItem instanceof PrimaryDrawerItem) {
                            String selectedLabel = ((PrimaryDrawerItem) drawerItem).getName().toString();
                            String newTitle = "";
                            if (selectedLabel.equals(DrawerItem.SCAN.getLabel())) {
                                switchToFragment(new CheckinFragment());
                                newTitle = DrawerItem.SCAN.getTitle();
                            } else if (selectedLabel.equals(DrawerItem.SEARCH.getLabel())) {
                                switchToFragment(new SearchFragment());
                                newTitle = DrawerItem.SEARCH.getTitle();
                            } else if (selectedLabel.equals(DrawerItem.TAP.getLabel())) {
                                switchToFragment(new TapFragment());
                                newTitle = DrawerItem.TAP.getTitle();
                            } else if (selectedLabel.equals(DrawerItem.LOGOUT.getLabel())) {
                                logOut();
                                newTitle = DrawerItem.LOGOUT.getTitle();
                            }
                            setTitle(newTitle);
                        }
                        return false;
                    }
                })
                 .withOnDrawerListener(
                         new Drawer.OnDrawerListener() {
                             @Override
                             public void onDrawerOpened(View drawerView) {
                                drawerView.clearFocus();
                             }

                             @Override
                             public void onDrawerClosed(View drawerView) {

                             }

                             @Override
                             public void onDrawerSlide(View drawerView, float slideOffset) {

                             }
                         }
                 )
                .build();
        if (savedInstanceState == null) {
            result.setSelection(DrawerItem.SCAN.getDrawerItem(), true);
        }
    }

    private void switchToFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();
    }
    private void logOut() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        // May want to keep around some information in the future
        // For now, clear everything
        preferences.edit().clear().commit();
        Intent i = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(i);
        finish();
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getSupportActionBar().setTitle(mTitle);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Tag t = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        android.util.Log.v("NFC", "Discovered tag");
        android.util.Log.v("NFC", "{"+t+"}");
    }
}
