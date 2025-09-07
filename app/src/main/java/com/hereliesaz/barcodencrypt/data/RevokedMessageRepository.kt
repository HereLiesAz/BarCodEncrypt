package com.hereliesaz.barcodencrypt.data

// Assuming RevokedMessageDao and RevokeMessage are resolved from the same package.
// If type resolution issues arise with this file, we might need explicit imports or FQNs here too.

/**
 * A repository for managing the blacklist. It's not complicated.
 * @param revokedMessageDao The DAO for the blacklist table.
 */
class RevokedMessageRepository(private val revokedMessageDao: RevokedMessageDao) {

    /**
     * Blacklists a message hash.
     * @param messageHash The SHA-256 hash of the message to blacklist.
     */
    suspend fun revokeMessage(messageHash: String) {
        revokedMessageDao.insert(RevokedMessage(messageHash))
    }

    /**
     * Checks if a message is on the blacklist.
     * @param messageHash The hash of the message to check.
     * @return True if the message has been revoked, false otherwise.
     */
    suspend fun isMessageRevoked(messageHash: String): Boolean {
        return revokedMessageDao.getRevokedMessage(messageHash) != null
    }
}
