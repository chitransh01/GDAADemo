package com.andyscan.gdaademo;
/**
 * Copyright 2015 Sean Janson. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final public class UT {  private UT() {}
  private static final String L_TAG = "_";

  static final String MYROOT = "GDAADemoRoot";
  static final String TMP_FILENM = "temp";
  static final String JPEG_EXT = ".jpg";
  static final String MIME_JPEG = "image/jpeg";
  static final String MIME_FLDR = "application/vnd.google-apps.folder";

  static final String TITL_FMT = "yyMMdd-HHmmss";

  static final int BUF_SZ = 2048;

  private static UT        mInst;
  static SharedPreferences pfs;
  static Context           acx;

  static UT init(Context ctx) {
    if (mInst == null) {
      acx = ctx.getApplicationContext();
      pfs = PreferenceManager.getDefaultSharedPreferences(acx);
      mInst = new UT();                                         //lg("img cache " + ccheSz);
    }
    return mInst;
  }

  final static class GF{
    String titl, id;
    GF(String t, String i) { titl = t; id = i;}
  }

  final static class AM{ private AM() {}

    private static final String ACC_NAME = "account_name";
    static final int FAIL = -1;
    static final int UNCHANGED =  0;
    static final int CHANGED = +1;
    private static String mCurrEmail = null;  // cache locally

    /**
     * @return either active or primary registered email on the device
     */
    static Account getBestAccnt() {
      Account acct = email2Accnt(getActiveEmail());
      if (acct == null) {
        Account[] accts =
            AccountManager.get(UT.acx).getAccountsByType(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
        acct = accts == null || accts.length == 0 ? null : accts[0];
      }
      return acct;   // active of primary
    }

    /**
     * @return active email, accounting for possible backdoor (setup) removal
     */
    static String getActiveEmail() {
      if (mCurrEmail == null)
        mCurrEmail = UT.pfs.getString(ACC_NAME, null);

      // email's OK, but the account have been removed (through settings)
      if (UT.AM.email2Accnt(mCurrEmail) == null)
        removeActiveAccnt();

      return mCurrEmail;
    }

    /**
     * Stores an active email in persistent app storage, reporting result
     * @param newEmail new email, optionally null
     * @return FAIL, CHANGED or UNCHANGED (based on the following table)
     * OLD    NEW   SAVED   RESULT
     * ERROR                FAIL
     * null   null  null    FAIL
     * null   new   new     CHANGED
     * old    null  old     UNCHANGED
     * old != new   new     CHANGED
     * old == new   new     UNCHANGED
     */
    static int setEmail(String newEmail) {
      int result = FAIL;  // 0  0

      String prevEmail = getActiveEmail();
      if        ((prevEmail == null) && (newEmail != null)) {
        result = CHANGED;
      } else if ((prevEmail != null) && (newEmail == null)) {
        result = UNCHANGED;
      } else if (prevEmail != null) {  // && newEmail != null) {
        result = prevEmail.equalsIgnoreCase(newEmail) ? UNCHANGED : CHANGED;
      }
      if (result == CHANGED) {
        mCurrEmail = newEmail;
        UT.pfs.edit().putString(ACC_NAME, newEmail).apply();
      }
      return result;
    }
    static void removeActiveAccnt() {
      mCurrEmail = null;
      UT.pfs.edit().remove(ACC_NAME).apply();
    }

    private static Account email2Accnt(String email) {
      if (email != null) {
        for (Account acct :
          AccountManager.get(UT.acx).getAccountsByType(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE)) {
          if (email.equalsIgnoreCase(acct.name)) {
            return acct;
          }
        }
      }
      return null;
    }

  }

  static File bytes2File(byte[] buf, File fl) {
    if (buf == null || fl == null) return null;
    BufferedOutputStream bs = null;
    try {
      bs = new BufferedOutputStream(new FileOutputStream(fl));
      bs.write(buf);
    } catch (Exception e) {le(e);}
    finally {
      if (bs != null) try { bs.close();
      } catch (Exception e) {le(e);}
    }
    return fl;
  }
  static byte[] is2Bytes(InputStream is) {
    byte[] buf = null;
    BufferedInputStream bufIS = null;
    if (is != null) try {
      ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
      bufIS = new BufferedInputStream(is);
      buf = new byte[BUF_SZ];
      int cnt;
      while ((cnt = bufIS.read(buf)) >= 0) {
        byteBuffer.write(buf, 0, cnt);
      }
      buf = byteBuffer.size() > 0 ? byteBuffer.toByteArray() : null;
    } catch (Exception e) {le(e);}
    finally {
      try {
        if (bufIS != null) bufIS.close();
      } catch (Exception e) {le(e);}
    }
    return buf;
  }
  static Bitmap jpg2Bmp(byte[] buf) {
    return buf == null ? null : BitmapFactory.decodeByteArray(buf, 0, buf.length);
  }
  static byte[] bm2Jpg(Bitmap bm, int qual) {
    if (bm != null) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      bm.compress(Bitmap.CompressFormat.JPEG, qual, bos);
      return bos.toByteArray();
    }
    return null;
  }
  static String time2Titl(Long milis) {       // time -> yymmdd-hhmmss
    Date dt =  (milis == null) ? new Date() : (milis >= 0) ? new Date(milis) : null;
    return (dt == null) ? null :  new SimpleDateFormat(TITL_FMT, Locale.US).format(dt);
  }
  static String titl2Month(String titl) {
    return titl == null ? null : ("20"+titl.substring(0,2) + "-" + titl.substring(2,4));
  }

  private static String stack2String(Throwable ex) {
    final Writer result = new StringWriter();
    final PrintWriter printWriter = new PrintWriter(result);
    try {
      ex.printStackTrace(printWriter);
      return result.toString();
    } finally {printWriter.close();}
  }
  static void le(Throwable ex){
    String msg = (ex == null || ex.getMessage() == null) ? "" : ex.getMessage() +"\n";
    try { Log.e(L_TAG, msg  + stack2String(ex)); } catch (Exception ignored) {}
  }
  static void lg(String msg) { if (msg != null) { Log.d(L_TAG, msg); } }

}
