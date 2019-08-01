goto nozip

del build\import.zip

cd build\testimport
c:\cygwin\bin\zip  -r ..\import.zip *
cd ..\..

:nozip
:goto end

build\install\jreports-tool\bin\jreports-tool ^
-jslogin jasperadmin ^
-jspassword jasperadmin ^
-jsrestapiurl http://mistral:9080/jasperserver/rest_v2 ^
-delete /reports/permits3 ^
-import build/import.zip ^
-verbose info

:end
