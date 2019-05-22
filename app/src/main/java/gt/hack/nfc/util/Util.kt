package gt.hack.nfc.util


import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import gt.hack.nfc.fragment.UserFragment

object Util {
  val DEFAULT_SERVER = "https://checkin.hack.gt"
  // Whether to make the tag read-only by default in production
  var nfcLockEnabled = true

  fun getValueOfQuestion(questions: List<UserFragment.Question>, name: String): String? {
    for (q in questions) {
      if (q.name() == name) {
        return q.value()
      }
    }
    return null
  }

  fun makeSnackbar(view: View, resId: Int, duration: Int): Snackbar {
    val snackbar = Snackbar.make(view, resId, duration)
    // Hack to show snackbar animation even when an accessibility service like Nova or Microsoft Launcher is enabled;
    try {
      val mAccessibilityManagerField = BaseTransientBottomBar::class.java.getDeclaredField("mAccessibilityManager")
      mAccessibilityManagerField.isAccessible = true
      val accessibilityManager = mAccessibilityManagerField.get(snackbar) as AccessibilityManager
      val mIsEnabledField = AccessibilityManager::class.java.getDeclaredField("mIsEnabled")
      mIsEnabledField.isAccessible = true
      mIsEnabledField.setBoolean(accessibilityManager, false)
      mAccessibilityManagerField.set(snackbar, accessibilityManager)
    } catch (e: Exception) {
      Log.d("Snackbar", "Reflection error: $e")
    }

    return snackbar
  }

  fun hideSoftKeyboard(view: View, context: Context) {
    val inputMethodManager = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
  }
}
