package org.boardgamers.wbc;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

public class SearchSuggestionsProvider extends ContentProvider {
  private final String TAG="Content Provider";

  private final String PROVIDER_NAME="org.boardgamers.wbc.SearchSuggestionsProvider";
  private final String TABLE_NAME="tournaments";
  private final String URL="content://"+PROVIDER_NAME+"/"+TABLE_NAME;
  private final Uri CONTENT_URI=Uri.parse(URL);

  private final String[] SEARCH_SUGGEST_COLUMNS=
      {BaseColumns._ID, SearchManager.SUGGEST_COLUMN_TEXT_1};

  private WBCDataDbHelper dbHelper;

  @Override
  public boolean onCreate() {
    Log.d(TAG, "onCreate");

    dbHelper=new WBCDataDbHelper(getContext());

    return true;
  }

  @Override
  public Uri insert(Uri uri, ContentValues arg1) {
    return null;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                      String sortOrder) {
    MatrixCursor matrixCursor=new MatrixCursor(SEARCH_SUGGEST_COLUMNS);

    dbHelper.getReadableDatabase();
    Cursor c=dbHelper.getSearchCursor(selectionArgs[0]);

    String title;
    int id;
    while (c.moveToNext()) {
      id=c.getInt(c.getColumnIndexOrThrow(WBCDataDbHelper.TournamentEntry.COLUMN_NAME_TOURNAMENT_ID));
      title=c.getString(c.getColumnIndexOrThrow(WBCDataDbHelper.TournamentEntry.COLUMN_NAME_TITLE));

      matrixCursor.addRow(new Object[] {id, title,});
    }
    c.close();
    dbHelper.close();

    return matrixCursor;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }

  @Override
  public String getType(Uri uri) {
    return null;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return 0;
  }

}