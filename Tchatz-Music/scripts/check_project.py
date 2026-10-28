#!/usr/bin/env python3
"""Contrôles structurels sans SDK. Ne remplace ni Gradle, ni un test appareil."""
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile
p=Path(__file__).resolve().parents[1]
required=['gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties','settings.gradle.kts','app/build.gradle.kts','.github/workflows/android.yml','app/src/main/AndroidManifest.xml','app/schemas/com.tchatz.music.data.MusicDatabase/1.json']
for n in required: assert (p/n).is_file(),n
assert zipfile.is_zipfile(p/'gradle/wrapper/gradle-wrapper.jar')
for f in (p/'app/src/main').rglob('*.xml'): ET.parse(f)
m=(p/'app/src/main/AndroidManifest.xml').read_text()
assert 'READ_MEDIA_AUDIO' in m and 'maxSdkVersion="32"' in m
assert 'INTERNET' not in m and 'MANAGE_EXTERNAL_STORAGE' not in m
assert 'mediaPlayback' in m and '.playback.MusicService' in m
s=(p/'settings.gradle.kts').read_text();assert 'include(":app")' in s
ci=(p/'.github/workflows/android.yml').read_text()
for v in ['workflow_dispatch', 'pull_request', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', 'app/build/outputs/apk/debug/app-debug.apk','connectedDebugAndroidTest']: assert v in ci,v
assert (p/'gradlew').stat().st_mode & 0o111
print('OK — structure, XML, module, permissions, wrapper, chemins CI et schéma Room')
