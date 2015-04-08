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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.MetadataBufferResult;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveFolder.DriveFileResult;
import com.google.android.gms.drive.DriveFolder.DriveFolderResult;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.DriveResource.MetadataResult;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.MetadataChangeSet.Builder;
import com.google.android.gms.drive.query.Filter;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

final class GDAA { private GDAA() {}
  private static GoogleApiClient mGAC;

  /************************************************************************************************
   * initialize Google Drive Api
   * @param ctx   activity context
   * @param email  GOO account
   */
  static boolean initDrive(MainActivity ctx, String email){   UT.lg( "initDrive GDAA " + email);
    if (ctx != null && email != null) try {
      mGAC = new GoogleApiClient.Builder(ctx.getApplicationContext()).addApi(Drive.API)
       .addScope(Drive.SCOPE_FILE).setAccountName(email)
       .addConnectionCallbacks(ctx).addOnConnectionFailedListener(ctx).build();
      return true;
    } catch (Exception e) {UT.le(e);}
    return false;
  }

  /**
   * connect
   */
  static void connect() {
    if (mGAC != null) {
      if (mGAC.isConnected())                                      UT.lg( "connect ");
        mGAC.disconnect();
      mGAC.connect();
    }
  }
  private static boolean isConnected() {return mGAC != null && mGAC.isConnected();}

