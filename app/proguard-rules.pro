# 不用反射与序列化框架，R8 默认规则即可。
# 发布版去掉日志调用
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
