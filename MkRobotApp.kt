package com.mkrobot.assistant

import android.app.Application

/**
 * Minimal Application class. Deliberately does no eager initialization of
 * heavy singletons (speech engines, HTTP clients, etc.) so cold start stays
 * fast on low-RAM Android 12+ devices — those are created lazily inside
 * MainActivity only when the user actually taps the mic.
 */
class MkRobotApp : Application()
