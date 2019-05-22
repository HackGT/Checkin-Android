package gt.hack.nfc.fragment


import android.app.SearchManager
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.ListView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.ListFragment
import com.apollographql.apollo.exception.ApolloException
import gt.hack.nfc.R
import gt.hack.nfc.util.API
import gt.hack.nfc.util.SearchAdapter
import kotlinx.coroutines.runBlocking
import java.util.ArrayList
import kotlin.collections.HashSet

class SearchFragment : ListFragment(), SearchView.OnQueryTextListener {
    private var adapter: SearchAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        adapter = SearchAdapter(context!!,
                R.layout.card_hacker, ArrayList())
        listAdapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.search_menu, menu)

        // Associate searchable configuration with the SearchView
        val searchManager = activity!!.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView = menu.findItem(R.id.search).actionView as SearchView
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(activity!!.componentName))
        searchView.setOnQueryTextListener(this)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        adapter!!.clear()
        setListShown(false)
        HackerLoadTask().execute(query)
        return false
    }

    override fun onQueryTextChange(newText: String): Boolean {
        return false
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)
        val fragmentManager = fragmentManager
        val transaction = fragmentManager!!.beginTransaction()
        transaction.setCustomAnimations(R.anim.enter, R.anim.exit,
                R.anim.pop_enter, R.anim.pop_exit)

        val fragment2 = CheckinFlowFragment.newInstance(adapter!!.getItem(position)!!)
        transaction.addToBackStack(null)
        transaction.replace(R.id.content_frame, fragment2)
        transaction.commit()
    }

    internal inner class HackerLoadTask : AsyncTask<String, String, ArrayList<UserFragment>>() {

        override fun doInBackground(vararg strings: String): ArrayList<UserFragment>? {
            try {
                return runBlocking { API.getUsers(PreferenceManager.getDefaultSharedPreferences(activity), strings[0]) }
            } catch (e: ApolloException) {
                e.printStackTrace()
            }

            return null
        }

        override fun onPostExecute(searchResults: ArrayList<UserFragment>?) {
            super.onPostExecute(searchResults)
            setListShown(true)
            if (searchResults != null) {
                adapter!!.clear()
                adapter!!.addAll(HashSet(searchResults))
                (listAdapter as SearchAdapter).notifyDataSetChanged()
            }
        }
    }
}

