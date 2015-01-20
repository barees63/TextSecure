package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class IntroFragment extends Fragment {

  private static final String ARG_DRAWABLE = "drawable";
  private static final String ARG_TEXT     = "text";

  private int drawable;
  private int text;

  public static IntroFragment newInstance(int drawable, int text) {
    IntroFragment fragment = new IntroFragment();
    Bundle args = new Bundle();
    args.putInt(ARG_DRAWABLE, drawable);
    args.putInt(ARG_TEXT, text);
    fragment.setArguments(args);
    return fragment;
  }

  public IntroFragment() {}

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (getArguments() != null) {
      drawable = getArguments().getInt(ARG_DRAWABLE);
      text     = getArguments().getInt(ARG_TEXT    );
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.color_fragment, container, false);

    ((ImageView)v.findViewById(R.id.watermark)).setImageResource(drawable);
    ((TextView)v.findViewById(R.id.blurb)).setText(text);

    return v;
  }
}
