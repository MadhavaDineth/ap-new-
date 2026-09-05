@echo off
setlocal enabledelayedexpansion
REM ====================================================================
REM  Compiles the SmileCare backend with plain javac and packs a jar.
REM  Needs a JDK 17 or newer on the PATH.  Run it from any folder.
REM ====================================================================

cd /d "%~dp0"

echo Compiling...
if exist build rmdir /s /q build
mkdir build

REM Paths are written with forward slashes and quoted: this project's own
REM folder has a space in it ("apn madhava"), which needs the quotes, and
REM javac's @argfile parser treats a backslash inside quotes as an escape
REM character, which silently eats plain Windows backslashes.
(for /r src %%f in (*.java) do (
    set "p=%%f"
    set "p=!p:\=/!"
    echo "!p!"
)) > build\sources.txt
javac --release 17 -encoding UTF-8 -cp "lib/*" -d build @build\sources.txt
if errorlevel 1 (
    echo.
    echo COMPILE FAILED - fix the errors above.
    exit /b 1
)

echo Packing smilecare.jar...
REM Some JDK installs only put java/javac on PATH, not the separate jar.exe
REM (e.g. the Oracle "javapath" shim folder). jar is itself bundled as a JDK
REM module, so it can always be reached through java this way.
java -Xmx64m -XX:+UseSerialGC -m jdk.jartool/sun.tools.jar.Main --create --file smilecare.jar --main-class com.smilecare.Main -C build .
if errorlevel 1 (
    echo JAR FAILED
    exit /b 1
)

del build\sources.txt
echo.
echo Done.  Start the server with run.bat
