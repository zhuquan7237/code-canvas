# Proguard rules for CodeCanvas
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# --- enum names are persisted data ------------------------------------------------
# DocumentKind.name() is written into the documents JSON ("kind":"MARKDOWN") and read back with
# DocumentKind.fromName(). R8 renames enum constants to a, b, c... which makes fromName() return
# null for every stored document, so every file falls back to UNKNOWN and opens as plain text —
# "所有格式都打不开了". The names are a storage format here, not identifiers to optimise.
-keepclassmembers enum com.nous.codecanvas.data.DocumentKind {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}
-keepnames enum com.nous.codecanvas.data.DocumentKind { *; }

# The same applies to any enum whose name is persisted.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepnames enum com.nous.codecanvas.util.RenderKind { *; }
