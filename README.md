Database Connector
================
	
Very simple SQL/jdbc proxy.

Read all needed parameters, one on each line, to connect to a database using a jdbc driver, and then execute the request.

Result is then transmitted, serialized in one of two possible formats: xml or json.

HTTP 1.0 response codes are used

Warning: absolutely no filters on the request will be performed, hence a drop database would be executed for example.

How to start the connector: 

	export JAVA_OPTS=-server -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled
	groovy -cp .:path/to/myjdbcdriver.jar -l 8000 DBConnector.groovy

The connector will then listen on the port 8000	

You can use an http request to call the connector, passing parameters through HTTP headers

	curl -v -q -H "x-sqlp-url:jdbc:mysql://localhost:3306/mydatabase?characterEncoding=UTF-8" -H "x-sqlp-username:dbuser" 	-H "x-sqlp-pwd:dbpwd" -H "x-sqlp-driver:com.mysql.jdbc.Driver" 	-H "x-sqlp-stmt:select * from mytable limit 5" -H "x-sqlp-format:json" http://localhost:8000/