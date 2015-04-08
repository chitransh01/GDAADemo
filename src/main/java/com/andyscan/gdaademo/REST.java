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

import android.os.AsyncTask;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

final class REST { private REST() {}
  private static Drive mGOOSvc;
  private static MainActivity mAct;
  private static boolean mConnected;

  /************************************************************************************************
   * initialize Google Drive Api
   * @param ctx   activity context
   * @param email  GOO account
   */
  static boolean initDrive(MainActivity ctx, String email){    UT.lg( "initDrive REST " + email);
    if (ctx != null && email != null) try {
      mAct = ctx;
      mGOOSvc = new com.google.api.services.drive.Drive.Builder(
      AndroidHttp.newCompatibleTransport(), new GsonFactory(),
      GoogleAccountCredential
        .usingOAuth2(ctx.getApplicationContext(), Arrays.asList(DriveScopes.DRIVE_FILE))
        .setSelectedAccountName(email)
      ).build();
      return true;
    } catch (Exception e) {UT.le(e);}
    return false;
  }

  /**
   * connect / disconnect
   */
  static void connect() {
    if (mGOOSvc != null) {
      mConnected = false;
      new AsyncTask<Void, Void, UserRecoverableAuthIOException>(){
        @Override
        protected UserRecoverableAuthIOException doInBackground(Void... nadas) {
          try {
            mGOOSvc.files().get("root").setFields("title").execute();
            mConnected = true;
          }
          catch (UserRecoverableAuthIOException uraIOEx) {
            return uraIOEx;
          }
          catch (IOException e) {   // '404 not found' in FILE scope, consider connected
            if (e instanceof GoogleJsonResponseException) {
              if (404 == ((GoogleJsonResponseException)e).getStatusCode())
                mConnected = true;
            }
          } catch (Exception e) { UT.le(e); }
          return null;
        }

        @Override
        protected void onPostExecute(UserRecoverableAuthIOException uraIOEx) {
          super.onPostExecute(uraIOEx);
          if (mConnected)
            mAct.onConnected(null);
          else {
            mAct.onRESTConnFail(uraIOEx == null ? null : uraIOEx.getIntent());
          }
        }
      }.execute();
    }
  }

