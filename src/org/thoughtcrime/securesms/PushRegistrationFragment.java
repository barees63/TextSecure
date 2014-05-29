package org.thoughtcrime.securesms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gcm.GCMRegistrar;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.thoughtcrime.securesms.registration.RegistrationPageFragment;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;
import org.whispersystems.textsecure.util.Util;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class PushRegistrationFragment extends RegistrationPageFragment {
  private static final int PICK_COUNTRY    = 1;
  private static final int DO_REGISTRATION = 2;

  private AsYouTypeFormatter   countryFormatter;
  private ArrayAdapter<String> countrySpinnerAdapter;
  private CompletionListener   pendingCompletionListener = null;
  @InjectView(R.id.country_spinner) Spinner  countrySpinner;
  @InjectView(R.id.country_code)    TextView countryCode;
  @InjectView(R.id.number)          TextView number;

  private MasterSecret masterSecret;

  public PushRegistrationFragment() { }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return inflater.inflate(R.layout.push_registration_fragment, container, false);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    initializeResources();
    initializeSpinner();
    initializeNumber();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == PICK_COUNTRY && resultCode == Activity.RESULT_OK && data != null) {
      this.countryCode.setText(data.getIntExtra("country_code", 1) + "");
      setCountryDisplay(data.getStringExtra("country_name"));
      setCountryFormatter(data.getIntExtra("country_code", 1));
    } if (requestCode == DO_REGISTRATION) {
      if (resultCode == Activity.RESULT_OK && pendingCompletionListener != null) {
        Log.w("PushFragment", "got back ok registration result, returning that all's well.");
        pendingCompletionListener.onComplete();
      } else if (pendingCompletionListener != null) {
        pendingCompletionListener.onCancel();
      }
      pendingCompletionListener = null;
    }
  }

  private void initializeResources() {
    ButterKnife.inject(this, getView());

    this.countryCode.addTextChangedListener(new CountryCodeChangedListener());
    this.number.addTextChangedListener(new NumberChangedListener());
  }

  private void initializeSpinner() {
    this.countrySpinnerAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item);
    this.countrySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    setCountryDisplay(getString(R.string.RegistrationActivity_select_your_country));

    this.countrySpinner.setAdapter(this.countrySpinnerAdapter);
    this.countrySpinner.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
          Intent intent = new Intent(getActivity(), CountrySelectionActivity.class);
          startActivityForResult(intent, PICK_COUNTRY);
        }
        return true;
      }
    });
  }

  private void initializeNumber() {
    String localNumber = org.whispersystems.textsecure.util.Util.getDeviceE164Number(getActivity());

    try {
      if (!Util.isEmpty(localNumber)) {
        PhoneNumberUtil numberUtil                = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber localNumberObject = numberUtil.parse(localNumber, null);

        if (localNumberObject != null) {
          this.countryCode.setText(localNumberObject.getCountryCode()+"");
          this.number.setText(localNumberObject.getNationalNumber()+"");
        }
      }
    } catch (NumberParseException npe) {
      Log.w("CreateAccountActivity", npe);
    }
  }

  private void setCountryDisplay(String value) {
    this.countrySpinnerAdapter.clear();
    this.countrySpinnerAdapter.add(value);
  }

  private void setCountryFormatter(int countryCode) {
    PhoneNumberUtil util = PhoneNumberUtil.getInstance();
    String regionCode    = util.getRegionCodeForCountryCode(countryCode);

    if (regionCode == null) this.countryFormatter = null;
    else                    this.countryFormatter = util.getAsYouTypeFormatter(regionCode);
  }

  private String getConfiguredE164Number() {
    return PhoneNumberFormatter.formatE164(countryCode.getText().toString(),
                                           number.getText().toString());
  }

  @Override
  public void onFinishPage(CompletionListener listener) {
    handlePushRegistration(listener);
  }

  @Override
  public void onSkipPage(CompletionListener listener) {
    handleSkipRegistration();
    listener.onComplete();
  }

  @Override
  public void setMasterSecret(MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
  }

  public void handlePushRegistration(final CompletionListener listener) {
    final Activity self = getActivity();

    TextSecurePreferences.setPromptedPushRegistration(self, true);

    if (Util.isEmpty(countryCode.getText())) {
      Toast.makeText(self.getApplicationContext(),
                     getString(R.string.RegistrationActivity_you_must_specify_your_country_code),
                     Toast.LENGTH_LONG).show();
      return;
    }

    if (Util.isEmpty(number.getText())) {
      Toast.makeText(self.getApplicationContext(),
                     getString(R.string.RegistrationActivity_you_must_specify_your_phone_number),
                     Toast.LENGTH_LONG).show();
      return;
    }

    final String e164number = getConfiguredE164Number();

    if (!PhoneNumberFormatter.isValidNumber(e164number)) {
      Dialogs.showAlertDialog(self,
                              getString(R.string.RegistrationActivity_invalid_number),
                              String.format(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid),
                                            e164number)
                             );
      return;
    }

    try {
      GCMRegistrar.checkDevice(self);
    } catch (UnsupportedOperationException uoe) {
      Dialogs.showAlertDialog(self, getString(R.string.RegistrationActivity_unsupported),
                              getString(R.string.RegistrationActivity_sorry_this_device_is_not_supported_for_data_messaging));
      return;
    }

    AlertDialog.Builder dialog = new AlertDialog.Builder(self);
    dialog.setMessage(String.format(getString(R.string.RegistrationActivity_we_will_now_verify_that_the_following_number_is_associated_with_your_device_s),
                                    PhoneNumberFormatter.getInternationalFormatFromE164(e164number)));
    dialog.setPositiveButton(getString(R.string.RegistrationActivity_continue),
                             new DialogInterface.OnClickListener() {
                               @Override
                               public void onClick(DialogInterface dialog, int which) {
                                 Intent intent = new Intent(self, RegistrationProgressActivity.class);
                                 intent.putExtra("e164number", e164number);
                                 intent.putExtra("master_secret", masterSecret);
                                 pendingCompletionListener = listener;
                                 startActivityForResult(intent, DO_REGISTRATION);
                               }
                             });
    dialog.setNegativeButton(getString(R.string.RegistrationActivity_edit),
                             new DialogInterface.OnClickListener() {
                               @Override
                               public void onClick(DialogInterface dialogInterface, int i) {
                                 listener.onCancel();
                               }
                             });
    dialog.show();
  }

  private class CountryCodeChangedListener implements TextWatcher {
    @Override
    public void afterTextChanged(Editable s) {
      if (Util.isEmpty(s)) {
        setCountryDisplay(getString(R.string.RegistrationActivity_select_your_country));
        countryFormatter = null;
        return;
      }

      int countryCode   = Integer.parseInt(s.toString());
      String regionCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);

      setCountryFormatter(countryCode);
      setCountryDisplay(PhoneNumberFormatter.getRegionDisplayName(regionCode));

      if (!Util.isEmpty(regionCode) && !regionCode.equals("ZZ")) {
        number.requestFocus();
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  }

  private class NumberChangedListener implements TextWatcher {

    @Override
    public void afterTextChanged(Editable s) {
      if (countryFormatter == null)
        return;

      if (Util.isEmpty(s))
        return;

      countryFormatter.clear();

      String number          = s.toString().replaceAll("[^\\d.]", "");
      String formattedNumber = null;

      for (int i=0;i<number.length();i++) {
        formattedNumber = countryFormatter.inputDigit(number.charAt(i));
      }

      if (!s.toString().equals(formattedNumber)) {
        s.replace(0, s.length(), formattedNumber);
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }
  }

  public void handleSkipRegistration() {
    TextSecurePreferences.setPromptedPushRegistration(getActivity(), true);
  }
}

