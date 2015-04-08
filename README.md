# GDAADemo

GDAADemo is a minimal implementation of (S)CRUD functionality applied to both,<br/> 
the Google Drive Android API (GDAA), https://developers.google.com/drive/android, and<br/>
the Google Drive Web APIs (REST) https://developers.google.com/drive/v2/reference  

The usage of REST vs GDAA APis is controlled by the 'USE_REST' boolean in 
MainActivity, and the REST vs GDAA methods have the same signatures. The Drive object
ID, even if represented by a string is not interchangeable between REST and GDAA, since
it represents ResourceId in REST and string form of DriveId in GDAA. (see StackOverflow
29030110). I would not recommend to mix the two interfaces, since there are timing
issues that can't be easily reconciled. 

The demo is very raw, it dumps its output directly to the screen and is intended
mostly as a tool to step through in a debugger. 
 
GDAADemo has 3 functions:\n\n

1/ UPLOAD invokes a camera and the resulting thumbnail is uploaded to
   Google Drive, creating a simple tree directory structure in the process.
    The createTree)() method allows for testing of different CRUD primitives (search,
    create folder, etc…) in the process.
 
2/ DOWNLOAD scans the tree created by the createTree method. If the
   object is a file (jpg), it\'s metadata is updated (description field) and the content
   is downloaded (and dumped) - only the number of bytes and dimensions are reported.

3/ DELETE scans the folder/file tree created by the createTree method and deletes all the
   files and folders in the process\n

You can select different Google accounts. There is an account manager wrapper class (AM) in
the UT (utility) class that handles account switching and MainActivity has the full logic to
handle the OAuth2 authorization and account picking. 

Hope it helps.

IMPORTANT:
In case authorization fails with 'no resolution', indicated by the 'Check Developers Console (SHA1/PackageName)' toast, check the SHA1 of the APK file you're running (see StackOverflow 28532206) and make sure your Developers Console has correct SHA1/PackageName pair and the Consent Screen has a valid email address.
