# Room, Firestore, CameraX, ML Kit and Compose all ship their own consumer
# rules; nothing in this app uses reflection on its own classes (entities are
# referenced directly by Room-generated code, Firestore payloads are plain
# Maps). Keep this file minimal - add rules only when R8 reports a real need.

# Keep crash stack traces readable in production reports.
-keepattributes SourceFile,LineNumberTable
