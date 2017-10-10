package gt.hack.nfc.util;


import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import java.lang.reflect.Field;
import java.util.List;

import gt.hack.nfc.fragment.UserFragment;

public class Util {
    public static String getValueOfQuestion(List<UserFragment.Question> questions, String name) {
        for (UserFragment.Question q : questions) {
            if (q.name().equals(name)) {
                return q.value();
            }
        }
        return null;
    }

    public static Snackbar makeSnackbar(View view, int resId, int duration) {
        Snackbar snackbar = Snackbar.make(view, resId, duration);
        // Hack to show snackbar animation even when an accessibility service like Nova or Microsoft Launcher is enabled;
        try {
            Field mAccessibilityManagerField = BaseTransientBottomBar.class.getDeclaredField("mAccessibilityManager");
            mAccessibilityManagerField.setAccessible(true);
            AccessibilityManager accessibilityManager = (AccessibilityManager) mAccessibilityManagerField.get(snackbar);
            Field mIsEnabledField = AccessibilityManager.class.getDeclaredField("mIsEnabled");
            mIsEnabledField.setAccessible(true);
            mIsEnabledField.setBoolean(accessibilityManager, false);
            mAccessibilityManagerField.set(snackbar, accessibilityManager);
        } catch (Exception e) {
            Log.d("Snackbar", "Reflection error: " + e.toString());
        }

        return snackbar;
    }
}
