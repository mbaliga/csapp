package com.mbaliga.csapp.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    /** Statements are exposed so a JVM test can replay them against the exported v1/v2 schema JSON. */
    val MIGRATION_1_2_SQL: List<String> = listOf(
        "ALTER TABLE `signals` ADD COLUMN `projectId` TEXT NOT NULL DEFAULT 'default'",
        "ALTER TABLE `signals` ADD COLUMN `sentReplyText` TEXT",
        "CREATE INDEX IF NOT EXISTS `index_signals_projectId` ON `signals` (`projectId`)",
        "ALTER TABLE `incidents` ADD COLUMN `projectId` TEXT NOT NULL DEFAULT 'default'",
        "CREATE INDEX IF NOT EXISTS `index_incidents_projectId` ON `incidents` (`projectId`)",
        "CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `externalId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `project_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` TEXT NOT NULL, `kind` TEXT NOT NULL, `value` TEXT NOT NULL, `enabled` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_project_sources_projectId` ON `project_sources` (`projectId`)",
        "CREATE TABLE IF NOT EXISTS `mailboxes` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `kind` TEXT NOT NULL, `host` TEXT NOT NULL, `port` INTEGER NOT NULL, `username` TEXT NOT NULL, `folder` TEXT NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `mail_checkpoints` (`key` TEXT NOT NULL, `uidValidity` INTEGER NOT NULL, `lastUid` INTEGER NOT NULL, `lastPollAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
        "CREATE TABLE IF NOT EXISTS `reply_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `signalSourceKey` TEXT NOT NULL, `textSent` TEXT NOT NULL, `sentAt` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `httpStatus` INTEGER)",
        "CREATE INDEX IF NOT EXISTS `index_reply_log_signalSourceKey` ON `reply_log` (`signalSourceKey`)",
        "CREATE TABLE IF NOT EXISTS `quota_buckets` (`bucketKey` TEXT NOT NULL, `windowStart` INTEGER NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`bucketKey`))",
        "CREATE TABLE IF NOT EXISTS `suggestions` (`id` TEXT NOT NULL, `targetType` TEXT NOT NULL, `targetId` TEXT NOT NULL, `kind` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `provenance` TEXT NOT NULL, `model` TEXT NOT NULL, `promptVersion` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_suggestions_targetId` ON `suggestions` (`targetId`)",
        "CREATE INDEX IF NOT EXISTS `index_suggestions_state` ON `suggestions` (`state`)",
    )

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
        }
    }
}
