package com.skate3rust.android;

import android.app.Activity;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;

/** Opens Android document-provider content without converting a URI into a filesystem path. */
final class ImportSource {
    static final class Handle implements AutoCloseable {
        final InputStream stream;final long size;
        Handle(InputStream stream,long size){this.stream=stream;this.size=size;}
        public void close()throws IOException{stream.close();}
    }
    static Handle open(Context context,Uri uri)throws IOException {
        long size=-1;
        try(AssetFileDescriptor descriptor=context.getContentResolver().openAssetFileDescriptor(uri,"r")) {
            if(descriptor!=null)size=descriptor.getLength()>=0?descriptor.getLength():descriptor.getDeclaredLength();
        }
        InputStream stream=context.getContentResolver().openInputStream(uri);
        if(stream==null)throw new FileNotFoundException("The selected document provider returned no data.");
        return new Handle(stream,size);
    }
    static String displayName(Context context,Uri uri) {
        try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(cursor!=null&&cursor.moveToFirst())return cursor.getString(0);
        } catch(RuntimeException ignored) {}
        String tail=uri.getLastPathSegment();return tail==null?"Selected file":tail;
    }
    static void retainGrant(Activity activity,int resultCode,Intent result) {
        if(resultCode!=Activity.RESULT_OK||result==null)return;
        int flags=result.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if((flags&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0&&result.getData()!=null) {
            try{activity.getContentResolver().takePersistableUriPermission(result.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        }
    }
    private ImportSource(){}
}

