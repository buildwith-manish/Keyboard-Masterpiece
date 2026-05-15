package com.tapnix.keyboard.database;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.tapnix.keyboard.database.daos.BigramDao;
import com.tapnix.keyboard.database.daos.BigramDao_Impl;
import com.tapnix.keyboard.database.daos.ClipboardDao;
import com.tapnix.keyboard.database.daos.ClipboardDao_Impl;
import com.tapnix.keyboard.database.daos.EmojiDao;
import com.tapnix.keyboard.database.daos.EmojiDao_Impl;
import com.tapnix.keyboard.database.daos.WordDao;
import com.tapnix.keyboard.database.daos.WordDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TapNixDatabase_Impl extends TapNixDatabase {
  private volatile ClipboardDao _clipboardDao;

  private volatile EmojiDao _emojiDao;

  private volatile WordDao _wordDao;

  private volatile BigramDao _bigramDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `clipboard` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `preview` TEXT NOT NULL, `full_text` TEXT NOT NULL, `is_pinned` INTEGER NOT NULL, `label` TEXT, `created_at` INTEGER NOT NULL, `size_chars` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_clipboard_is_pinned` ON `clipboard` (`is_pinned`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_clipboard_created_at` ON `clipboard` (`created_at`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `emoji_history` (`unicode` TEXT NOT NULL, `name` TEXT NOT NULL, `use_count` INTEGER NOT NULL, `is_favorite` INTEGER NOT NULL, `last_used` INTEGER NOT NULL, PRIMARY KEY(`unicode`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_emoji_history_last_used` ON `emoji_history` (`last_used`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `word_frequency` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `word` TEXT NOT NULL, `frequency` INTEGER NOT NULL, `last_used` INTEGER NOT NULL, `language` TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_word_frequency_word_language` ON `word_frequency` (`word`, `language`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_frequency_last_used` ON `word_frequency` (`last_used`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `bigrams` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `word1` TEXT NOT NULL, `word2` TEXT NOT NULL, `frequency` INTEGER NOT NULL, `last_used` INTEGER NOT NULL, `language` TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bigrams_word1_language` ON `bigrams` (`word1`, `language`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bigrams_word1_word2_language` ON `bigrams` (`word1`, `word2`, `language`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bigrams_last_used` ON `bigrams` (`last_used`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7f64dc588a18bbe4a01cbcd8c8c45478')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `clipboard`");
        db.execSQL("DROP TABLE IF EXISTS `emoji_history`");
        db.execSQL("DROP TABLE IF EXISTS `word_frequency`");
        db.execSQL("DROP TABLE IF EXISTS `bigrams`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsClipboard = new HashMap<String, TableInfo.Column>(7);
        _columnsClipboard.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsClipboard.put("preview", new TableInfo.Column("preview", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsClipboard.put("full_text", new TableInfo.Column("full_text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsClipboard.put("is_pinned", new TableInfo.Column("is_pinned", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsClipboard.put("label", new TableInfo.Column("label", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsClipboard.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsClipboard.put("size_chars", new TableInfo.Column("size_chars", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysClipboard = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesClipboard = new HashSet<TableInfo.Index>(2);
        _indicesClipboard.add(new TableInfo.Index("index_clipboard_is_pinned", false, Arrays.asList("is_pinned"), Arrays.asList("ASC")));
        _indicesClipboard.add(new TableInfo.Index("index_clipboard_created_at", false, Arrays.asList("created_at"), Arrays.asList("ASC")));
        final TableInfo _infoClipboard = new TableInfo("clipboard", _columnsClipboard, _foreignKeysClipboard, _indicesClipboard);
        final TableInfo _existingClipboard = TableInfo.read(db, "clipboard");
        if (!_infoClipboard.equals(_existingClipboard)) {
          return new RoomOpenHelper.ValidationResult(false, "clipboard(com.tapnix.keyboard.database.entities.ClipboardEntity).\n"
                  + " Expected:\n" + _infoClipboard + "\n"
                  + " Found:\n" + _existingClipboard);
        }
        final HashMap<String, TableInfo.Column> _columnsEmojiHistory = new HashMap<String, TableInfo.Column>(5);
        _columnsEmojiHistory.put("unicode", new TableInfo.Column("unicode", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmojiHistory.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmojiHistory.put("use_count", new TableInfo.Column("use_count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmojiHistory.put("is_favorite", new TableInfo.Column("is_favorite", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmojiHistory.put("last_used", new TableInfo.Column("last_used", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEmojiHistory = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesEmojiHistory = new HashSet<TableInfo.Index>(1);
        _indicesEmojiHistory.add(new TableInfo.Index("index_emoji_history_last_used", false, Arrays.asList("last_used"), Arrays.asList("ASC")));
        final TableInfo _infoEmojiHistory = new TableInfo("emoji_history", _columnsEmojiHistory, _foreignKeysEmojiHistory, _indicesEmojiHistory);
        final TableInfo _existingEmojiHistory = TableInfo.read(db, "emoji_history");
        if (!_infoEmojiHistory.equals(_existingEmojiHistory)) {
          return new RoomOpenHelper.ValidationResult(false, "emoji_history(com.tapnix.keyboard.database.entities.EmojiEntity).\n"
                  + " Expected:\n" + _infoEmojiHistory + "\n"
                  + " Found:\n" + _existingEmojiHistory);
        }
        final HashMap<String, TableInfo.Column> _columnsWordFrequency = new HashMap<String, TableInfo.Column>(5);
        _columnsWordFrequency.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWordFrequency.put("word", new TableInfo.Column("word", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWordFrequency.put("frequency", new TableInfo.Column("frequency", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWordFrequency.put("last_used", new TableInfo.Column("last_used", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWordFrequency.put("language", new TableInfo.Column("language", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWordFrequency = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesWordFrequency = new HashSet<TableInfo.Index>(2);
        _indicesWordFrequency.add(new TableInfo.Index("index_word_frequency_word_language", true, Arrays.asList("word", "language"), Arrays.asList("ASC", "ASC")));
        _indicesWordFrequency.add(new TableInfo.Index("index_word_frequency_last_used", false, Arrays.asList("last_used"), Arrays.asList("ASC")));
        final TableInfo _infoWordFrequency = new TableInfo("word_frequency", _columnsWordFrequency, _foreignKeysWordFrequency, _indicesWordFrequency);
        final TableInfo _existingWordFrequency = TableInfo.read(db, "word_frequency");
        if (!_infoWordFrequency.equals(_existingWordFrequency)) {
          return new RoomOpenHelper.ValidationResult(false, "word_frequency(com.tapnix.keyboard.database.entities.WordFrequencyEntity).\n"
                  + " Expected:\n" + _infoWordFrequency + "\n"
                  + " Found:\n" + _existingWordFrequency);
        }
        final HashMap<String, TableInfo.Column> _columnsBigrams = new HashMap<String, TableInfo.Column>(6);
        _columnsBigrams.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBigrams.put("word1", new TableInfo.Column("word1", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBigrams.put("word2", new TableInfo.Column("word2", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBigrams.put("frequency", new TableInfo.Column("frequency", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBigrams.put("last_used", new TableInfo.Column("last_used", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBigrams.put("language", new TableInfo.Column("language", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBigrams = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBigrams = new HashSet<TableInfo.Index>(3);
        _indicesBigrams.add(new TableInfo.Index("index_bigrams_word1_language", false, Arrays.asList("word1", "language"), Arrays.asList("ASC", "ASC")));
        _indicesBigrams.add(new TableInfo.Index("index_bigrams_word1_word2_language", true, Arrays.asList("word1", "word2", "language"), Arrays.asList("ASC", "ASC", "ASC")));
        _indicesBigrams.add(new TableInfo.Index("index_bigrams_last_used", false, Arrays.asList("last_used"), Arrays.asList("ASC")));
        final TableInfo _infoBigrams = new TableInfo("bigrams", _columnsBigrams, _foreignKeysBigrams, _indicesBigrams);
        final TableInfo _existingBigrams = TableInfo.read(db, "bigrams");
        if (!_infoBigrams.equals(_existingBigrams)) {
          return new RoomOpenHelper.ValidationResult(false, "bigrams(com.tapnix.keyboard.database.entities.BigramEntity).\n"
                  + " Expected:\n" + _infoBigrams + "\n"
                  + " Found:\n" + _existingBigrams);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "7f64dc588a18bbe4a01cbcd8c8c45478", "0a44ed0db0e5d9424aa9fb600acca130");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "clipboard","emoji_history","word_frequency","bigrams");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `clipboard`");
      _db.execSQL("DELETE FROM `emoji_history`");
      _db.execSQL("DELETE FROM `word_frequency`");
      _db.execSQL("DELETE FROM `bigrams`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(ClipboardDao.class, ClipboardDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(EmojiDao.class, EmojiDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(WordDao.class, WordDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(BigramDao.class, BigramDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public ClipboardDao clipboardDao() {
    if (_clipboardDao != null) {
      return _clipboardDao;
    } else {
      synchronized(this) {
        if(_clipboardDao == null) {
          _clipboardDao = new ClipboardDao_Impl(this);
        }
        return _clipboardDao;
      }
    }
  }

  @Override
  public EmojiDao emojiDao() {
    if (_emojiDao != null) {
      return _emojiDao;
    } else {
      synchronized(this) {
        if(_emojiDao == null) {
          _emojiDao = new EmojiDao_Impl(this);
        }
        return _emojiDao;
      }
    }
  }

  @Override
  public WordDao wordDao() {
    if (_wordDao != null) {
      return _wordDao;
    } else {
      synchronized(this) {
        if(_wordDao == null) {
          _wordDao = new WordDao_Impl(this);
        }
        return _wordDao;
      }
    }
  }

  @Override
  public BigramDao bigramDao() {
    if (_bigramDao != null) {
      return _bigramDao;
    } else {
      synchronized(this) {
        if(_bigramDao == null) {
          _bigramDao = new BigramDao_Impl(this);
        }
        return _bigramDao;
      }
    }
  }
}
