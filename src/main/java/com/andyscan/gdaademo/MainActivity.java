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

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;

import java.util.ArrayList;

public class MainActivity extends Activity
 implements ConnectionCallbacks, OnConnectionFailedListener {

  static boolean USE_REST = false;

  static final String DIALOG_ERROR = "dialog_error";
  static final String REQUEST_CODE = "request_code";
  static final String TMP_FILE_NAME = "tmp_file_name";

  private static final int REQ_ACCPICK = 1;
  private static final int REQ_AUTH    = 2;
  private static final int REQ_RECOVER = 3;
  private static final int REQ_PHOTO   = 4;


  private static boolean mIsInAuth;
  private static TextView mDispTxt;
  private static String mTmpFlNm;
  private static boolean mBusy;

  @Override
  protected void onCreate(Bundle bundle) { super.onCreate(bundle);
    setContentView(R.layout.activity_main);
    mDispTxt = (TextView)findViewById(R.id.tvDispText);

    UT.init(this);

    if (checkPlayServices() && checkUserAccount()) {
      if (initDrive(this, UT.AM.getActiveEmail()))
        connect();
    }
    if (bundle != null) {
      mTmpFlNm = bundle.getString(TMP_FILE_NAME);
    }

    setTitle(USE_REST ? "REST" : "GDAA");
  }

  @Override
  protected void onSaveInstanceState(Bundle bundle) { super.onSaveInstanceState(bundle);
    bundle.putString(TMP_FILE_NAME, mTmpFlNm);  // we can be killed when in the cam activity
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {

      case R.id.action_scan: {
        Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (it.resolveActivity(UT.acx.getPackageManager()) != null)
          startActivityForResult(it, REQ_PHOTO);
        return true;
      }

      case R.id.action_list: {
        testTree();
        return true;
      }

      case R.id.action_delete: {
        deleteTree();
        return true;
      }

      case R.id.action_account: {
        accPick(false);
        return true;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    switch (request) {

      case REQ_ACCPICK: {                                            UT.lg("ACCPICK");
        String email;
        if (result == Activity.RESULT_OK && data != null) {          UT.lg("ACCPICK ok");
          email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
          if (UT.AM.setEmail(email) == UT.AM.CHANGED) {            UT.lg("ACCPICK changed");
            if (initDrive(this, UT.AM.getActiveEmail()))
              connect();
          }                                                     else UT.lg("ACCPICK same");
        } else {                                               UT.lg("ACCPICK cancel");
          email = UT.AM.getActiveEmail();
          if (email == null)
            suicide(this, R.string.err_noacc);
          else {
            if (initDrive(this, email))
              connect();
          }
        }
        break;
      }

      case REQ_AUTH: case REQ_RECOVER: { // from  GOOPlaySvcs recoverable failure
        mIsInAuth = false;
        if (result == Activity.RESULT_OK) {           UT.lg("AUTH RECOVER ok " + request);
          connect();
        } else if (result == RESULT_CANCELED) {       UT.lg("AUTH RECOVER cancel " + request);
          accPick(true);
        }
        break;
      }

      case REQ_PHOTO: {
        if (result == Activity.RESULT_OK) {                                UT.lg( "scanned");
          final String titl = UT.time2Titl(null);
          if (titl != null) {
            byte[] jpgBuf = UT.bm2Jpg((Bitmap)(data.getExtras()).get("data"), 92);
            createTree(titl, jpgBuf);
          }
        }                                                                 else UT.lg("quit");
        break;
      }
    }
    super.onActivityResult(request, result, data);
  }

  private boolean initDrive(MainActivity act, String emil) {
    return USE_REST ? REST.initDrive(act, emil) : GDAA.initDrive(act, emil);
  }

  private void connect() {
    if (USE_REST)  REST.connect();
    else           GDAA.connect();
  }

  // *** connection callbacks ***********************************************************
  @Override public void onConnectionSuspended(int i) {}
  // both REST and GDAA
  @Override
  public void onConnected(Bundle bundle) {                                 UT.lg("connected ");
    Toast.makeText(this, R.string.msg_connect, Toast.LENGTH_SHORT).show();
  }
  // GDAA only connection fail, no-op on REST
  @Override
  public void onConnectionFailed(ConnectionResult result) {                  UT.lg("failed ");
    if (!mIsInAuth) {
      if (result == null || !result.hasResolution()) {                 UT.lg("no resolution");
        accPick(true);
      } else {                                                        UT.lg("has resolution");
        try {
          mIsInAuth = true;
          result.startResolutionForResult(this, REQ_AUTH);
        } catch (IntentSender.SendIntentException e) {
          suicide(this, R.string.err_auth);
        }
      }
    }
  }
  // REST only connection fail, no-op on GDAA
  void onRESTConnFail(Intent it) {                                     UT.lg("failed ");
    if (!mIsInAuth) {
      if (it == null) {                                              UT.lg("no resolution");
        accPick(true);
      } else {                                                      UT.lg("has resolution");
        mIsInAuth = true;
        startActivityForResult(it, REQ_AUTH);
      }
    }
  }

  private void createTree(final String titl, final byte[] buf) {
    if (titl != null && buf != null && !mBusy) {
      mDispTxt.setText("uploading");
      new AsyncTask<Void, String, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          mBusy = true;
          String rsid = findOrCreateFolder("root", UT.MYROOT);
          if (rsid != null) {
            rsid = findOrCreateFolder(rsid, UT.titl2Month(titl));
            if (rsid != null) {
              String title = titl + UT.JPEG_EXT;
              String id = USE_REST ?
                REST.create(rsid, title, UT.MIME_JPEG, buf):
                GDAA.create(rsid, title, UT.MIME_JPEG, buf);

              if (id != null)
                publishProgress("  created " + title);
              else
                publishProgress("  failed " + title);
            }
          }
          return null;
        }
        private String findOrCreateFolder(String prnt, String titl){
          ArrayList<UT.GF> gfs = USE_REST ?
            REST.search(prnt, titl, UT.MIME_FLDR): GDAA.search(prnt, titl, UT.MIME_FLDR);
          String id, txt;
          if (gfs.size() > 0) {
            txt = "found ";
            id =  gfs.get(0).id;
          } else {
            id = USE_REST ?
            REST.create(prnt, titl, null, null) : GDAA.create(prnt, titl, null, null);
            txt = "created ";
          }
          //return gfs.size() > 0 ? gfs.get(0).id : GDAA.create(prnt, titl, null, null);
          if (id != null)
            txt += titl;
          else
            txt = "FAIL " + titl;
          publishProgress(txt);
          return id;
        }
        protected void onProgressUpdate(String... strings) { super.onProgressUpdate(strings);
          mDispTxt.setText(mDispTxt.getText() + "\n" +  strings[0]);
        }
        @Override
        protected void onPostExecute(Void nada) { super.onPostExecute(nada);
          mDispTxt.setText(mDispTxt.getText() + "\nDONE");
          mBusy = false;
        }
      }.execute();
    }
  }

  private void testTree() {
    if (!mBusy) {
      mDispTxt.setText("downloading");
      new AsyncTask<Void, String, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          mBusy = true;
          ArrayList<UT.GF> gfs0 = USE_REST ?
            REST.search("root", UT.MYROOT, null): GDAA.search("root", UT.MYROOT, null);
          if (gfs0 != null) for (UT.GF gf0 : gfs0) {
            publishProgress(gf0.titl);
            ArrayList<UT.GF> gfs1 = USE_REST ?
              REST.search(gf0.id, null, null) : GDAA.search(gf0.id, null, null);
            if (gfs1 != null) for (UT.GF gf1 : gfs1) {
              publishProgress("  " + gf1.titl);
              ArrayList<UT.GF> gfs2 = USE_REST ?
                REST.search(gf1.id, null, null) : GDAA.search(gf1.id, null, null);
              if (gfs2 != null) for (UT.GF gf2 : gfs2) {
                byte[] buf = USE_REST ?  REST.read(gf2.id) : GDAA.read(gf2.id);
                if (buf != null) {
                  Bitmap bm = UT.jpg2Bmp(buf);
                  if (bm == null)
                    gf2.titl += (", " + (buf.length / 1024) + " kB ");
                  else
                    gf2.titl += (", " + bm.getWidth() + "x" + bm.getHeight());
                } else {
                  gf2.titl += (" failed to download ");
                }
                publishProgress("    " + gf2.titl);
                if (USE_REST)
                  REST.update(gf2.id, null, null, "seen " + UT.time2Titl(null), null);
                else
                  GDAA.update(gf2.id, null, null, "seen " + UT.time2Titl(null), null);
              }
            }
          }
          return null;
        }

        @Override
        protected void onProgressUpdate(String... strings) {
          super.onProgressUpdate(strings);
          mDispTxt.setText(mDispTxt.getText() + "\n" + strings[0]);
        }

        @Override
        protected void onPostExecute(Void nada) {          super.onPostExecute(nada);
          mDispTxt.setText(mDispTxt.getText() + "\nDONE");
          mBusy = false;
        }
      }.execute();
    }
  }

  private void deleteTree() {
    if (!mBusy) {
      mDispTxt.setText("deleting");
      new AsyncTask<Void, String, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          mBusy = true;
          String txt;
          boolean bOK;
          ArrayList<UT.GF> gfs0 = USE_REST ?
            REST.search("root", UT.MYROOT, null) : GDAA.search("root", UT.MYROOT, null);
          if (gfs0 != null) for (UT.GF gf0 : gfs0) {
            ArrayList<UT.GF> gfs1 = USE_REST ?
              REST.search(gf0.id, null, null) :  GDAA.search(gf0.id, null, null);
            if (gfs1 != null) for (UT.GF gf1 : gfs1) {
              ArrayList<UT.GF> gfs2 = USE_REST ?
                REST.search(gf1.id, null, null):GDAA.search(gf1.id, null, null);
              if (gfs2 != null) for (UT.GF gf2 : gfs2) {
                bOK = USE_REST ? REST.delete(gf2.id) : GDAA.delete(gf2.id);
                txt = gf2.titl + (bOK ? " OK" : " FAIL");
                publishProgress("    " + txt);
              }
              bOK = USE_REST ? REST.delete(gf1.id) : GDAA.delete(gf1.id);
              txt = gf1.titl + (bOK ? " OK" : " FAIL");
              publishProgress("  " + txt);
            }
            bOK = USE_REST ? REST.delete(gf0.id) : GDAA.delete(gf0.id);
            txt = gf0.titl + (bOK ? " OK" : " FAIL");
            publishProgress(txt);
          }
          return null;
        }

        @Override
        protected void onProgressUpdate(String... strings) {
          super.onProgressUpdate(strings);
          mDispTxt.setText(mDispTxt.getText() + "\n" + strings[0]);
        }

        @Override
        protected void onPostExecute(Void nada) {
          super.onPostExecute(nada);
          mDispTxt.setText(mDispTxt.getText() + "\nDONE");
          mBusy = false;
        }
      }.execute();
    }
  }


  void suicide(Activity act, int strId) {
    Toast.makeText(act, strId, Toast.LENGTH_LONG).show();
    finish();
  }

  private boolean checkUserAccount() {                                UT.lg("check user acc");
    String email = UT.AM.getActiveEmail();
    if (email == null) { // no active and (multiple or no accounts), go create / pick one
      accPick(true);
      return false;  //------------------>>>
    }
    // active email, or only one registered, skip the picker
    UT.AM.setEmail(email);
    return true;
  }

  private boolean checkPlayServices() {                              UT.lg("check play svcs");
    int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
    if (status != ConnectionResult.SUCCESS) {
      if (GooglePlayServicesUtil.isUserRecoverableError(status)) {
        errorDialog(status, REQ_RECOVER);
      } else {
        suicide(this, R.string.err_auth);
      }
      return false;
    }
    return true;
  }

  private void accPick(boolean bReset) {
    startActivityForResult(
      AccountPicker.newChooseAccountIntent(UT.AM.getBestAccnt(), null,
      new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, true, null, null, null, null),
      REQ_ACCPICK
    );
    if (bReset) {
      UT.AM.removeActiveAccnt();
    }
  }


  private void errorDialog(int errorCode, int requestCode) {
    Bundle args = new Bundle();
    args.putInt(DIALOG_ERROR, errorCode);
    args.putInt(REQUEST_CODE, requestCode);
    ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
    dialogFragment.setArguments(args);
    dialogFragment.show(getFragmentManager(), "errordialog");
  }
  public static class ErrorDialogFragment extends DialogFragment {
    public ErrorDialogFragment() { }
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      int errorCode = getArguments().getInt(DIALOG_ERROR);
      int requestCode = getArguments().getInt(DIALOG_ERROR);
      return GooglePlayServicesUtil.getErrorDialog(errorCode, getActivity(), requestCode);
    }
    @Override
    public void onDismiss(DialogInterface dialog) {
      ((MainActivity)getActivity()).suicide(getActivity(), R.string.err_quit);
    }
  }
}
