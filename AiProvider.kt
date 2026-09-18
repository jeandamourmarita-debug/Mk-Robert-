package com.mkrobot.assistant.ai

/**
 * Anything that can turn a user's question into an AI-generated answer.
 * Kept as a single-method interface so the concrete backend (Anthropic,
 * another provider, or a future on-device model) can be swapped without
 * touching MainActivity.
 */
interface AiProvider {
    /**
     * @throws AiProviderException if the key is missing, the network is
     *         unavailable, or the backend returns an error.
     */
    suspend fun answer(question: String): String
}

class AiProviderException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
