# Giữ lại toàn bộ siêu dữ liệu của Kotlin để tránh lỗi VerifyError
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Metadata

# Giữ lại các lớp mô hình dữ liệu (Data classes) và các hàm thành phần
-keepclassmembers class * {
    @kotlin.jvm.JvmField <fields>;
}

# Bảo vệ các lớp và phương thức được gọi thông qua Reflection từ mã nguồn Kotlin/Java
-keep class com.kinh.gmsprotect.** {
    *;
}

# Giữ lại các API ẩn của hệ thống HyperOS/MIUI không bị xóa nhầm bởi R8
-keepclassmembers class android.app.AppOpsManager {
    public int checkOpNoThrow(int, int, java.lang.String);
}

# Giữ lại tên lớp gốc cho các BroadcastReceiver, Service, Activity chính
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
