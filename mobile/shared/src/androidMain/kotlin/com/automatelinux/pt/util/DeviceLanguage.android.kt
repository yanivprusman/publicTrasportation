package com.automatelinux.pt.util

import java.util.Locale

actual fun platformLanguageTag(): String = Locale.getDefault().language
