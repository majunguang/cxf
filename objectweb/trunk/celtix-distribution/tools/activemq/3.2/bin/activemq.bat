@echo off

REM   ActiveMQ shell script
REM
REM   $Id: activemq.bat,v 1.1.1.1 2005/03/11 21:14:04 jstrachan Exp $
REM
REM   This script is heavily based on the Ant script
REM
REM   Copyright (c) 2001-2003 The Apache Software Foundation.  All rights
REM   reserved.

if exist "%HOME%\activemqrc_pre.bat" call "%HOME%\activemqrc_pre.bat"

if "%OS%"=="Windows_NT" @setlocal

rem %~dp0 is expanded pathname of the current script under NT
set DEFAULT_ACTIVEMQ_HOME=%~dp0..

if "%ACTIVEMQ_HOME%"=="" set ACTIVEMQ_HOME=%DEFAULT_ACTIVEMQ_HOME%
set DEFAULT_ACTIVEMQ_HOME=

rem Slurp the command line arguments. This loop allows for an unlimited number
rem of arguments (up to the command line limit, anyway).
set ACTIVEMQ_CMD_LINE_ARGS=%1
if ""%1""=="""" goto doneStart
shift
:setupArgs
if ""%1""=="""" goto doneStart
set ACTIVEMQ_CMD_LINE_ARGS=%ACTIVEMQ_CMD_LINE_ARGS% %1
shift
goto setupArgs
rem This label provides a place for the argument list loop to break out 
rem and for NT handling to skip to.

:doneStart
rem find ACTIVEMQ_HOME if it does not exist due to either an invalid value passed
rem by the user or the %0 problem on Windows 9x
if exist "%ACTIVEMQ_HOME%\README.txt" goto checkJava

rem check for activemq in Program Files on system drive
if not exist "%SystemDrive%\Program Files\activemq" goto checkSystemDrive
set ACTIVEMQ_HOME=%SystemDrive%\Program Files\activemq
goto checkJava

:checkSystemDrive
rem check for activemq in root directory of system drive
if not exist %SystemDrive%\activemq\README.txt goto checkCDrive
set ACTIVEMQ_HOME=%SystemDrive%\activemq
goto checkJava

:checkCDrive
rem check for activemq in C:\activemq for Win9X users
if not exist C:\activemq\README.txt goto noAntHome
set ACTIVEMQ_HOME=C:\activemq
goto checkJava

:noAntHome
echo ACTIVEMQ_HOME is set incorrectly or activemq could not be located. Please set ACTIVEMQ_HOME.
goto end

:checkJava
set _JAVACMD=%JAVACMD%
set LOCALCLASSPATH=%CLASSPATH%

set JAVA_EXT_DIRS=%JAVA_HOME%\lib\ext;%ACTIVEMQ_HOME%;%ACTIVEMQ_HOME%\lib;%ACTIVEMQ_HOME%\lib\optional

if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
if "%_JAVACMD%" == "" set _JAVACMD=%JAVA_HOME%\bin\java.exe
goto runAnt

:noJavaHome
if "%_JAVACMD%" == "" set _JAVACMD=java.exe
echo.
echo Warning: JAVA_HOME environment variable is not set.
echo.

:runAnt

if "%ACTIVEMQ_OPTS%" == "" set ACTIVEMQ_OPTS=-Xmx512M -Dderby.system.home="..\var" -Dderby.storage.fileSyncTransactionLog=true

REM Uncomment to enable YourKit profiling
REM SET ACTIVEMQ_DEBUG_OPTS="-Xrunyjpagent"

REM Uncomment to enable remote debugging
REM SET ACTIVEMQ_DEBUG_OPTS=-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005

set LOCALCLASSPATH=%ACTIVEMQ_HOME%\conf;%LOCALCLASSPATH%


"%_JAVACMD%" %ACTIVEMQ_DEBUG_OPTS% %ACTIVEMQ_OPTS% -Djava.ext.dirs="%JAVA_EXT_DIRS%" -classpath "%LOCALCLASSPATH%" -Dactivemq.home="%ACTIVEMQ_HOME%" org.activemq.spring.Main %ACTIVEMQ_ARGS% %ACTIVEMQ_CMD_LINE_ARGS%


goto end


:end
set LOCALCLASSPATH=
set _JAVACMD=
set ACTIVEMQ_CMD_LINE_ARGS=

if "%OS%"=="Windows_NT" @endlocal

:mainEnd
if exist "%HOME%\activemqrc_post.bat" call "%HOME%\activemqrc_post.bat"

