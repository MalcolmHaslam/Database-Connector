/**

   Copyright 2011 RunMyProcess SAS

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
   
**/

/**

	Database Connector
	
	https://github.com/runmyprocess/Database-Connector
		
**/

import groovy.json.JsonBuilder
import groovy.json.StreamingJsonBuilder
import groovy.sql.Sql
import groovy.xml.MarkupBuilder

// known/expected parameter names

// JDBC url, e.g : jdbc:mysql://localhost:3306/mydb?characterEncoding=UTF-8
final String SQL_URL = "x-sqlp-url"

// Database username
final String SQL_USERNAME = "x-sqlp-username"

// Database password
final String SQL_PASSWORD = "x-sqlp-pwd"

// SQL statement to be executed
final String SQL_STATEMENT = "x-sqlp-stmt"

// JDBC driver class name, e.g : com.ibm.db2.jdbc.app.DB2Driver
final String SQL_DRIVER = "x-sqlp-driver"

// Response format: xml or json, json if not specified
final String SQL_FORMAT = "x-sqlp-format"

// xml builder
// result will be sent using <rows> and <row> elements
// each row will be composed of elements deducted from the table column names
// <rows><row><name1>value</name1><name2>value</name2></row></rows>
def xmlWriter( items, writer ) {

  	writer <<  "HTTP/1.0 200 OK\n"
	def contentType = "Content-Type: text/xml; charset=utf-8"
	writer << contentType << "\n\n"

	writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
	def xml = new MarkupBuilder(writer)
	xml.rows {
		items.each { item ->
			xml.row {
				item.each { key, value -> 
					xml."$key"( value )
				} 
			}
		}
	}
}


// json builder
// result will be sent in an array named 'rows'
// each element will be a json object, with fields deducted from the table column names
// { "rows":[{ name1:value, name2:value }, ... ] }
def jsonWriter( items, writer ) {
	def rows = ['rows':items]
	
	writer <<  "HTTP/1.0 200 OK\n"
	def contentType = "Content-Type: application/json; charset=utf-8"
	writer << contentType << "\n\n"


	def json = new StreamingJsonBuilder(writer, rows )
}


// Execute SQL request, a metaClosure is used to retrieved column names
// Results are placed in list, used by the builders
def executeStatement( format, writer, String sqlDriver, String sqlSource, String sqlUsername, String sqlPassword, String sqlStatement ) {
	def fields = []
	def items = []
	
	def sqlConnection = Sql.newInstance( sqlSource, sqlUsername, sqlPassword, sqlDriver )
	
	if( sqlStatement.matches("\\s*[sS][eE][lL][eE][cC][tT].*") ) {
		Closure metaClosure = { info ->
			for( int index=0; index < info.columnCount; ++index ) fields << info.getColumnName(index+1)
		}
		
		sqlConnection.eachRow(sqlStatement, metaClosure ) { row ->
			def item = [:]
			fields.each { field ->
				item[field] = row[field]
			}
			items << item
		}

	} else {
		if( !sqlConnection.execute( sqlStatement )) {
			items << [count:sqlConnection.updateCount]
		}
	}
		
	sqlConnection.close()
	format == "xml" ? xmlWriter( items, writer ) : jsonWriter( items, writer )
}


// Main part of the script
// This script is based on the -l option of groovy
// Groovy will start a socket server, listening on the specified port
// The script will be executed each time a new line is received on the socket
// The script will try to extract a parameter from the given line, until a blank line is received
// Then the request is executed if all the needed parameters have been found
// Once the request is executed, the script returns the string "success" telling Groovy to start over a new session

// Implicit variables, these variables are injected by the Groovy engine before calling the script
// init: true if a new session (first line) is started, false otherwise
// socket: the socket that have been accepted
// out: a Writer that can be used to send the response, 
// 		the script does not use this, since an utf-8 response is enforced, using the socket output stream
//
// line: the line received on the socket

if (init) {
	// reset parameters values
	values = [:]
}
  

if (line.size() > 0) {
	// non blank line, try to extract a parameter value
	// format: parameter-name:value
	def separatorPosition = line.indexOf(':')
  	if( separatorPosition > 0 ) {
	  	header = line.substring(0, separatorPosition )
	  	value = line.substring( separatorPosition+1 )
	  	if( header && value ) {
		  	values[header] = value.trim()
	  	}
  	}
  
} else {
	// blank line, execute request if all mandatory parameters have been provided
	// enforce UTF-8 output
  	def writer = new OutputStreamWriter( socket.outputStream, "UTF-8" )
  
  	// only format is optional
  	if( values[SQL_DRIVER] && values[SQL_URL] && values[SQL_USERNAME] && values[SQL_PASSWORD] && values[SQL_STATEMENT] ) {
	  
	  try {
	  
  		executeStatement(  values[SQL_FORMAT], writer, values[SQL_DRIVER], values[SQL_URL], values[SQL_USERNAME], values[SQL_PASSWORD], values[SQL_STATEMENT] )
  		  
	  } catch( Exception ex ) {
  	 	writer << "HTTP/1.0 400 Bad Request\n" << "Content-Type: text/plain; charset=utf-8\n\n" << ex.getMessage()
  	  } 
		  
  } else {
  	writer << "HTTP/1.0 400 Bad Request\n\n"
  }
  writer.flush()
  writer.close()
  /* 	
  	try to avoid https://jira.codehaus.org/browse/GROOVY-5790
  	suggested by Tom Roche
  */
  this.class.getClassLoader().clearCache()
  return "success"
}