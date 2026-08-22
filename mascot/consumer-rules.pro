# Material Mascot — consumer ProGuard/R8 rules.
#
# The library is pure Jetpack Compose + Canvas: nothing is reached via
# reflection or JNI, so R8 may shrink, optimize and obfuscate everything a
# consuming app does not reference. The rule below only pins the library's
# public API surface so it survives aggressive consumer configs: crash
# reports keep readable names and Java interop callers never see stripped
# members.
#
# Deliberately NO -keep rules for androidx.compose.**, androidx.lifecycle.**
# or kotlinx.coroutines.**: those artifacts ship their own consumer rules,
# and duplicating them here would disable shrinking of the whole Compose
# runtime in every app depending on this library.

-keep public class com.maxmaster.materialmascot.** {
    public <init>(...);
    public *;
}
