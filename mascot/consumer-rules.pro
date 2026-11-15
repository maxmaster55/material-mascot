# Material Mascot — consumer ProGuard/R8 rules.
#
# Nothing is reached via reflection, so R8 may shrink, optimize and obfuscate
# everything a consuming app does not reference. The first rule only pins the
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

# The Rust core finds these methods by their exact JNI names
# (Java_com_maxmaster_materialmascot_engine_NativeMascot_*), so neither the
# class nor its native methods may be renamed or removed.
-keep class com.maxmaster.materialmascot.engine.NativeMascot {
    native <methods>;
}
