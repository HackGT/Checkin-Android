package gt.hack.nfc.util

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.util.Log
import android.widget.Toast


import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.ApolloException

import java.io.IOException
import java.util.ArrayList
import java.util.HashMap


import gt.hack.nfc.CheckInTagMutation
import gt.hack.nfc.CheckOutTagMutation
import gt.hack.nfc.TagsGetQuery
import gt.hack.nfc.UserGetQuery
import gt.hack.nfc.UserSearchQuery
import gt.hack.nfc.fragment.TagFragment
import gt.hack.nfc.fragment.UserFragment
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import kotlin.coroutines.experimental.suspendCoroutine

object API {


    @Throws(IOException::class)
    fun login(username: String, password: String,
              preferences: SharedPreferences): Boolean {
        val client = OkHttpClient()
        val formBody = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build()
        val request = Request.Builder().url(preferences.getString("url", Util.DEFAULT_SERVER)!! + "/api/user/login").post(formBody).build()
        val call = client.newCall(request)
        call.execute().use { response ->
            println(response)
            if (response.isSuccessful) {
                val cookieResponses = response.headers("set-cookie")
                println(cookieResponses)
                var authCookie = ""
                for (cookie in cookieResponses) {
                    // Gross string manipulation
                    if (cookie.contains("auth=")) {
                        authCookie = cookie.substring(cookie.indexOf("auth="))
                        authCookie = authCookie.substring(0, authCookie.indexOf(";"))
                    }
                }
                preferences.edit().putString("cookie", authCookie).putBoolean("loggedIn", true).commit()
                return true
            }
        }
        return false
    }

    private fun getApolloClient(preferences: SharedPreferences): ApolloClient {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder().method(original.method(),
                    original.body())
            builder.header("Cookie", preferences.getString("cookie", "")!!)
            println(chain.request().url())
            chain.proceed(builder.build())
        }
                .build()

        return ApolloClient.builder()
                .serverUrl(preferences.getString("url", Util.DEFAULT_SERVER)!! + "/graphql/")
                .okHttpClient(client).build()
    }


    suspend fun <T> ApolloCall<T>.execute() = suspendCoroutine<com.apollographql.apollo.api.Response<T>> { cont ->
        enqueue(object: ApolloCall.Callback<T>() {
            override fun onResponse(response: com.apollographql.apollo.api.Response<T>) {
                cont.resume(response)
            }

            override fun onFailure(e: ApolloException) {
                cont.resumeWithException(e)
            }
        })
    }

    @Throws(ApolloException::class)
    suspend fun getUsers(preferences: SharedPreferences, query: String): ArrayList<UserFragment> {
        val apolloClient = getApolloClient(preferences)
        val response = apolloClient.query(UserSearchQuery(query, 20)).execute();

        val users = ArrayList<UserFragment>()
        for (user in response.data()!!.search_user_simple()) {
            users.add(user.user().fragments().userFragment())
        }
        for (user in response.data()!!.search_user_simple()) {
            users.add(user.user().fragments().userFragment())
        }
        return users
    }

    @Throws(ApolloException::class)
    suspend fun getTags(preferences: SharedPreferences): ArrayList<String>? {
        val apolloClient = getApolloClient(preferences)
        val response = apolloClient.query(TagsGetQuery()).execute()
        val items = ArrayList<String>()
        for (t in response.data()!!.tags()) {
            items.add(t.name())
        }
        return items
    }

    @Throws(ApolloException::class)
    suspend fun getUserById(preferences: SharedPreferences, id: String): UserFragment? {
        val apolloClient = getApolloClient(preferences)
        val response = apolloClient.query(UserGetQuery(id)).execute()
        if (response.hasErrors()) {
            Log.e("apollo", response.errors().toString())
            return null
        }
        return if (response.data()!!.user() != null) {
            response.data()!!.user()!!.user().fragments().userFragment()
        } else null
    }

    @Throws(ApolloException::class)
    suspend fun getTagsForUser(preferences: SharedPreferences, id: String): HashMap<String, TagFragment>? {
        val apolloClient = getApolloClient(preferences)
        val response = apolloClient.query(UserGetQuery(id)).execute()
        if (response.hasErrors()) {
            Log.e("apollo", response.errors().toString())
            return null
        }
        val tags = HashMap<String, TagFragment>()
        if (response.data()!!.user() != null) {
            for (t in response.data()!!.user()!!.tags()) {
                tags[t.fragments().tagFragment().tag().name()] = t.fragments().tagFragment()
            }
            return tags
        }
        return null
    }

    @Throws(ApolloException::class)
    suspend fun checkInTag(preferences: SharedPreferences,
                   userid: String, tag: String): HashMap<String, TagFragment>? {
        val apolloClient = getApolloClient(preferences)
        val response = apolloClient.mutate(CheckInTagMutation(userid, tag)).execute()
        if (response.hasErrors()) {
            Log.e("apollo", response.errors().toString())
            return null
        }
        val tags = HashMap<String, TagFragment>()
        if (response.data()!!.check_in() != null) {
            for (t in response.data()!!.check_in()!!.tags()) {
                tags[t.fragments().tagFragment().tag().name()] = t.fragments().tagFragment()
            }
            return tags
        }
        return null
    }

    @Throws(ApolloException::class)
    suspend fun checkOutTag(preferences: SharedPreferences,
                    userid: String, tag: String): HashMap<String, TagFragment>? {
        val apolloClient = getApolloClient(preferences)
        val response = apolloClient.mutate(CheckOutTagMutation(userid, tag)).execute()
        if (response.hasErrors()) {
            Log.e("apollo", response.errors().toString())
            return null
        }
        val tags = HashMap<String, TagFragment>()
        if (response.data()!!.check_out() != null) {
            for (t in response.data()!!.check_out()!!.tags()) {
                tags[t.fragments().tagFragment().tag().name()] = t.fragments().tagFragment()
            }
            return tags
        }
        return null
    }
//
//    interface Supplier<T> {
//        @Throws(ApolloException::class)
//        fun get(): T
//    }
//
//    interface Consumer<T> {
//        fun run(data: T)
//    }

//    class AsyncGraphQlTask<O>(private val context: Context, private val callback: Consumer<List<O>>) : AsyncTask<Supplier<O>, Void, List<O>>() {
//
//        override fun doInBackground(vararg params: Supplier<O>): List<O> {
//            val outputs = ArrayList<O?>()
//            for (supplier in params) {
//                try {
//                    outputs.add(supplier.get())
//                } catch (err: Exception) {
//                    err.printStackTrace()
//                    Toast.makeText(context, err.message, Toast.LENGTH_LONG).show()
//                    outputs.add(null)
//                }
//
//            }
//            return outputs
//        }
//
//        override fun onPostExecute(data: List<O>) {
//            this.callback.run(data)
//        }
//    }
}
