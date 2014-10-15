package org.thoughtcrime.securesms.database;


import android.content.Context;
import android.os.Environment;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.database.ThreadDatabase.ProgressListener;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class PlaintextBackupExporter {

  public static void exportPlaintextToSd(Context context, MasterSecret masterSecret, ProgressListener progressListener)
      throws NoExternalStorageException, IOException
  {
    verifyExternalStorageForPlaintextExport();
    BufferedWriter writer = new BufferedWriter(new FileWriter(getPlaintextExportDirectoryPath()));
    exportPlaintext(context, masterSecret, writer, progressListener);
  }

  private static void verifyExternalStorageForPlaintextExport() throws NoExternalStorageException {
    if (!Environment.getExternalStorageDirectory().canWrite())
      throw new NoExternalStorageException();
  }

  private static String getPlaintextExportDirectoryPath() {
    File sdDirectory = Environment.getExternalStorageDirectory();
    return sdDirectory.getAbsolutePath() + File.separator + "TextSecurePlaintextBackup.xml";
  }

  public static void exportPlaintext(Context context, MasterSecret masterSecret, BufferedWriter outWriter, ProgressListener progressListener)
      throws IOException
  {
    int count               = DatabaseFactory.getSmsDatabase(context).getMessageCount();
    XmlBackup.Writer writer = new XmlBackup.Writer(outWriter, count);

    SmsMessageRecord record;
    EncryptingSmsDatabase.Reader reader = null;
    int progress                        = 0;
    int skip                            = 0;
    int ROW_LIMIT                       = 500;

    do {
      if (reader != null)
        reader.close();

      reader = DatabaseFactory.getEncryptingSmsDatabase(context).getMessages(masterSecret, skip, ROW_LIMIT);

      while ((record = reader.getNext()) != null) {
        XmlBackup.Sms sms = new XmlBackup.Sms(record.getIndividualRecipient().getNumber(),
                                              record.getDateReceived(),
                                              MmsSmsColumns.Types.translateToSystemBaseType(record.getType()),
                                              null,
                                              record.getDisplayBody().toString(),
                                              record.getDeliveryStatus(),
                                              record.isSecure(),
                                              MmsSmsColumns.Types.isPushType(record.getType()));
        writer.writeItem(sms);
        progressListener.onProgress(progress++, count);
      }

      skip += ROW_LIMIT;
    } while (reader.getCount() > 0);

    writer.close();
  }
}
