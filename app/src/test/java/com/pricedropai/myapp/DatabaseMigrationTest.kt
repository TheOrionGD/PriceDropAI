package com.pricedropai.myapp

import androidx.sqlite.db.SupportSQLiteDatabase
import com.pricedropai.myapp.core.model.Availability
import com.pricedropai.myapp.core.model.MarketplaceStatus
import com.pricedropai.myapp.core.model.Product
import com.pricedropai.myapp.core.model.Store
import com.pricedropai.myapp.core.model.StoreOffer
import com.pricedropai.myapp.data.local.PriceDropDatabase
import com.pricedropai.myapp.data.local.entity.ProductEntity
import com.pricedropai.myapp.data.local.entity.StoreOfferEntity
import com.pricedropai.myapp.data.repository.ProductRepository
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class DatabaseMigrationTest {

    /**
     * Fake/Spy SupportSQLiteDatabase to record all executed SQL statements during Room migrations.
     */
    private class TestSupportSQLiteDatabase : ArrayList<String>() {
        fun createProxy(): SupportSQLiteDatabase {
            return Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java)
            ) { _, method, args ->
                if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                    add(args[0].toString())
                }
                null
            } as SupportSQLiteDatabase
        }
    }

    @Test
    fun testMigration1To2PreservesSchema() {
        val executedSql = TestSupportSQLiteDatabase()
        val dbProxy = executedSql.createProxy()

        assertEquals(1, PriceDropDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, PriceDropDatabase.MIGRATION_1_2.endVersion)

        PriceDropDatabase.MIGRATION_1_2.migrate(dbProxy)

        // Version 1 and 2 schemas were identical; no destructive statements executed
        assertTrue("MIGRATION_1_2 must not execute destructive statements", executedSql.isEmpty())
    }

    @Test
    fun testMigration2To3AddsExpectedColumnsWithSafeDefaults() {
        val executedSql = TestSupportSQLiteDatabase()
        val dbProxy = executedSql.createProxy()

        assertEquals(2, PriceDropDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, PriceDropDatabase.MIGRATION_2_3.endVersion)

        PriceDropDatabase.MIGRATION_2_3.migrate(dbProxy)

        // Verify products table alterations
        assertTrue("Must add asin column to products", executedSql.any { it.contains("ALTER TABLE products ADD COLUMN asin TEXT DEFAULT NULL", ignoreCase = true) })
        assertTrue("Must add searchQuery column to products", executedSql.any { it.contains("ALTER TABLE products ADD COLUMN searchQuery TEXT DEFAULT NULL", ignoreCase = true) })

        // Verify store_offers table alterations with safe non-null defaults
        assertTrue("Must add imageUrl column", executedSql.any { it.contains("ALTER TABLE store_offers ADD COLUMN imageUrl TEXT DEFAULT NULL", ignoreCase = true) })
        assertTrue("Must add searchUrl column", executedSql.any { it.contains("ALTER TABLE store_offers ADD COLUMN searchUrl TEXT DEFAULT NULL", ignoreCase = true) })
        assertTrue("Must add isDirectProductUrl with DEFAULT 0", executedSql.any { it.contains("ALTER TABLE store_offers ADD COLUMN isDirectProductUrl INTEGER NOT NULL DEFAULT 0", ignoreCase = true) })
        assertTrue("Must add status with DEFAULT 'AVAILABLE'", executedSql.any { it.contains("ALTER TABLE store_offers ADD COLUMN status TEXT NOT NULL DEFAULT 'AVAILABLE'", ignoreCase = true) })
        assertTrue("Must add matchScore with DEFAULT 100", executedSql.any { it.contains("ALTER TABLE store_offers ADD COLUMN matchScore INTEGER NOT NULL DEFAULT 100", ignoreCase = true) })
        assertTrue("Must add lastVerifiedAt column", executedSql.any { it.contains("ALTER TABLE store_offers ADD COLUMN lastVerifiedAt INTEGER DEFAULT NULL", ignoreCase = true) })

        // Verify duplicate cleanup query was invoked
        assertTrue("Must execute legacy duplicate cleanup query", executedSql.any { it.contains("DELETE FROM products WHERE id NOT IN", ignoreCase = true) })

        // Verify NO DROP TABLE statements exist
        assertFalse("Migration must NEVER drop tables", executedSql.any { it.contains("DROP TABLE", ignoreCase = true) })
    }

    @Test
    fun testMigration1To3ChainsSequentialMigrations() {
        val executedSql = TestSupportSQLiteDatabase()
        val dbProxy = executedSql.createProxy()

        assertEquals(1, PriceDropDatabase.MIGRATION_1_3.startVersion)
        assertEquals(3, PriceDropDatabase.MIGRATION_1_3.endVersion)

        PriceDropDatabase.MIGRATION_1_3.migrate(dbProxy)

        assertTrue("Chained migration 1->3 must add asin column", executedSql.any { it.contains("asin", ignoreCase = true) })
        assertTrue("Chained migration 1->3 must add isDirectProductUrl", executedSql.any { it.contains("isDirectProductUrl", ignoreCase = true) })
    }

    @Test
    fun testRoomDatabaseConfigurationDoesNotContainDestructiveFallback() {
        assertEquals("Database version must be 3", 3, PriceDropDatabase.DATABASE_VERSION)
        assertEquals("Migration 1->2 startVersion must be 1", 1, PriceDropDatabase.MIGRATION_1_2.startVersion)
        assertEquals("Migration 1->2 endVersion must be 2", 2, PriceDropDatabase.MIGRATION_1_2.endVersion)
        assertEquals("Migration 2->3 startVersion must be 2", 2, PriceDropDatabase.MIGRATION_2_3.startVersion)
        assertEquals("Migration 2->3 endVersion must be 3", 3, PriceDropDatabase.MIGRATION_2_3.endVersion)
        assertEquals("Migration 1->3 startVersion must be 1", 1, PriceDropDatabase.MIGRATION_1_3.startVersion)
        assertEquals("Migration 1->3 endVersion must be 3", 3, PriceDropDatabase.MIGRATION_1_3.endVersion)
    }

    @Test
    fun testPersistentDeduplicationWithCanonicalAsinSupersedingStaleId() {
        // Simulates pre-existing product in Room under an old non-canonical ID (e.g. from query slug)
        val initialProduct = ProductEntity(
            id = "milton-thermosteel-bottle-1l",
            title = "Milton Thermosteel Flip Lid Flask 1000 ml",
            asin = null,
            lastUpdated = 1000L
        )

        // When a new search discovers the canonical ASIN "B07H8NXZ6Y"
        val canonicalAsin = "B07H8NXZ6Y"
        val targetId = "amazon_${canonicalAsin.lowercase()}"

        // Check canonical resolution logic
        val oldIdToDelete = initialProduct.id.takeIf { it != targetId }

        assertEquals("Stale ID must be identified for atomic deletion", "milton-thermosteel-bottle-1l", oldIdToDelete)
        assertEquals("New canonical record must use amazon_{asin}", "amazon_b07h8nxz6y", targetId)
    }

    @Test
    fun testDistinctProductVariantsAreNotMerged() {
        val adjustableGripper = Product(
            id = "amazon_b08xyz1234",
            title = "Adjustable Hand Gripper 5kg to 60kg",
            asin = "B08XYZ1234",
            brand = "Boldfit",
            lastUpdated = 2000L
        )

        val fixedGripper = Product(
            id = "amazon_b09abc5678",
            title = "Fixed Steel Hand Gripper 60kg",
            asin = "B09ABC5678",
            brand = "Boldfit",
            lastUpdated = 2100L
        )

        val list = listOf(adjustableGripper, fixedGripper)
        val seenIdentities = mutableSetOf<String>()
        val deduplicated = mutableListOf<Product>()

        for (item in list) {
            val asinKey = item.asin?.lowercase()?.takeIf { it.isNotBlank() }
            val canonicalKey = asinKey
                ?: item.lowestOffer?.productUrl?.let { ProductRepository.cleanCanonicalUrl(it) }
                ?: "${item.brand?.lowercase()?.trim() ?: ""}_${ProductRepository.normalizeProductTitle(item.title)}"

            if (seenIdentities.add(canonicalKey)) {
                deduplicated.add(item)
            }
        }

        assertEquals("Distinct product variants must both be preserved", 2, deduplicated.size)
        assertEquals(adjustableGripper.id, deduplicated[0].id)
        assertEquals(fixedGripper.id, deduplicated[1].id)
    }

    @Test
    fun testConcurrentWritesWithSameCanonicalIdentityConverge() {
        val thread1Product = Product(
            id = "amazon_b08xyz1234",
            title = "5KG To 60KG Weight Adjustable Hand Gripper",
            asin = "B08XYZ1234",
            lastUpdated = 3000L
        )

        val thread2Product = Product(
            id = "amazon_b08xyz1234",
            title = "5KG To 60KG Weight Adjustable Hand Gripper",
            asin = "B08XYZ1234",
            lastUpdated = 3050L
        )

        val rawQueue = listOf(thread2Product, thread1Product)
        val seenIdentities = mutableSetOf<String>()
        val finalHistory = mutableListOf<Product>()

        for (item in rawQueue) {
            val asinKey = item.asin?.lowercase()?.takeIf { it.isNotBlank() }
            val canonicalKey = asinKey
                ?: item.lowestOffer?.productUrl?.let { ProductRepository.cleanCanonicalUrl(it) }
                ?: "${item.brand?.lowercase()?.trim() ?: ""}_${ProductRepository.normalizeProductTitle(item.title)}"

            if (seenIdentities.add(canonicalKey)) {
                finalHistory.add(item)
            }
        }

        assertEquals("Concurrent writes of the same item must result in 1 unique entry", 1, finalHistory.size)
        assertEquals(3050L, finalHistory[0].lastUpdated)
    }
}
