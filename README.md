# jreports-tool

Command-line tool for JasperReports Server

http://community.jaspersoft.com/project/jasperreports-server/releases

Usage: `jreports-tool [-jsrestapiurl <url>] [-jslogin <login>] [-jspassword <password>]`
` [-delete <resource>] [-import <file.zip>] [-prepareimport] [-sourcedir <dirName>] [-tempdir <dirName>]`
` [-targetzip <zipFileName>] [-targetresource <resource>] [-dsname <name>] [-dsdriver <className>]`
` [-dsurl <connectionUrl>] [-dslogin <login>] [-dspassword <password>] [-exclude <patternsFile>]`
` [-verbose <level>]`

Options

 -jsrestapiurl <url>          Base URL of JasperReports Server REST API v2, e.g. http://localhost:8080/jasperserver/rest_v2

 -jslogin <login>             JasperServer login name

 -jspassword <password>       JasperServer password


 -delete <resource>           Delete specified resource from server, e.g. /reports/permits


 -import <file.zip>           Import zipped file with reports and xml metadata, e.g. d:\files\reports.zip


 -prepareimport               Prepare reports for importing to JasperReports Server

 -sourcedir <dirName>         Root folder with source jrxml report files. Option may be specified several times

 -tempdir <dirName>           Intermediate folder for generated files

 -targetzip <zipFileName>     Target zip file

 -targetresource <resource>   Target resource directory on JasperReports Server, e.g. /reports/permits

 -dsname <name>               Name of the datasource

 -dsdriver <className>        Data source jdbc driver class, e.g org.mariadb.jdbc.Driver

 -dsurl <connectionUrl>       Data source connection URL, e.g jdbc:mysql://localhost:3306/permits

 -dslogin <login>             Data source connection login

 -dspassword <password>       Data source connection password

 -exclude <patternsFile>      Skip report files and dirs which names match patterns listed in this file

 -verbose <level>             Verbosity level: TRACE, DEBUG, INFO, WARN, ERROR, ALL or OFF. Default level is INFO
