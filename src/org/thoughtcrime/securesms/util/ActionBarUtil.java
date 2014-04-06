package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import com.actionbarsherlock.app.ActionBar;

import org.thoughtcrime.securesms.testing.R;

public class ActionBarUtil {

  public static void initializeDefaultActionBar(final Context c, final ActionBar actionBar, final int titleResId) {
    actionBar.setTitle(titleResId);
    initializeDefaultActionBar(c, actionBar);
  }

  public static void initializeDefaultActionBar(final Context c, final ActionBar actionBar, final String title) {
    actionBar.setTitle(title);
    initializeDefaultActionBar(c, actionBar);
  }

  public static void initializeDefaultActionBar(final Context c, final ActionBar actionBar) {
    TypedValue iconResValue = new TypedValue();
    c.getTheme().resolveAttribute(R.attr.actionbar_icon, iconResValue, true);
    int attributeResourceId = iconResValue.resourceId;
    Drawable icon = c.getResources().getDrawable(attributeResourceId);
    actionBar.setIcon(icon);
  }

}
