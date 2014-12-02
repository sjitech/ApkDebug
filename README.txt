Add android:debuggable="true" to <application> of AndroidManifest.xml of APK

bin/ApkDebug.sh <inputApkFile> <outputApkFile> <debugKeyStoreFile>

or 

java -jar bin/ApkDebug.jar <inputApkFile> <outputApkFile> <debugKeyStoreFile>

Note: debugKeyStoreFile can be \"\" means do not sign result apk file