  /************************************************************************************************
   * find file/folder in GOODrive
   * @param prnId   parent ID (optional), null searches full drive, "root" searches Drive root
   * @param titl    file/folder name (optional)
   * @param mime    file/folder mime type (optional)
   * @return        arraylist of found objects
   */
  static ArrayList<UT.GF> search(String prnId, String titl, String mime) {
    ArrayList<UT.GF> gfs = new ArrayList<>();
    if (mGAC != null && isConnected()) try {
      // add query conditions, build query
      ArrayList<Filter> fltrs = new ArrayList<>();
      if (prnId != null){
        fltrs.add(Filters.in(SearchableField.PARENTS,
        prnId.equalsIgnoreCase("root") ?
          Drive.DriveApi.getRootFolder(mGAC).getDriveId() : DriveId.decodeFromString(prnId)));
      }
      if (titl != null) fltrs.add(Filters.eq(SearchableField.TITLE, titl));
      if (mime != null) fltrs.add(Filters.eq(SearchableField.MIME_TYPE, mime));
      Query qry = new Query.Builder().addFilter(Filters.and(fltrs)).build();

      // fire the query
      MetadataBufferResult rslt = Drive.DriveApi.query(mGAC, qry).await();
      if (rslt.getStatus().isSuccess()) {
        MetadataBuffer mdb = null;
        try {
          mdb = rslt.getMetadataBuffer();
          for (Metadata md : mdb) {
            if (md == null || !md.isDataValid() || md.isTrashed()) continue;
            gfs.add(new UT.GF(md.getTitle(), md.getDriveId().encodeToString()));
          }
        } finally { if (mdb != null) mdb.close(); }
      }
    } catch (Exception e) { UT.le(e); }
    return gfs;
  }
  /************************************************************************************************
   * create file/folder in GOODrive
   * @param prnId  parent's ID, (null or "root") for root
   * @param titl  file name
   * @param mime  file mime type
   * @param buf   file contents  (optional, if null, create folder)
   * @return      file id  / null on fail
   */
  static String create(String prnId, String titl, String mime, byte[] buf) {
    DriveId dId = null;
    if (mGAC != null && isConnected() && titl != null) try {
      DriveFolder pFldr = (prnId == null || prnId.equalsIgnoreCase("root")) ?
      Drive.DriveApi.getRootFolder(mGAC):
      Drive.DriveApi.getFolder(mGAC, DriveId.decodeFromString(prnId));
      if (pFldr == null) return null; //----------------->>>

      MetadataChangeSet meta;
      if (buf != null) {  // create file
        if (mime != null) {   // file must have mime
          DriveContentsResult r1 = Drive.DriveApi.newDriveContents(mGAC).await();
          if (r1 == null || !r1.getStatus().isSuccess()) return null; //-------->>>

          meta = new MetadataChangeSet.Builder().setTitle(titl).setMimeType(mime).build();
          DriveFileResult r2 = pFldr.createFile(mGAC, meta, r1.getDriveContents()).await();
          DriveFile dFil = r2 != null && r2.getStatus().isSuccess() ? r2.getDriveFile() : null;
          if (dFil == null) return null; //---------->>>

          r1 = dFil.open(mGAC, DriveFile.MODE_WRITE_ONLY, null).await();
          if ((r1 != null) && (r1.getStatus().isSuccess())) try {
            Status stts = bytes2Cont(r1.getDriveContents(), buf).commit(mGAC, meta).await();
            if ((stts != null) && stts.isSuccess()) {
              MetadataResult r3 = dFil.getMetadata(mGAC).await();
              if (r3 != null && r3.getStatus().isSuccess()) {
                dId = r3.getMetadata().getDriveId();
              }
            }
          } catch (Exception e) {
            UT.le(e);
          }
        }

      } else {
        meta = new MetadataChangeSet.Builder().setTitle(titl).setMimeType(UT.MIME_FLDR).build();
        DriveFolderResult r1 = pFldr.createFolder(mGAC, meta).await();
        DriveFolder dFld = (r1 != null) && r1.getStatus().isSuccess() ? r1.getDriveFolder() : null;
        if (dFld != null) {
          MetadataResult r2 = dFld.getMetadata(mGAC).await();
          if ((r2 != null) && r2.getStatus().isSuccess()) {
            dId = r2.getMetadata().getDriveId();
          }
        }
      }
    } catch (Exception e) { UT.le(e); }
    return dId == null ? null : dId.encodeToString();
  }
  /************************************************************************************************
   * get file contents
   * @param drvId  file driveId
   * @return       file's content  / null on fail
   */
  static byte[] read(String drvId) {
    byte[] buf = null;
    if (mGAC != null && isConnected() && drvId != null) try {
      DriveFile df = Drive.DriveApi.getFile(mGAC, DriveId.decodeFromString(drvId));
      DriveContentsResult rslt = df.open(mGAC, DriveFile.MODE_READ_ONLY, null).await();
      if ((rslt != null) && rslt.getStatus().isSuccess()) {
        DriveContents cont = rslt.getDriveContents();
        buf = UT.is2Bytes(cont.getInputStream());
        cont.discard(mGAC);    // or cont.commit();  they are equiv if READONLY
      }
    } catch (Exception e) { UT.le(e); }
    return buf;
  }
  /************************************************************************************************
   * update file in GOODrive
   * @param drvId   file  id
   * @param titl  new file name (optional)
   * @param mime  new file mime type (optional, null or MIME_FLDR indicates folder)
   * @param buf   new file contents (optional)
   * @return      success status
   */
  static boolean update(String drvId, String titl, String mime, String desc, byte[] buf){
    Boolean bOK = false;
    if (mGAC != null && isConnected() && drvId != null) try {
      Builder mdBd = new MetadataChangeSet.Builder();
      if (titl != null) mdBd.setTitle(titl);
      if (mime != null) mdBd.setMimeType(mime);
      if (desc != null) mdBd.setDescription(desc);
      MetadataChangeSet meta = mdBd.build();

      if (mime == null || UT.MIME_FLDR.equals(mime)) {
        DriveFolder dFldr = Drive.DriveApi.getFolder(mGAC, DriveId.decodeFromString(drvId));
        MetadataResult r1 = dFldr.updateMetadata(mGAC, meta).await();
        bOK = (r1 != null) && r1.getStatus().isSuccess();

      } else {
        DriveFile dFile = Drive.DriveApi.getFile(mGAC, DriveId.decodeFromString(drvId));
        MetadataResult r1 = dFile.updateMetadata(mGAC, meta).await();
        if ((r1 != null) && r1.getStatus().isSuccess() && buf != null) {
          DriveContentsResult r2 = dFile.open(mGAC, DriveFile.MODE_WRITE_ONLY, null).await();
          if (r2.getStatus().isSuccess()) {
            Status r3 = bytes2Cont(r2.getDriveContents(), buf).commit(mGAC, meta).await();
            bOK = (r3 != null && r3.isSuccess());
          }
        }
      }
    } catch (Exception e) { UT.le(e); }
    return bOK;
  }
  /************************************************************************************************
   * trash file in GOODrive
   * @param drvId  file  id
   * @return       success status
   */
  static boolean delete(String drvId) {
    Boolean bOK = false;
    if (mGAC != null && isConnected() && drvId != null) try {
      DriveId dId = DriveId.decodeFromString(drvId);
      DriveResource driveResource;
      if (dId.getResourceType() == DriveId.RESOURCE_TYPE_FOLDER) {
        driveResource = Drive.DriveApi.getFolder(mGAC, dId);
      } else {
        driveResource = Drive.DriveApi.getFile(mGAC, dId);
      }
      Status rslt = driveResource == null ? null : driveResource.trash(mGAC).await();
      bOK = rslt != null && rslt.isSuccess();
    } catch (Exception e) { UT.le(e); }
    return bOK;
  }

  private static DriveContents bytes2Cont(DriveContents driveContents, byte[] buf) {
    OutputStream os = driveContents.getOutputStream();
    try { os.write(buf);
    } catch (IOException e)  { UT.le(e);}
     finally {
      try { os.close();
      } catch (Exception e) { UT.le(e);}
    }
    return driveContents;
  }
}

/***
 DriveId dId = md.getDriveId();

 // DriveId -> String -> DriveId
 DriveId dId = DriveId.decodeFromString(dId.encodeToString())

 // DriveId -> ResourceId
 ResourceId rsid = dId.getResourceId()

 // ResourceId -> DriveId
 DriveApi.DriveIdResult r = Drive.DriveApi.fetchDriveId(mGAC, rsid).await();
 DriveId dId = (r == null || !r.getStatus().isSuccess()) ? null : r.getDriveId();

***/
