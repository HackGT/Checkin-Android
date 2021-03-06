package gt.hack.nfc;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.typeface.IIcon;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.AbstractDrawerItem;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
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
                .withTextColor(Color.WHITE)
                .withSelectionListEnabledForSingleProfile(false)
                .build();
        String username = preferences.getString("username", null);
        ArrayList<AbstractDrawerItem> drawerItems = new ArrayList<>();
        if (username != null && !username.contains("sponsor")) {
            drawerItems.add(DrawerItem.SCAN.getDrawerItem());

        }


        drawerItems.add(DrawerItem.SEARCH.getDrawerItem().withIdentifier(101));
        drawerItems.add(DrawerItem.TAP.getDrawerItem().withIdentifier(102));
        drawerItems.add(new DividerDrawerItem().withIdentifier(103));
        drawerItems.add(DrawerItem.LOGOUT.getDrawerItem().withIdentifier(104));
        drawerItems.add(new SecondaryDrawerItem().withName("Version " + getApplicationContext().getString(R.string.app_version)).withSelectable(false).withIdentifier(105));

        result = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(toolbar)
                //.withHasStableIds(true)
                .withAccountHeader(headerResult)
                .addDrawerItems(
                        drawerItems.toArray(new AbstractDrawerItem[drawerItems.size()])
                )
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {

                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        if (drawerItem instanceof PrimaryDrawerItem) {
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

                             }

                             @Override
                             public void onDrawerClosed(View drawerView) {

                             }

                             @Override
                             public void onDrawerSlide(View drawerView, float slideOffset) {
                                 drawerView.bringToFront();
                                 drawerView.requestLayout();
                             }
                         }
                 )
                .build();
        if (savedInstanceState == null) {
            result.setSelection(DrawerItem.SCAN.getDrawerItem(), true);
        }
        switchToFragment(new CheckinFragment());
    }

    private void switchToFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();
    }
    private void logOut() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String instanceUrl = preferences.getString("url", Util.DEFAULT_SERVER);
        preferences.edit().clear().putString("url", instanceUrl).apply();
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
