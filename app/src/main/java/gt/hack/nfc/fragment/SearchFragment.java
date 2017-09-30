package gt.hack.nfc.fragment;


import android.app.SearchManager;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

import gt.hack.nfc.R;
import gt.hack.nfc.util.Hacker;
import gt.hack.nfc.util.SearchAdapter;

public class SearchFragment extends ListFragment implements SearchView.OnQueryTextListener {
    private ArrayList<Hacker> hackers;
    private SearchAdapter adapter;
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        hackers = new ArrayList<>();
        adapter = new SearchAdapter(getContext(),
                R.layout.card_hacker, hackers);
        setListAdapter(adapter);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.search_menu, menu);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager =
                (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView =
                (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(getActivity().getComponentName()));
        searchView.setOnQueryTextListener(this);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        hackers.clear();
        hackers.add(new Hacker("Ehsan Asdar", "easdar@gatech.edu", "M", "Georgia Institute of Technology"));
        adapter.notifyDataSetChanged();
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit);

        CheckinFlowFragment fragment2 = new CheckinFlowFragment();
        transaction.addToBackStack(null);
        transaction.replace(R.id.content_frame, fragment2);
        transaction.commit();
    }
}

