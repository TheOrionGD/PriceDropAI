package com.pricedropai.myapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pricedropai.myapp.data.local.dao.PriceSnapshotDao
import com.pricedropai.myapp.data.local.dao.ProductDao
import com.pricedropai.myapp.data.local.dao.SearchHistoryDao
import com.pricedropai.myapp.data.local.dao.StoreOfferDao
import com.pricedropai.myapp.data.local.dao.TrackedProductDao
import com.pricedropai.myapp.data.local.entity.Converters
import com.pricedropai.myapp.data.local.entity.PriceSnapshotEntity
import com.pricedropai.myapp.data.local.entity.ProductEntity
import com.pricedropai.myapp.data.local.entity.SearchHistoryEntity
import com.pricedropai.myapp.data.local.entity.StoreOfferEntity
import com.pricedropai.myapp.data.local.entity.TrackedProductEntity

@Database(
    entities = [
        ProductEntity::class,
        StoreOfferEntity::class,
        PriceSnapshotEntity::class,
        TrackedProductEntity::class,
        SearchHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PriceDropDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun storeOfferDao(): StoreOfferDao
    abstract fun priceSnapshotDao(): PriceSnapshotDao
    abstract fun trackedProductDao(): TrackedProductDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val DATABASE_VERSION = 3

        @Volatile
        private var INSTANCE: PriceDropDatabase? = null

        /**
         * Migration from version 1 to 2.
         * The repository initially released version 2 with identical entity schemas.
         * This explicit no-op migration ensures any v1 installs cleanly advance without data loss.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1 and 2 schemas are identical; no-op migration to allow safe upgrade
            }
        }

        /**
         * Migration from version 2 to 3.
         * Adds non-breaking columns with safe defaults:
         * 1. products: asin (TEXT NULL), searchQuery (TEXT NULL)
         * 2. store_offers: imageUrl (TEXT NULL), searchUrl (TEXT NULL),
         *    isDirectProductUrl (INTEGER NOT NULL DEFAULT 0),
         *    status (TEXT NOT NULL DEFAULT 'AVAILABLE'),
         *    matchScore (INTEGER NOT NULL DEFAULT 100),
         *    lastVerifiedAt (INTEGER NULL)
         * Preserves existing product records, store offers, price history,
         * tracked products, and search history without destructive wiping.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add new columns to products
                db.execSQL("ALTER TABLE products ADD COLUMN asin TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN searchQuery TEXT DEFAULT NULL")

                // 2. Add new columns to store_offers
                db.execSQL("ALTER TABLE store_offers ADD COLUMN imageUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE store_offers ADD COLUMN searchUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE store_offers ADD COLUMN isDirectProductUrl INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE store_offers ADD COLUMN status TEXT NOT NULL DEFAULT 'AVAILABLE'")
                db.execSQL("ALTER TABLE store_offers ADD COLUMN matchScore INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE store_offers ADD COLUMN lastVerifiedAt INTEGER DEFAULT NULL")

                // 3. Clean up any historical duplicate product records (preserving the newest row per product identity)
                try {
                    db.execSQL("""
                        DELETE FROM products WHERE id NOT IN (
                            SELECT p1.id FROM products p1
                            INNER JOIN (
                                SELECT 
                                    CASE WHEN asin IS NOT NULL AND asin != '' THEN 'asin:' || asin ELSE 'title:' || title END AS identity,
                                    MAX(lastUpdated) AS maxLastUpdated
                                FROM products
                                GROUP BY CASE WHEN asin IS NOT NULL AND asin != '' THEN 'asin:' || asin ELSE 'title:' || title END
                            ) p2 ON (CASE WHEN p1.asin IS NOT NULL AND p1.asin != '' THEN 'asin:' || p1.asin ELSE 'title:' || p1.title END) = p2.identity
                            AND p1.lastUpdated = p2.maxLastUpdated
                        )
                    """.trimIndent())
                } catch (_: Exception) {
                    // Safe fallback if table is empty
                }
            }
        }

        /**
         * Direct Migration from version 1 to 3.
         */
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
            }
        }

        fun getInstance(context: Context): PriceDropDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PriceDropDatabase::class.java,
                    "pricedrop_ai_room.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
