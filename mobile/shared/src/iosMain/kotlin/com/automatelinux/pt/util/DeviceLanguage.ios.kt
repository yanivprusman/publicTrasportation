package com.automatelinux.pt.util

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual fun platformLanguageTag(): String = NSLocale.currentLocale.languageCode
