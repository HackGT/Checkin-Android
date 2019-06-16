package gt.hack.nfc

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.TargetApi
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.Patterns
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import gt.hack.nfc.util.API
import gt.hack.nfc.util.Util
import gt.hack.nfc.util.toEditable
import kotlinx.android.synthetic.main.activity_login.*
import java.io.IOException


/**
 * A login screen that offers login via email/password.
 */
class LoginActivity : AppCompatActivity() {

  /**
   * Keep track of the login task to ensure we can cancel it if requested.
   */
  private var mAuthTask: UserLoginTask? = null

  // UI references.
  private var mProgressView: View? = null
  private var mLoginFormView: View? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val p = PreferenceManager.getDefaultSharedPreferences(applicationContext)

    if (p.getBoolean("loggedIn", false)) {
      val i = Intent(this@LoginActivity, MainActivity::class.java)
      startActivity(i)
      if (p.getBoolean("loggedIn", false)) {
        finish()
      }
    }

    setContentView(R.layout.activity_login)
    val savedCheckInInstanceUrl: String? = p.getString("url", Util.DEFAULT_SERVER)
    instance_url_input.text = savedCheckInInstanceUrl.toEditable()

    // Set up the login form.

    val mEmailSignInButton = findViewById<Button>(R.id.btn_login)
    mEmailSignInButton.setOnClickListener { attemptLogin() }

    mLoginFormView = findViewById(R.id.login_form)
    mProgressView = findViewById(R.id.login_progress)
  }


  /**
   * Attempts to sign in or register the account specified by the login form.
   * If there are form errors (invalid email, missing fields, etc.), the
   * errors are presented and no actual login attempt is made.
   */
  private fun attemptLogin() {
    if (mAuthTask != null) {
      return
    }

    // Reset errors.
    username_input.error = null
    password_input.error = null

    // Store values at the time of the login attempt.
    val usernameVal = username_input.text.toString()
    val passwordVal = password_input.text.toString()
    val instanceUrl = instance_url_input.text.toString()

    var cancel = false
    var focusView: View? = null

    if (TextUtils.isEmpty(instanceUrl)) {
      instance_url_input.error = getString(R.string.error_field_required)
      focusView = instance_url_input
      cancel = true
    } else if (!Patterns.WEB_URL.matcher(instanceUrl).matches()) {
      instance_url_input.error = "Enter a valid URL"
      focusView = instance_url_input
      cancel = true
    } else {
      val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
      preferences.edit().putString("url", instanceUrl).apply()
    }

    if (TextUtils.isEmpty(passwordVal)) {
      password_input.error = getString(R.string.error_field_required)
      focusView = password_input
      cancel = true
    }

    if (TextUtils.isEmpty(usernameVal)) {
      username_input.error = getString(R.string.error_field_required)
      focusView = username_input
      cancel = true
    }

    if (cancel) {
      // There was an error; don't attempt login and focus the first
      // form field with an error.
      focusView!!.requestFocus()
    } else {
      // Show a progress spinner, and kick off a background task to
      // perform the user login attempt.
      showProgress(true)
      mAuthTask = UserLoginTask(usernameVal, passwordVal)
      mAuthTask!!.execute(null as Void?)
    }
  }

  /**
   * Shows the progress UI and hides the login form.
   */
  @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
  private fun showProgress(show: Boolean) {
    val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)

    mLoginFormView!!.visibility = if (show) View.GONE else View.VISIBLE
    mLoginFormView!!.animate().setDuration(shortAnimTime.toLong()).alpha(
        (if (show) 0 else 1).toFloat()).setListener(object : AnimatorListenerAdapter() {
      override fun onAnimationEnd(animation: Animator) {
        mLoginFormView!!.visibility = if (show) View.GONE else View.VISIBLE
      }
    })

    mProgressView!!.visibility = if (show) View.VISIBLE else View.GONE
    mProgressView!!.animate().setDuration(shortAnimTime.toLong()).alpha(
        (if (show) 1 else 0).toFloat()).setListener(object : AnimatorListenerAdapter() {
      override fun onAnimationEnd(animation: Animator) {
        mProgressView!!.visibility = if (show) View.VISIBLE else View.GONE
      }
    })
  }

  /**
   * Represents an asynchronous login/registration task used to authenticate
   * the user.
   */
  inner class UserLoginTask internal constructor(private val username: String, private val passwordVal: String) : AsyncTask<Void, Void, Boolean>() {

    override fun doInBackground(vararg params: Void): Boolean? {
      try {
        return API.login(username, passwordVal,
            PreferenceManager.getDefaultSharedPreferences(applicationContext))
      } catch (e: IOException) {
        e.printStackTrace()
        return false
      }

    }

    override fun onPostExecute(success: Boolean) {
      mAuthTask = null
      showProgress(false)

      if (success) {
        val preferences = PreferenceManager
            .getDefaultSharedPreferences(applicationContext)
        preferences.edit().putString("username", username).apply()
        val i = Intent(this@LoginActivity, MainActivity::class.java)
        startActivity(i)
        finish()
      } else {
        password_input.error = getString(R.string.error_incorrect_password)
        password_input.requestFocus()
      }
    }

    override fun onCancelled() {
      mAuthTask = null
      showProgress(false)
    }
  }
}