  /************************************************************************************************
   * find file/folder in GOODrive
   * @param prnId   parent ID (optional), null searches full drive, "root" searches Drive root
   * @param titl    file/folder name (optional)
   * @param mime    file/folder mime type (optional)
   * @return        arraylist of found objects
   */
  static ArrayList<UT.GF> search(String prnId, String titl, String mime) {
    ArrayList<UT.GF> gfs = new ArrayList<>();
    if (mGOOSvc != null && mConnected) try {
      // add query conditions, build query
      String qryClause = "'me' in owners and ";
      if (prnId != null) qryClause += "'" + prnId + "' in parents and ";
      if (titl != null) qryClause += "title = '" + titl + "' and ";
      if (mime != null) qryClause += "mimeType = '" + mime + "' and ";
      qryClause = qryClause.substring(0, qryClause.length() - " and ".length());
      try {
        Drive.Files.List qry = mGOOSvc.files().list().setQ(qryClause)
                                    .setFields("items(id, labels/trashed, title), nextPageToken");
        String npTok = null;
        if (qry != null) do {
          FileList gLst = qry.execute();
          if (gLst != null) {
            for (File gFl : gLst.getItems()) {
              if (gFl.getLabels().getTrashed()) continue;
              gfs.add(new UT.GF(gFl.getTitle(), gFl.getId()));
            }                                           //else UT.lg("failed " + gFl.getTitle());
            npTok = gLst.getNextPageToken();
            qry.setPageToken(npTok);
          }
        } while (npTok != null && npTok.length() > 0);         //UT.lg("found " + vlss.size());
      } catch (Exception e) { UT.le(e); }
    } catch (Exception e) { UT.le(e); }
    return gfs;
  }
  /************************************************************************************************
   * create file/folder in GOODrive
   * @param prnId  parent's ID, (null or "root") for root
   * @param titl  file name
   * @param mime  file mime type
   * @param buf   file content (optional, if null, create folder)
   * @return      file id  / null on fail
   */
  static String create(String prnId, String titl, String mime, byte[] buf) {
    String rsid = null;
    if (mGOOSvc != null && mConnected && titl != null) try {
      File meta = new File();
      meta.setParents(Arrays.asList(new ParentReference().setId(prnId == null ? "root" : prnId)));
      meta.setTitle(titl);

      File gFl = null;
      if (buf != null) {  // create file
        if (mime != null) {   // file must have mime
          meta.setMimeType(mime);
          java.io.File jvFl;
          try {
            jvFl =  UT.bytes2File(buf,
            java.io.File.createTempFile(UT.TMP_FILENM, null, UT.acx.getCacheDir()));
            gFl = mGOOSvc.files().insert(meta, new FileContent(mime, jvFl)).execute();
          } catch (Exception e) { UT.le(e); }
          if (gFl != null && gFl.getId() != null) {
            rsid = gFl.getId();
          }
        }
      } else {    // create folder
        meta.setMimeType(UT.MIME_FLDR);
        try { gFl = mGOOSvc.files().insert(meta).execute();
        } catch (Exception e) { UT.le(e); }
        if (gFl != null && gFl.getId() != null) {
          rsid = gFl.getId();
        }
      }
    } catch (Exception e) { UT.le(e); }
    return rsid;
  }
  /************************************************************************************************
   * get file contents
   * @param resId  file driveId
   * @return       file's content  / null on fail
   */
  static byte[] read(String resId) {
    byte[] buf = null;
    if (mGOOSvc != null && mConnected && resId != null) try {
      File gFl = mGOOSvc.files().get(resId).setFields("downloadUrl").execute();
      if (gFl != null){
        String strUrl = gFl.getDownloadUrl();
        InputStream is = mGOOSvc.getRequestFactory()
        .buildGetRequest(new GenericUrl(strUrl)).execute().getContent();
        buf = UT.is2Bytes(is);
      }
    } catch (Exception e) { UT.le(e); }
    return buf;
  }
  /************************************************************************************************
   * update file in GOODrive
   * @param resId  file  id
   * @param titl  new file name (optional)
   * @param mime  new file mime type (optional, null or MIME_FLDR indicates folder)
   * @param buf   new file content (optional)
   * @return      success status
   */
  static boolean update(String resId, String titl, String mime, String desc, byte[] buf){
    Boolean bOK = false;
    java.io.File jvFl = null;
    if (mGOOSvc != null && mConnected && resId != null) try {
      File body = new File();
      if (titl != null) body.setTitle(titl);
      if (mime != null) body.setMimeType(mime);
      if (desc != null) body.setDescription(desc);
      jvFl = UT.bytes2File(buf,
      java.io.File.createTempFile(UT.TMP_FILENM, null, UT.acx.getCacheDir()));
      FileContent cont = jvFl != null ? new FileContent(mime, jvFl) : null;
      File gFl = (cont == null) ? mGOOSvc.files().patch(resId, body).execute() :
      mGOOSvc.files().update(resId, body, cont).setOcr(false).execute();
      bOK = gFl != null && gFl.getId() != null;
    } catch (Exception e) { UT.le(e);  }
    finally { if (jvFl != null) jvFl.delete(); }
    return bOK;
  }
  /************************************************************************************************
   * trash file in GOODrive
   * @param resId  file  id
   * @return       success status
   */
  static boolean delete(String resId) {
    if (mGOOSvc != null && mConnected && resId != null) try {
      return null != mGOOSvc.files().trash(resId).execute();
    } catch (Exception e) {UT.le(e);}
    return false;
  }

}

