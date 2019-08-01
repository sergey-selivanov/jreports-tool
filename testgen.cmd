call gradle clean installdist

del build\import.zip

build\install\jreports-tool\bin\jreports-tool ^
-prepareimport ^
-sourcedir d:/git/nnn-reports-nnngit ^
-sourcedir d:/git/nnn-reports-annual ^
-targetzip build/import.zip ^
-targetresource /reports/permits3 ^
-dsname test_ds ^
-dsdriver org.mariadb.jdbc.Driver ^
-dsurl jdbc:mysql://localhost:3306/permits ^
-dslogin permits ^
-dspassword permits ^
-exclude src/main/resources/sampleExcludes.txt ^
-verbose info

: -tempdir build/testimport ^

: -targetzip build/import.zip ^
