package gt.hack.nfc.util


import android.content.SharedPreferences
import android.util.Log
import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.ApolloException
import gt.hack.nfc.CheckInTagMutation
import gt.hack.nfc.TagsGetQuery
import gt.hack.nfc.UserGetQuery
import gt.hack.nfc.UserSearchQuery
import gt.hack.nfc.fragment.TagFragment
import gt.hack.nfc.fragment.UserFragment
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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
              preferences.edit().putString("cookie", authCookie).putBoolean("loggedIn", true).apply()
                return true
            }
        }
        return false
    }

    private fun getApolloClient(preferences: SharedPreferences): ApolloClient {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val original = chain.request()
          val builder = original.newBuilder().method(original.method,
              original.body)
            builder.header("Cookie", preferences.getString("cookie", "")!!)
          println(chain.request().url)
            chain.proceed(builder.build())
        }
            .build()

        return ApolloClient.builder()
            .serverUrl(preferences.getString("url", Util.DEFAULT_SERVER)!! + "/graphql/")
            .okHttpClient(client).build()
    }


    suspend fun <T> ApolloCall<T>.execute() = suspendCoroutine<com.apollographql.apollo.api.Response<T>> { cont ->
        enqueue(object : ApolloCall.Callback<T>() {
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
        val response = apolloClient.query(UserSearchQuery(query, 20)).execute()

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
    suspend fun getTags(preferences: SharedPreferences, only_current: Boolean = false): ArrayList<String>? {
        val apolloClient = getApolloClient(preferences)
        val response = apolloClient.query(TagsGetQuery(only_current)).execute()
        val items = ArrayList<String>()
        for (t in response.data()!!.tags()) {
            items.add(t.name())
        }
        return items
    }

    @Throws(ApolloException::class)
    suspend fun getUserById(preferences: SharedPreferences, id: String): UserGetQuery.User? {
        val apolloClient = getApolloClient(preferences)
        val response = apolloClient.query(UserGetQuery(id)).execute()
        if (response.hasErrors()) {
            Log.e("apollo", response.errors().toString())
            return null
        }
        return if (response.data()!!.user() != null) {
            response.data()!!.user()!!
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
        if (response.data()!!.user() != null) {
            return parseTags(response.data()!!.user()!!.tags())
        }
        return null
    }

    fun parseTags(tagsIn: List<UserGetQuery.Tag>): HashMap<String, TagFragment>? {
        val tags = HashMap<String, TagFragment>()
        for (t in tagsIn) {
            tags[t.fragments().tagFragment().tag().name()] = t.fragments().tagFragment()
        }
        return tags
    }

    @Throws(ApolloException::class)
    suspend fun checkInTag(preferences: SharedPreferences,
                           userid: String, tag: String, checkin: Boolean): CheckInTagMutation.Check_in? {
        val apolloClient = getApolloClient(preferences)
        val response = apolloClient.mutate(CheckInTagMutation(userid, tag, checkin)).execute()
        if (response.hasErrors()) {
            Log.e("apollo", response.errors().toString())
            return null
        }
        val tags = HashMap<String, TagFragment>()
        if (response.data()?.check_in() != null) {
            for (t in response.data()!!.check_in()!!.tags()) {
                tags[t.fragments().tagFragment().tag().name()] = t.fragments().tagFragment()
            }
            return response.data()?.check_in()
        }
        return null
    }
}
