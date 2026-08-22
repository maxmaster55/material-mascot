# Material Mascot consumer ProGuard/R8 rules
# The public API is referenced directly by Compose call sites; keep all public classes.

-keep public class com.maxmaster.materialmascot.** { *; }
-keep public enum com.maxmaster.materialmascot.** { *; }
-keep public interface com.maxmaster.materialmascot.** { *; }
-keep public @interface com.maxmaster.materialmascot.** { *; }

# Keep Kotlin metadata for public APIs
-keep class kotlin.Metadata { *; }

# Compose compiler needs these
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# Lifecycle (used by LiveBot internally)
-keep class androidx.lifecycle.** { *; }

# Coroutines (used by MascotStatusBus internally)
-keep class kotlinx.coroutines.** { *; }
