# 研迹 · release 混淆与压缩规则
#
# release 构建已开启 R8（isMinifyEnabled = true）。本文件只补充「R8 无法从字节码
# 自行推断」的保留规则；Room / Compose / AndroidX 的规则由各自 AAR 的
# consumer-proguard-rules 自动引入，不需要在这里重复声明。

# ---------- kotlinx.serialization ----------
# @Serializable 类由编译器插件生成 serializer，反射入口需要保留伴生对象与注解。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers @kotlinx.serialization.Serializable class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- 前台计时 Service ----------
# Service 由系统按 Manifest 中的类名反射实例化，类名不能被重命名。
-keep class com.example.yanji.service.FocusTimerService { *; }

# ---------- 枚举 ----------
# 业务枚举通过 name / valueOf 与数据库中的字符串互转（如 SessionStatus、ChatSender），
# 重命名成员会破坏已落库的历史数据。
-keepclassmembers enum com.example.yanji.data.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------- 保留行号，便于线上崩溃定位 ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
