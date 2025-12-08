# MBassador (SMBJ Event Bus) - Critical for SMBJ
-keep class net.engio.mbassy.** { *; }
-dontwarn javax.el.**

# SMBJ / SSHJ optional dependencies (GSS / Kerberos)
-dontwarn org.ietf.jgss.**
-dontwarn com.hierynomus.smbj.**
-dontwarn net.schmizz.sshj.**

# Bouncy Castle / EdDSA
-dontwarn sun.security.x509.**
-dontwarn net.i2p.crypto.**

# General safety for data classes / serialization
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep our app code to avoid over-aggressive stripping of entry points (though R8 is usually smart)
-keep class com.example.megumidownload.** { *; }
-keep class com.example.megumidownload.RssItem { *; }
-keep class com.example.megumidownload.RssParser { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Security Providers (Conscrypt, Bouncy Castle, SMBJ)
# Critical for SSL/TLS and SMB Encryption
-keep class org.conscrypt.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class com.hierynomus.smbj.** { *; }
-keep class com.hierynomus.mssmb2.** { *; }
-keep class com.hierynomus.protocol.** { *; }
-keep class com.hierynomus.security.** { *; }

# Java Security & Network
-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class javax.net.ssl.** { *; }

# SMBJ uses reflection for PacketSerializer
-keepclassmembers class com.hierynomus.mssmb2.messages.** {
    public <init>(...);
}

# Coroutines
-keep class kotlinx.coroutines.** { *; }
