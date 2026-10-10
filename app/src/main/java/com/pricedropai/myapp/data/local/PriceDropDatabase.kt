package com.pricedropai.myapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 2,
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
        @Volatile
        private var INSTANCE: PriceDropDatabase? = null

        fun getInstance(context: Context): PriceDropDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PriceDropDatabase::class.java,
                    "pricedrop_ai_room.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
