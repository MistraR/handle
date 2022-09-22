@echo off

SET PRG=%~dp0%

SET CP=.

REM Get the full name of the directory where the Handle code is installed
SET HDLHOME=%PRG%..

REM add all the jar files in the lib directory to the classpath
FOR /R "%HDLHOME%\lib\" %%i IN ("*.*") DO CALL "%HDLHOME%\bin\cpappend.bat" %%i

set CMD=%1%
shift

set ARGS=
:args-loop
if "%~1"=="" goto :switch-case
set ARGS=%ARGS% %1
shift
goto :args-loop

:switch-case
  :: Call and mask out invalid call targets
  goto :switch-case-%CMD% 2>nul || (
    :: Default case
    echo Unknown Handle.Net server command %CMD%
  )
  goto :switch-case-end

  :switch-case-java
    java -cp "%CP%" %ARGS%
    goto :switch-case-end
  :switch-case-admintool
    java -cp "%CP%" net.handle.apps.admintool.controller.Main %ARGS%
    goto :switch-case-end
  :switch-case-oldadmintool
    java -cp "%CP%" net.handle.apps.gui.hadmin.HandleTool %ARGS%
    goto :switch-case-end
  :switch-case-keyutil
    java -cp "%CP%" net.handle.apps.tools.KeyUtil %ARGS%
    goto :switch-case-end
  :switch-case-keygen
    java -cp "%CP%" net.handle.apps.tools.KeyGenerator %ARGS%
    goto :switch-case-end
  :switch-case-qresolverGUI
    java -cp "%CP%" net.handle.apps.gui.resolver.Main %ARGS%
    goto :switch-case-end
  :switch-case-qresolver
    java -cp "%CP%" net.handle.apps.simple.HDLTrace %ARGS%
    goto :switch-case-end
  :switch-case-getrootinfo
    java -cp "%CP%" net.handle.apps.tools.GetRootInfo %ARGS%
    goto :switch-case-end
  :switch-case-getsiteinfo
    java -cp "%CP%" net.handle.apps.tools.GetSiteInfo %ARGS%
    goto :switch-case-end
  :switch-case-genericbatch
    java -cp "%CP%" net.handle.apps.batch.GenericBatch %ARGS%
    goto :switch-case-end
  :switch-case-create
    java -cp "%CP%" net.handle.apps.simple.HDLCreate %ARGS%
    goto :switch-case-end
  :switch-case-delete
    java -cp "%CP%" net.handle.apps.simple.HDLDelete %ARGS%
    goto :switch-case-end
  :switch-case-list
    java -cp "%CP%" net.handle.apps.simple.HDLList %ARGS%
    goto :switch-case-end
  :switch-case-trace
    java -cp "%CP%" net.handle.apps.simple.HDLTrace %ARGS%
    goto :switch-case-end
  :switch-case-home-na
    java -cp "%CP%" net.handle.apps.simple.HomeNA %ARGS%
    goto :switch-case-end
  :switch-case-convert-siteinfo
    java -cp "%CP%" net.handle.apps.simple.SiteInfoConverter %ARGS%
    goto :switch-case-end
  :switch-case-convert-values
    java -cp "%CP%" net.handle.apps.simple.HandleValuesConverter %ARGS%
    goto :switch-case-end
  :switch-case-convert-localinfo
    java -cp "%CP%" net.handle.apps.simple.LocalInfoConverter %ARGS%
    goto :switch-case-end
  :switch-case-convert-key
    java -cp "%CP%" net.handle.apps.simple.KeyConverter %ARGS%
    goto :switch-case-end

:switch-case-end
