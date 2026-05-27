name: Build Android

on:
  push:
    branches:
      - main
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'corretto'
          java-version: '21'
      
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
        
      # 插入这一步：检查项目依赖，这能帮你发现是哪个模块或库导致了构建中断
      - name: Check Android dependencies
        run: ./gradlew :android:dependencies
        
      - name: Build with Gradle
        run: ./gradlew assembleDebug
        
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: Bifrost-Debug-APK
          path: app/build/outputs/apk/debug/*.apk
