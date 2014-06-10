/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.service;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

import java.util.ArrayList;
import java.util.Locale;

public class SmsListener extends BroadcastReceiver {

  @SuppressLint("InlinedApi") private static final String SMS_RECEIVED_ACTION  = Telephony.Sms.Intents.SMS_RECEIVED_ACTION;
  @SuppressLint("InlinedApi") private static final String SMS_DELIVERED_ACTION = Telephony.Sms.Intents.SMS_DELIVER_ACTION;

  private boolean isExemption(SmsMessage message, String messageBody) {

    // ignore CLASS0 ("flash") messages
    if (message.getMessageClass() == SmsMessage.MessageClass.CLASS_0)
      return true;

    // ignore OTP messages from Sparebank1 (Norwegian bank)
    if (messageBody.startsWith("Sparebank1://otp?")) {
      return true;
    }

    return
        message.getOriginatingAddress().length() < 7 &&
            (messageBody.toUpperCase(Locale.ENGLISH).startsWith("//ANDROID:") || // Sprint Visual Voicemail
                messageBody.startsWith("//BREW:")); //BREW stands for “Binary Runtime Environment for Wireless"
  }

  private SmsMessage getSmsMessageFromIntent(Intent intent) {
    Bundle bundle = intent.getExtras();
    Object[] pdus = (Object[]) bundle.get("pdus");

    if (pdus == null || pdus.length == 0)
      return null;

    return SmsMessage.createFromPdu((byte[]) pdus[0]);
  }

  private String getSmsMessageBodyFromIntent(Intent intent) {
    Bundle bundle = intent.getExtras();
    Object[] pdus = (Object[]) bundle.get("pdus");
    StringBuilder bodyBuilder = new StringBuilder();

    if (pdus == null)
      return null;

    for (Object pdu : pdus)
      bodyBuilder.append(SmsMessage.createFromPdu((byte[]) pdu).getDisplayMessageBody());

    return bodyBuilder.toString();
  }

  private ArrayList<IncomingTextMessage> getAsTextMessages(Intent intent) {
    Object[] pdus                   = (Object[])intent.getExtras().get("pdus");
    ArrayList<IncomingTextMessage> messages = new ArrayList<IncomingTextMessage>(pdus.length);

    for (int i=0;i<pdus.length;i++)
      messages.add(new IncomingTextMessage(SmsMessage.createFromPdu((byte[])pdus[i])));

    return messages;
  }

  private boolean isRelevant(Context context, Intent intent) {
    SmsMessage message = getSmsMessageFromIntent(intent);
    String messageBody = getSmsMessageBodyFromIntent(intent);

    if (message == null && messageBody == null)
      return false;

    if (isExemption(message, messageBody))
      return false;

    if (!ApplicationMigrationService.isDatabaseImported(context))
      return false;

    if (isChallenge(context, intent))
      return false;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
        SMS_RECEIVED_ACTION.equals(intent.getAction()) &&
        Util.isDefaultSmsProvider(context))
    {
      return false;
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT &&
        TextSecurePreferences.isInterceptAllSmsEnabled(context))
    {
      return true;
    }

    return WirePrefix.isEncryptedMessage(messageBody) || WirePrefix.isKeyExchange(messageBody);
  }

  private boolean isChallenge(Context context, Intent intent) {
    String messageBody = getSmsMessageBodyFromIntent(intent);

    if (messageBody == null)
      return false;

    if (messageBody.matches("Your TextSecure verification code: [0-9]{3,4}-[0-9]{3,4}") &&
        TextSecurePreferences.isVerifying(context))
    {
      return true;
    }

    return false;
  }

  private String parseChallenge(Context context, Intent intent) {
    String messageBody    = getSmsMessageBodyFromIntent(intent);
    String[] messageParts = messageBody.split(":");
    String[] codeParts    = messageParts[1].trim().split("-");

    return codeParts[0] + codeParts[1];
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w("SMSListener", "Got SMS broadcast...");

    if (SMS_RECEIVED_ACTION.equals(intent.getAction()) && isChallenge(context, intent)) {
      Log.w("SmsListener", "Got challenge!");
      Intent challengeIntent = new Intent(RegistrationService.CHALLENGE_EVENT);
      challengeIntent.putExtra(RegistrationService.CHALLENGE_EXTRA, parseChallenge(context, intent));
      context.sendBroadcast(challengeIntent);

      abortBroadcast();
    } else if ((intent.getAction().equals(SMS_DELIVERED_ACTION)) ||
               (intent.getAction().equals(SMS_RECEIVED_ACTION)) && isRelevant(context, intent))
    {
      Intent receivedIntent = new Intent(context, SendReceiveService.class);
      receivedIntent.setAction(SendReceiveService.RECEIVE_SMS_ACTION);
      receivedIntent.putExtra("ResultCode", this.getResultCode());
      receivedIntent.putParcelableArrayListExtra("text_messages",getAsTextMessages(intent));
      context.startService(receivedIntent);

      abortBroadcast();
    }
  }
}
