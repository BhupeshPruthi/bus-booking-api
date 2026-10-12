# Project-specific R8 rules for release builds.
# Libraries (Retrofit, OkHttp, Moshi codegen, Hilt, Coil, AndroidX) ship their own consumer rules.

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
# Keeps line numbers in crash reports; the mapping file restores names.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# --- JSON (Moshi) ---------------------------------------------------------
# All API/stored models use @JsonClass codegen, but KotlinJsonAdapterFactory is also
# registered as a fallback, which needs real class/field names and Kotlin metadata.
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class com.mybus.app.data.remote.dto.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class * { @com.squareup.moshi.Json <fields>; }

# --- Retrofit API interface ----------------------------------------------
# Keep the service interface and its generic return types (suspend + Response<ApiResponse<T>>).
-keep,allowobfuscation interface com.mybus.app.data.remote.ApiService
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# --- Google Sign-In via Credential Manager -------------------------------
# Required by androidx.credentials (see developer.android.com/identity/sign-in/credential-manager).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
