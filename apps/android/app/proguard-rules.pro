# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/android-sdk/tools/proguard/proguard-android.txt

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ical4j 3.x
-keep,includedescriptorclasses class net.fortuna.ical4j.util.MapTimeZoneCache
-dontwarn groovy.**
-dontwarn org.codehaus.groovy.**
-dontwarn org.apache.commons.logging.**
