package gt.hack.nfc.fragment;


import android.app.SearchManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.ListView;

import com.apollographql.apollo.exception.ApolloException;

import java.util.ArrayList;


import gt.hack.nfc.R;
import gt.hack.nfc.util.API;
import gt.hack.nfc.util.SearchAdapter;

public class SearchFragment extends ListFragment implements SearchView.OnQueryTextListener {
    private SearchAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        adapter = new SearchAdapter(getContext(),
                R.layout.card_hacker, new ArrayList<UserFragment>());
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
        adapter.clear();
        setListShown(false);
        new HackerLoadTask().execute(query);
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
        transaction.setCustomAnimations(R.anim.enter, R.anim.exit,
                R.anim.pop_enter, R.anim.pop_exit);

        CheckinFlowFragment fragment2 = CheckinFlowFragment.newInstance(adapter.getItem(position));
        transaction.addToBackStack(null);
        transaction.replace(R.id.content_frame, fragment2);
        transaction.commit();
    }

    class HackerLoadTask extends AsyncTask<String, String, ArrayList<UserFragment>> {

        @Override
        protected ArrayList<UserFragment> doInBackground(String... strings) {
            try {
                return API.getUsers(PreferenceManager.getDefaultSharedPreferences(getActivity())
                        , strings[0]);
            } catch (ApolloException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(ArrayList<UserFragment> searchResults) {
            super.onPostExecute(searchResults);
            setListShown(true);
            if (searchResults != null) {
                adapter.clear();
                adapter.addAll(searchResults);
                ((SearchAdapter) getListAdapter()).notifyDataSetChanged();
            }
        }
    }
}

