package com.tapnix.keyboard.database.daos;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.tapnix.keyboard.database.entities.EmojiEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class EmojiDao_Impl implements EmojiDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<EmojiEntity> __insertionAdapterOfEmojiEntity;

  private final SharedSQLiteStatement __preparedStmtOfIncrementUse;

  private final SharedSQLiteStatement __preparedStmtOfSetFavorite;

  public EmojiDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfEmojiEntity = new EntityInsertionAdapter<EmojiEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `emoji_history` (`unicode`,`name`,`use_count`,`is_favorite`,`last_used`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EmojiEntity entity) {
        statement.bindString(1, entity.getUnicode());
        statement.bindString(2, entity.getName());
        statement.bindLong(3, entity.getUseCount());
        final int _tmp = entity.isFavorite() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindLong(5, entity.getLastUsed());
      }
    };
    this.__preparedStmtOfIncrementUse = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE emoji_history\n"
                + "        SET use_count = use_count + 1, last_used = ?\n"
                + "        WHERE unicode = ?\n"
                + "    ";
        return _query;
      }
    };
    this.__preparedStmtOfSetFavorite = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE emoji_history SET is_favorite = ? WHERE unicode = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final EmojiEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfEmojiEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object incrementUse(final String unicode, final long ts,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfIncrementUse.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, ts);
        _argIndex = 2;
        _stmt.bindString(_argIndex, unicode);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfIncrementUse.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setFavorite(final String unicode, final boolean fav,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetFavorite.acquire();
        int _argIndex = 1;
        final int _tmp = fav ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindString(_argIndex, unicode);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetFavorite.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<EmojiEntity>> observeRecent() {
    final String _sql = "SELECT * FROM emoji_history ORDER BY last_used DESC LIMIT 30";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"emoji_history"}, new Callable<List<EmojiEntity>>() {
      @Override
      @NonNull
      public List<EmojiEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUnicode = CursorUtil.getColumnIndexOrThrow(_cursor, "unicode");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfUseCount = CursorUtil.getColumnIndexOrThrow(_cursor, "use_count");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "is_favorite");
          final int _cursorIndexOfLastUsed = CursorUtil.getColumnIndexOrThrow(_cursor, "last_used");
          final List<EmojiEntity> _result = new ArrayList<EmojiEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EmojiEntity _item;
            final String _tmpUnicode;
            _tmpUnicode = _cursor.getString(_cursorIndexOfUnicode);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final int _tmpUseCount;
            _tmpUseCount = _cursor.getInt(_cursorIndexOfUseCount);
            final boolean _tmpIsFavorite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp != 0;
            final long _tmpLastUsed;
            _tmpLastUsed = _cursor.getLong(_cursorIndexOfLastUsed);
            _item = new EmojiEntity(_tmpUnicode,_tmpName,_tmpUseCount,_tmpIsFavorite,_tmpLastUsed);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<EmojiEntity>> observeFavorites() {
    final String _sql = "SELECT * FROM emoji_history WHERE is_favorite = 1 ORDER BY use_count DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"emoji_history"}, new Callable<List<EmojiEntity>>() {
      @Override
      @NonNull
      public List<EmojiEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUnicode = CursorUtil.getColumnIndexOrThrow(_cursor, "unicode");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfUseCount = CursorUtil.getColumnIndexOrThrow(_cursor, "use_count");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "is_favorite");
          final int _cursorIndexOfLastUsed = CursorUtil.getColumnIndexOrThrow(_cursor, "last_used");
          final List<EmojiEntity> _result = new ArrayList<EmojiEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EmojiEntity _item;
            final String _tmpUnicode;
            _tmpUnicode = _cursor.getString(_cursorIndexOfUnicode);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final int _tmpUseCount;
            _tmpUseCount = _cursor.getInt(_cursorIndexOfUseCount);
            final boolean _tmpIsFavorite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp != 0;
            final long _tmpLastUsed;
            _tmpLastUsed = _cursor.getLong(_cursorIndexOfLastUsed);
            _item = new EmojiEntity(_tmpUnicode,_tmpName,_tmpUseCount,_tmpIsFavorite,_tmpLastUsed);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getByUnicode(final String unicode,
      final Continuation<? super EmojiEntity> $completion) {
    final String _sql = "SELECT * FROM emoji_history WHERE unicode = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, unicode);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<EmojiEntity>() {
      @Override
      @Nullable
      public EmojiEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUnicode = CursorUtil.getColumnIndexOrThrow(_cursor, "unicode");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfUseCount = CursorUtil.getColumnIndexOrThrow(_cursor, "use_count");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "is_favorite");
          final int _cursorIndexOfLastUsed = CursorUtil.getColumnIndexOrThrow(_cursor, "last_used");
          final EmojiEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpUnicode;
            _tmpUnicode = _cursor.getString(_cursorIndexOfUnicode);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final int _tmpUseCount;
            _tmpUseCount = _cursor.getInt(_cursorIndexOfUseCount);
            final boolean _tmpIsFavorite;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp != 0;
            final long _tmpLastUsed;
            _tmpLastUsed = _cursor.getLong(_cursorIndexOfLastUsed);
            _result = new EmojiEntity(_tmpUnicode,_tmpName,_tmpUseCount,_tmpIsFavorite,_tmpLastUsed);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
