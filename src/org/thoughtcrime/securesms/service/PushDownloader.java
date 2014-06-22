package org.thoughtcrime.securesms.service;


import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.database.AttachmentTransferDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.AttachmentCipherInputStream;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.push.NotFoundException;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.util.Base64;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduPart;

public class PushDownloader {

  private final Context context;

  public PushDownloader(Context context) {
    this.context = context.getApplicationContext();
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    if (!SendReceiveService.DOWNLOAD_PUSH_ACTION.equals(intent.getAction()))
      return;

    long         messageId = intent.getLongExtra("message_id", -1);
    PartDatabase database  = DatabaseFactory.getPartDatabase(context);

    Log.w("PushDownloader", "Downloading push parts for: " + messageId);

    if (messageId != -1) {
      List<Pair<Long, PduPart>> parts = database.getParts(masterSecret, messageId, false);

      for (Pair<Long, PduPart> partPair : parts) {
        retrievePart(masterSecret, partPair.second, messageId, partPair.first);
        Log.w("PushDownloader", "Got part: " + partPair.first);
      }
    } else {
      List<Pair<Long, Pair<Long, PduPart>>> parts = database.getPushPendingParts(masterSecret);

      for (Pair<Long, Pair<Long, PduPart>> partPair : parts) {
        retrievePart(masterSecret, partPair.second.second, partPair.first, partPair.second.first);
        Log.w("PushDownloader", "Got part: " + partPair.second.first);
      }
    }
  }

  private void retrievePart(final MasterSecret masterSecret, final PduPart part, final long messageId, final long partId) {
    PartDatabase database       = DatabaseFactory.getPartDatabase(context);
    File         attachmentFile = null;

    try {
      MasterCipher masterCipher    = new MasterCipher(masterSecret);
      long         contentLocation = Long.parseLong(Util.toIsoString(part.getContentLocation()));
      byte[]       key             = masterCipher.decryptBytes(Base64.decode(Util.toIsoString(part.getContentDisposition())));
      String       relay           = null;

      if (part.getName() != null) {
        relay = Util.toIsoString(part.getName());
      }
      final AttachmentTransferDatabase attachmentDb = AttachmentTransferDatabase.getInstance(context);
      attachmentDb.add(messageId, partId, 0, -1);

      PushServiceSocket.TransferProgressListener listener = new PushServiceSocket.TransferProgressListener() {
        @Override
        public void onProgressUpdate(long downloadedBytes, long totalBytes) {
          attachmentDb.update(messageId, partId, downloadedBytes, totalBytes);
        }
      };

      attachmentFile              = downloadAttachment(relay, contentLocation, listener);
      InputStream attachmentInput = new AttachmentCipherInputStream(attachmentFile, key);

      database.updateDownloadedPart(masterSecret, messageId, partId, part, attachmentInput);
    } catch (NotFoundException e) {
      Log.w("PushDownloader", e);
      try {
        database.updateFailedDownloadedPart(messageId, partId, part);
      } catch (MmsException mme) {
        Log.w("PushDownloader", mme);
      }
    } catch (InvalidMessageException e) {
      Log.w("PushDownloader", e);
      try {
        database.updateFailedDownloadedPart(messageId, partId, part);
      } catch (MmsException mme) {
        Log.w("PushDownloader", mme);
      }
    } catch (MmsException e) {
      Log.w("PushDownloader", e);
      try {
        database.updateFailedDownloadedPart(messageId, partId, part);
      } catch (MmsException mme) {
        Log.w("PushDownloader", mme);
      }
    } catch (IOException e) {
      Log.w("PushDownloader", e);
      /// XXX schedule some kind of soft failure retry action
    } finally {
      if (attachmentFile != null)
        attachmentFile.delete();
    }
  }

  private File downloadAttachment(String relay, long contentLocation, PushServiceSocket.TransferProgressListener listener)
      throws IOException
  {
    PushServiceSocket socket = PushServiceSocketFactory.create(context);
    return socket.retrieveAttachment(relay, contentLocation, listener);
  }

}
