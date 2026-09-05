@echo off
REM ====================================================================
REM  Starts the SmileCare server on http://localhost:8080
REM  MySQL must be running and database.sql must have been imported.
REM ====================================================================

cd /d "%~dp0"

if not exist smilecare.jar (
    echo smilecare.jar not found - running compile.bat first.
    call compile.bat || exit /b 1
)

echo.
REM The clinic PC has a small Windows paging file, and the JVM's default
REM garbage collector reserves more than it can commit, so it refuses to
REM start at all. This server is tiny; a 256 MB heap is ample.
java -Xmx256m -XX:+UseSerialGC -cp "smilecare.jar;lib/*" com.smilecare.Main %*
