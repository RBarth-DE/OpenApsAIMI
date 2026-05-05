package app.aaps.database.di

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import androidx.room.RoomDatabase.Callback
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.aaps.database.AppDatabase
import app.aaps.database.entities.TABLE_APS_RESULTS
import app.aaps.database.entities.TABLE_AUTOISF_VALUES
import app.aaps.database.entities.TABLE_BOLUSES
import app.aaps.database.entities.TABLE_EFFECTIVE_PROFILE_SWITCHES
import app.aaps.database.entities.TABLE_HEART_RATE
import app.aaps.database.entities.TABLE_TOTAL_DAILY_DOSES
import app.aaps.database.entities.TABLE_PREFERENCE_CHANGES
import app.aaps.database.entities.TABLE_PROFILE_SWITCHES
import app.aaps.database.entities.TABLE_RUNNING_MODE
import app.aaps.database.entities.TABLE_STEPS_COUNT
import app.aaps.database.entities.TABLE_TEMPORARY_BASALS
import app.aaps.database.entities.TABLE_THERAPY_EVENTS
import app.aaps.database.entities.TABLE_USER_ENTRY
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
open class DatabaseModule {

    @DbFileName
    @Provides
    fun dbFileName() = "androidaps.db"

    @Provides
    @Singleton
    internal fun provideAppDatabase(context: Context, @DbFileName fileName: String) =
        Room
            .databaseBuilder(context, AppDatabase::class.java, fileName)
            .addMigrations(*migrations)
            .addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    createCustomIndexes(db)
                }
            })
            .fallbackToDestructiveMigration(false)
            .build()

    @Qualifier
    annotation class DbFileName

    private fun createCustomIndexes(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryBasals_end` ON `temporaryBasals` (`timestamp` + `duration`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_extendedBoluses_end` ON `extendedBoluses` (`timestamp` + `duration`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryTargets_end` ON `temporaryTargets` (`timestamp` + `duration`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_carbs_end` ON `carbs` (`timestamp` + `duration`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_runningModes_end` ON `runningModes` (`timestamp` + `duration`)")
    }

    private fun dropCustomIndexes(database: SupportSQLiteDatabase) {
        database.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_end`")
        database.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_end`")
        database.execSQL("DROP INDEX IF EXISTS `index_temporaryTargets_end`")
        database.execSQL("DROP INDEX IF EXISTS `index_carbs_end`")
        database.execSQL("DROP INDEX IF EXISTS `index_runningModes_end`")
    }

    internal val migration20to21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS offlineEvents")
            db.execSQL("CREATE TABLE IF NOT EXISTS `offlineEvents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `reason` TEXT NOT NULL, `duration` INTEGER NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`referenceId`) REFERENCES `offlineEvents`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_offlineEvents_id` ON offlineEvents (`id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_offlineEvents_isValid` ON offlineEvents (`isValid`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_offlineEvents_nightscoutId` ON offlineEvents (`nightscoutId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_offlineEvents_referenceId` ON offlineEvents (`referenceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_offlineEvents_timestamp` ON offlineEvents (`timestamp`)")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    internal val migration21to22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `carbs` ADD COLUMN `notes` TEXT")
            db.execSQL("ALTER TABLE `boluses` ADD COLUMN `notes` TEXT")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    internal val migration22to23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `deviceStatus` ADD COLUMN `isCharging` INTEGER")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    internal val migration23to24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `$TABLE_HEART_RATE` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `beatsPerMinute` REAL NOT NULL,
                    `device` TEXT NOT NULL,
                    `utcOffset` INTEGER NOT NULL,
                    `version` INTEGER NOT NULL,
                    `dateCreated` INTEGER NOT NULL,
                    `isValid` INTEGER NOT NULL,
                    `referenceId` INTEGER,
                    `nightscoutSystemId` TEXT,
                    `nightscoutId` TEXT,
                    `pumpType` TEXT,
                    `pumpSerial` TEXT,
                    `temporaryId` INTEGER,
                    `pumpId` INTEGER, `startId` INTEGER,
                    `endId` INTEGER)""".trimIndent()
            )
            db.execSQL("""CREATE INDEX IF NOT EXISTS `index_heartRate_id` ON `$TABLE_HEART_RATE` (`id`)""")
            db.execSQL("""CREATE INDEX IF NOT EXISTS `index_heartRate_timestamp` ON `$TABLE_HEART_RATE` (`timestamp`)""")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }
    internal val migration24to25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Creation of table TABLE_STEPS_COUNT
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${TABLE_STEPS_COUNT}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `duration` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `steps5min` INTEGER NOT NULL, `steps10min` INTEGER NOT NULL, `steps15min` INTEGER NOT NULL, `steps30min` INTEGER NOT NULL, `steps60min` INTEGER NOT NULL, `steps180min` INTEGER NOT NULL, `device` TEXT NOT NULL, `utcOffset` INTEGER NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER)"
            )
            // Creation of index for table TABLE_STEPS_COUNT
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stepsCount_id` ON `${TABLE_STEPS_COUNT}` (`id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stepsCount_timestamp` ON `${TABLE_STEPS_COUNT}` (`timestamp`)")

            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    internal val migration25to26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_APS_RESULTS")
            db.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_APS_RESULTS}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `algorithm` TEXT NOT NULL, `glucoseStatusJson` TEXT NOT NULL, `currentTempJson` TEXT NOT NULL, `iobDataJson` TEXT NOT NULL, `profileJson` TEXT NOT NULL, `autosensDataJson` TEXT, `mealDataJson` TEXT NOT NULL, `resultJson` TEXT NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`referenceId`) REFERENCES `apsResults`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_apsResults_referenceId` ON `${TABLE_APS_RESULTS}` (`referenceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_apsResults_timestamp` ON `${TABLE_APS_RESULTS}` (`timestamp`)")
            db.execSQL("DROP TABLE IF EXISTS apsResultLinks")
            db.execSQL("DROP TABLE IF EXISTS multiwaveBolusLinks")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    internal val migration26to27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_APS_RESULTS")
            db.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_APS_RESULTS}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `algorithm` TEXT NOT NULL, `glucoseStatusJson` TEXT, `currentTempJson` TEXT, `iobDataJson` TEXT, `profileJson` TEXT, `autosensDataJson` TEXT, `mealDataJson` TEXT, `resultJson` TEXT NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`referenceId`) REFERENCES `apsResults`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_apsResults_referenceId` ON `${TABLE_APS_RESULTS}` (`referenceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_apsResults_timestamp` ON `${TABLE_APS_RESULTS}` (`timestamp`)")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    internal val migration27to28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM $TABLE_APS_RESULTS")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    internal val migration28to29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_PREFERENCE_CHANGES")
            db.execSQL("CREATE TABLE IF NOT EXISTS `$TABLE_PREFERENCE_CHANGES` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `key` TEXT NOT NULL, `value` TEXT NOT NULL)")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_USER_ENTRY")
            db.execSQL("CREATE TABLE IF NOT EXISTS `$TABLE_USER_ENTRY` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `action` TEXT NOT NULL, `source` TEXT NOT NULL, `note` TEXT NOT NULL, `values` TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_userEntry_source` ON `$TABLE_USER_ENTRY` (`source`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_userEntry_timestamp` ON `$TABLE_USER_ENTRY` (`timestamp`)")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    internal val migration29to30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `offlineEvents`")

            db.execSQL("CREATE TABLE IF NOT EXISTS `$TABLE_RUNNING_MODE` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `mode` TEXT NOT NULL, `duration` INTEGER NOT NULL, `autoForced` INTEGER NOT NULL, `reasons` TEXT, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, FOREIGN KEY(`referenceId`) REFERENCES `runningModes`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_runningModes_id` ON `$TABLE_RUNNING_MODE` (`id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_runningModes_nightscoutId` ON `$TABLE_RUNNING_MODE` (`nightscoutId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_runningModes_referenceId` ON `$TABLE_RUNNING_MODE` (`referenceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_runningModes_timestamp` ON `$TABLE_RUNNING_MODE` (`timestamp`)")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    internal val migration30to31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `$TABLE_THERAPY_EVENTS` ADD COLUMN `location` TEXT")
            db.execSQL("ALTER TABLE `$TABLE_THERAPY_EVENTS` ADD COLUMN `arrow` TEXT")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    internal val migration31to32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Creation of table TABLE_AUTOISF_VALUES
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${TABLE_AUTOISF_VALUES}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `acceIsf` REAL NOT NULL, `bgIsf` REAL NOT NULL, `ppIsf` REAL NOT NULL, `driftIsf` REAL NOT NULL, `duraIsf` REAL NOT NULL, `finalIsf` REAL NOT NULL, `iobThEffective` REAL NOT NULL, `utcOffset` INTEGER NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER)"
            )
            // Creation of index for table TABLE_AUTOISF_VALUES
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_autoIsfValues_id` ON `${TABLE_AUTOISF_VALUES}` (`id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_autoIsfValues_timestamp` ON `${TABLE_AUTOISF_VALUES}` (`timestamp`)")


            // Add carbInsulin column to TDD table — cached data, old rows get default 0 and will be recalculated
            db.execSQL("DELETE FROM $TABLE_TOTAL_DAILY_DOSES")
            db.execSQL("ALTER TABLE `$TABLE_TOTAL_DAILY_DOSES` ADD COLUMN `carbInsulin` REAL NOT NULL DEFAULT 0")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    internal val migration32to33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Migration of boluses table (insulinPeakTime must be migrated in MainApp)
            db.execSQL("CREATE TABLE IF NOT EXISTS new_boluses (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `amount` REAL NOT NULL, `type` TEXT NOT NULL, `notes` TEXT, `isBasalInsulin` INTEGER NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, `insulinLabel` TEXT NOT NULL, `insulinEndTime` INTEGER NOT NULL, `insulinPeakTime` INTEGER NOT NULL, `concentration` REAL NOT NULL, FOREIGN KEY(`referenceId`) REFERENCES `boluses`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            db.execSQL(
                """
                INSERT INTO new_boluses (id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, amount, type, notes, isBasalInsulin, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, insulinLabel, insulinEndTime, insulinPeakTime, concentration)
                SELECT id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, amount, type, notes, isBasalInsulin, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, '', -1, -1, 1.0 
                FROM `$TABLE_BOLUSES`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `$TABLE_BOLUSES`")
            db.execSQL("ALTER TABLE new_boluses RENAME TO `$TABLE_BOLUSES`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_id` ON `$TABLE_BOLUSES` (`id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_pumpId` ON `$TABLE_BOLUSES` (`pumpId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_referenceId` ON `$TABLE_BOLUSES` (`referenceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_timestamp` ON `$TABLE_BOLUSES` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_nightscoutId` ON `$TABLE_BOLUSES` (`nightscoutId`)")

            // Migration of effectiveProfileSwitches table (insulinPeakTime must be migrated in MainApp)
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS new_effectiveProfileSwitches (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `basalBlocks` TEXT NOT NULL, `isfBlocks` TEXT NOT NULL, `icBlocks` TEXT NOT NULL, `targetBlocks` TEXT NOT NULL, `glucoseUnit` TEXT NOT NULL, `originalProfileName` TEXT NOT NULL, `originalCustomizedName` TEXT NOT NULL, `originalTimeshift` INTEGER NOT NULL, `originalPercentage` INTEGER NOT NULL, `originalDuration` INTEGER NOT NULL, `originalEnd` INTEGER NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, `insulinLabel` TEXT NOT NULL, `insulinEndTime` INTEGER NOT NULL, `insulinPeakTime` INTEGER NOT NULL, `concentration` REAL NOT NULL, FOREIGN KEY(`referenceId`) REFERENCES `effectiveProfileSwitches`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
            )
            db.execSQL(
                """
                INSERT INTO new_effectiveProfileSwitches (id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, basalBlocks, isfBlocks, icBlocks, targetBlocks, glucoseUnit, originalProfileName, originalCustomizedName, originalTimeshift, originalPercentage, originalDuration, originalEnd, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, insulinLabel, insulinEndTime, insulinPeakTime, concentration)
                SELECT id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, basalBlocks, isfBlocks, icBlocks, targetBlocks, glucoseUnit, originalProfileName, originalCustomizedName, originalTimeshift, originalPercentage, originalDuration, originalEnd, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, '', -1, -1, 1.0 
                FROM `$TABLE_EFFECTIVE_PROFILE_SWITCHES`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `$TABLE_EFFECTIVE_PROFILE_SWITCHES`")
            db.execSQL("ALTER TABLE new_effectiveProfileSwitches RENAME TO `$TABLE_EFFECTIVE_PROFILE_SWITCHES`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_effectiveProfileSwitches_id` ON `$TABLE_EFFECTIVE_PROFILE_SWITCHES` (`id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_effectiveProfileSwitches_referenceId` ON `$TABLE_EFFECTIVE_PROFILE_SWITCHES` (`referenceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_effectiveProfileSwitches_timestamp` ON `$TABLE_EFFECTIVE_PROFILE_SWITCHES` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_effectiveProfileSwitches_nightscoutId` ON `$TABLE_EFFECTIVE_PROFILE_SWITCHES` (`nightscoutId`)")

            // Migration of profileSwitches table (insulinPeakTime must be migrated in MainApp)
            db.execSQL("CREATE TABLE IF NOT EXISTS new_profileSwitches (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `basalBlocks` TEXT NOT NULL, `isfBlocks` TEXT NOT NULL, `icBlocks` TEXT NOT NULL, `targetBlocks` TEXT NOT NULL, `glucoseUnit` TEXT NOT NULL, `profileName` TEXT NOT NULL, `timeshift` INTEGER NOT NULL, `percentage` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, `insulinLabel` TEXT NOT NULL, `insulinEndTime` INTEGER NOT NULL, `insulinPeakTime` INTEGER NOT NULL, `concentration` REAL NOT NULL, FOREIGN KEY(`referenceId`) REFERENCES `profileSwitches`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            db.execSQL(
                """
                INSERT INTO new_profileSwitches (id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, basalBlocks, isfBlocks, icBlocks, targetBlocks, glucoseUnit, profileName, timeshift, percentage, duration, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, insulinLabel, insulinEndTime, insulinPeakTime, concentration)
                SELECT id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, basalBlocks, isfBlocks, icBlocks, targetBlocks, glucoseUnit, profileName, timeshift, percentage, duration, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, '', -1, -1, 1.0 
                FROM `$TABLE_PROFILE_SWITCHES`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `$TABLE_PROFILE_SWITCHES`")
            db.execSQL("ALTER TABLE new_profileSwitches RENAME TO `$TABLE_PROFILE_SWITCHES`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_profileSwitches_id` ON `$TABLE_PROFILE_SWITCHES` (`id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_profileSwitches_referenceId` ON `$TABLE_PROFILE_SWITCHES` (`referenceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_profileSwitches_timestamp` ON `$TABLE_PROFILE_SWITCHES` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_profileSwitches_nightscoutId` ON `$TABLE_PROFILE_SWITCHES` (`nightscoutId`)")

            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    internal val migration33to34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {

db.execSQL(
                "CREATE TABLE IF NOT EXISTS `${TABLE_AUTOISF_VALUES}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `acceIsf` REAL NOT NULL, `bgIsf` REAL NOT NULL, `ppIsf` REAL NOT NULL, `driftIsf` REAL NOT NULL, `duraIsf` REAL NOT NULL, `finalIsf` REAL NOT NULL, `iobThEffective` REAL NOT NULL, `utcOffset` INTEGER NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER)"
            )
            // Creation of index for table TABLE_AUTOISF_VALUES
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_autoIsfValues_id` ON `${TABLE_AUTOISF_VALUES}` (`id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_autoIsfValues_timestamp` ON `${TABLE_AUTOISF_VALUES}` (`timestamp`)")
            db.execSQL("ALTER TABLE `$TABLE_EFFECTIVE_PROFILE_SWITCHES` ADD COLUMN `originalPsId` INTEGER DEFAULT NULL")
            // Remove redundant indexes on primary key columns
            db.execSQL("DROP INDEX IF EXISTS `index_effectiveProfileSwitches_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_boluses_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_bolusCalculatorResults_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_bolusCalculatorResults_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_carbs_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_carbs_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_pumpSerial`")
            db.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_pumpType`")
            db.execSQL("DROP INDEX IF EXISTS `index_glucoseValues_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_glucoseValues_sourceSensor`")
            db.execSQL("DROP INDEX IF EXISTS `index_profileSwitches_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_pumpType`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_pumpSerial`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryBasals_pumpId` ON `$TABLE_TEMPORARY_BASALS` (`pumpId`)")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryTargets_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryTargets_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_therapyEvents_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_therapyEvents_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_pumpType`")
            db.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_pumpSerial`")
            db.execSQL("DROP INDEX IF EXISTS `index_foods_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_foods_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_deviceStatus_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_runningModes_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_heartRate_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_stepsCount_id`")
            // Custom indexes must be dropped on migration to pass room schema checking after upgrade
            dropCustomIndexes(db)
        }
    }

    // Brings any v34 database (our fork's v34 OR upstream MTR v34) to v35.
    // Uses PRAGMA checks before referencing columns that may be absent on MTR devices.
    internal val migration34to35 = object : Migration(34, 35) {
        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            val cursor = db.query("PRAGMA table_info(`$table`)")
            return cursor.use {
                var found = false
                while (it.moveToNext()) {
                    if (it.getString(it.getColumnIndexOrThrow("name")) == column) { found = true; break }
                }
                found
            }
        }

        override fun migrate(db: SupportSQLiteDatabase) {
            // --- autoIsfValues: create if missing, or recreate to fix DOUBLE→REAL type bug ---
            val autoIsfCreateSql =
                "CREATE TABLE IF NOT EXISTS `${TABLE_AUTOISF_VALUES}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `acceIsf` REAL NOT NULL, `bgIsf` REAL NOT NULL, `ppIsf` REAL NOT NULL, `driftIsf` REAL NOT NULL, `duraIsf` REAL NOT NULL, `finalIsf` REAL NOT NULL, `iobThEffective` REAL NOT NULL, `utcOffset` INTEGER NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER)"
            val autoIsfExists = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='${TABLE_AUTOISF_VALUES}'"
            ).use { it.count > 0 }
            if (autoIsfExists) {
                db.execSQL("ALTER TABLE `${TABLE_AUTOISF_VALUES}` RENAME TO `autoIsfValues_old`")
                db.execSQL(autoIsfCreateSql)
                db.execSQL("INSERT INTO `${TABLE_AUTOISF_VALUES}` SELECT * FROM `autoIsfValues_old`")
                db.execSQL("DROP TABLE `autoIsfValues_old`")
            } else {
                db.execSQL(autoIsfCreateSql)
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_autoIsfValues_id` ON `${TABLE_AUTOISF_VALUES}` (`id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_autoIsfValues_timestamp` ON `${TABLE_AUTOISF_VALUES}` (`timestamp`)")

            // --- totalDailyDoses: add carbInsulin if missing (MTR v34 may not have it) ---
            if (!hasColumn(db, TABLE_TOTAL_DAILY_DOSES, "carbInsulin")) {
                db.execSQL("DELETE FROM `$TABLE_TOTAL_DAILY_DOSES`")
                db.execSQL("ALTER TABLE `$TABLE_TOTAL_DAILY_DOSES` ADD COLUMN `carbInsulin` REAL NOT NULL DEFAULT 0")
            }

            // --- boluses: recreate with nullable insulinConfiguration columns ---
            // Source may have them as NOT NULL (our fork v33 path) or absent (MTR v34 path).
            val bolusHasInsulin = hasColumn(db, TABLE_BOLUSES, "insulinLabel")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `new_boluses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `timestamp` INTEGER NOT NULL, `utcOffset` INTEGER NOT NULL, `amount` REAL NOT NULL, `type` TEXT NOT NULL, `notes` TEXT, `isBasalInsulin` INTEGER NOT NULL, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER, `insulinLabel` TEXT, `insulinEndTime` INTEGER, `insulinPeakTime` INTEGER, `concentration` REAL, FOREIGN KEY(`referenceId`) REFERENCES `boluses`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
            )
            if (bolusHasInsulin) {
                db.execSQL(
                    "INSERT INTO new_boluses (id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, amount, type, notes, isBasalInsulin, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, insulinLabel, insulinEndTime, insulinPeakTime, concentration) SELECT id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, amount, type, notes, isBasalInsulin, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId, insulinLabel, insulinEndTime, insulinPeakTime, concentration FROM `$TABLE_BOLUSES`"
                )
            } else {
                db.execSQL(
                    "INSERT INTO new_boluses (id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, amount, type, notes, isBasalInsulin, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId) SELECT id, version, dateCreated, isValid, referenceId, timestamp, utcOffset, amount, type, notes, isBasalInsulin, nightscoutSystemId, nightscoutId, pumpType, pumpSerial, temporaryId, pumpId, startId, endId FROM `$TABLE_BOLUSES`"
                )
            }
            db.execSQL("DROP TABLE `$TABLE_BOLUSES`")
            db.execSQL("ALTER TABLE new_boluses RENAME TO `$TABLE_BOLUSES`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_pumpId` ON `$TABLE_BOLUSES` (`pumpId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_referenceId` ON `$TABLE_BOLUSES` (`referenceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_timestamp` ON `$TABLE_BOLUSES` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_boluses_nightscoutId` ON `$TABLE_BOLUSES` (`nightscoutId`)")

            // --- effectiveProfileSwitches / profileSwitches: add insulin columns if missing ---
            if (!hasColumn(db, TABLE_EFFECTIVE_PROFILE_SWITCHES, "insulinLabel")) {
                db.execSQL("ALTER TABLE `$TABLE_EFFECTIVE_PROFILE_SWITCHES` ADD COLUMN `insulinLabel` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `$TABLE_EFFECTIVE_PROFILE_SWITCHES` ADD COLUMN `insulinEndTime` INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE `$TABLE_EFFECTIVE_PROFILE_SWITCHES` ADD COLUMN `insulinPeakTime` INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE `$TABLE_EFFECTIVE_PROFILE_SWITCHES` ADD COLUMN `concentration` REAL NOT NULL DEFAULT 1.0")
            }
            if (!hasColumn(db, TABLE_PROFILE_SWITCHES, "insulinLabel")) {
                db.execSQL("ALTER TABLE `$TABLE_PROFILE_SWITCHES` ADD COLUMN `insulinLabel` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `$TABLE_PROFILE_SWITCHES` ADD COLUMN `insulinEndTime` INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE `$TABLE_PROFILE_SWITCHES` ADD COLUMN `insulinPeakTime` INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE `$TABLE_PROFILE_SWITCHES` ADD COLUMN `concentration` REAL NOT NULL DEFAULT 1.0")
            }

            // --- originalPsId: add to effectiveProfileSwitches if missing ---
            if (!hasColumn(db, TABLE_EFFECTIVE_PROFILE_SWITCHES, "originalPsId")) {
                db.execSQL("ALTER TABLE `$TABLE_EFFECTIVE_PROFILE_SWITCHES` ADD COLUMN `originalPsId` INTEGER DEFAULT NULL")
            }

            // --- drop stale indexes that MTR v34 may still have ---
            db.execSQL("DROP INDEX IF EXISTS `index_effectiveProfileSwitches_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_boluses_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_bolusCalculatorResults_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_bolusCalculatorResults_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_carbs_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_carbs_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_pumpSerial`")
            db.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_pumpType`")
            db.execSQL("DROP INDEX IF EXISTS `index_glucoseValues_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_glucoseValues_sourceSensor`")
            db.execSQL("DROP INDEX IF EXISTS `index_profileSwitches_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_pumpType`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_pumpSerial`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryBasals_pumpId` ON `$TABLE_TEMPORARY_BASALS` (`pumpId`)")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryTargets_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryTargets_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_therapyEvents_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_therapyEvents_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_pumpType`")
            db.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_pumpSerial`")
            db.execSQL("DROP INDEX IF EXISTS `index_foods_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_foods_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_deviceStatus_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_runningModes_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_heartRate_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_stepsCount_id`")

            dropCustomIndexes(db)
        }
    }

    // Downgrade path for devices that were at v35.
    // Devices that came from MTR v34 directly (skipping migration33to34) still have stale indexes
    // that migration33to34 would have dropped. Use IF EXISTS so it's safe for devices that already
    // ran migration33to34.
    internal val migration35to34 = object : Migration(35, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_effectiveProfileSwitches_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_boluses_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_bolusCalculatorResults_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_bolusCalculatorResults_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_carbs_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_carbs_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_pumpSerial`")
            db.execSQL("DROP INDEX IF EXISTS `index_extendedBoluses_pumpType`")
            db.execSQL("DROP INDEX IF EXISTS `index_glucoseValues_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_glucoseValues_sourceSensor`")
            db.execSQL("DROP INDEX IF EXISTS `index_profileSwitches_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_pumpType`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryBasals_pumpSerial`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_temporaryBasals_pumpId` ON `$TABLE_TEMPORARY_BASALS` (`pumpId`)")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryTargets_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_temporaryTargets_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_therapyEvents_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_therapyEvents_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_pumpType`")
            db.execSQL("DROP INDEX IF EXISTS `index_totalDailyDoses_pumpSerial`")
            db.execSQL("DROP INDEX IF EXISTS `index_foods_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_foods_isValid`")
            db.execSQL("DROP INDEX IF EXISTS `index_deviceStatus_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_runningModes_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_heartRate_id`")
            db.execSQL("DROP INDEX IF EXISTS `index_stepsCount_id`")
            // originalPsId may be missing on devices that skipped migration33to34
            val cursor = db.query("PRAGMA table_info(`$TABLE_EFFECTIVE_PROFILE_SWITCHES`)")
            var hasOriginalPsId = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "originalPsId") {
                    hasOriginalPsId = true
                    break
                }
            }
            cursor.close()
            if (!hasOriginalPsId) {
                db.execSQL("ALTER TABLE `$TABLE_EFFECTIVE_PROFILE_SWITCHES` ADD COLUMN `originalPsId` INTEGER DEFAULT NULL")
            }
            // autoIsfValues may be missing on devices that skipped both migration31to32 and migration33to34
            val tableExists = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='$TABLE_AUTOISF_VALUES'"
            ).use { it.count > 0 }
            if (!tableExists) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `${TABLE_AUTOISF_VALUES}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `acceIsf` REAL NOT NULL, `bgIsf` REAL NOT NULL, `ppIsf` REAL NOT NULL, `driftIsf` REAL NOT NULL, `duraIsf` REAL NOT NULL, `finalIsf` REAL NOT NULL, `iobThEffective` REAL NOT NULL, `utcOffset` INTEGER NOT NULL, `version` INTEGER NOT NULL, `dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, `pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_autoIsfValues_id` ON `${TABLE_AUTOISF_VALUES}` (`id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_autoIsfValues_timestamp` ON `${TABLE_AUTOISF_VALUES}` (`timestamp`)")
            }
            dropCustomIndexes(db)
        }
    }

    /** List of all migrations for easy reply in tests. */
    @VisibleForTesting
    internal val migrations = arrayOf(migration20to21, migration21to22, migration22to23, migration23to24, migration24to25, migration25to26, migration26to27, migration27to28, migration28to29, migration29to30, migration30to31, migration31to32, migration32to33, migration33to34, migration34to35, migration35to34)
}
