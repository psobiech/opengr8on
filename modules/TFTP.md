# TFTP Client and Server

Server based on Apache Commons Net implementation (https://commons.apache.org/net/).

* Added support for dos-like paths, paths like a:\TEST.TXT are converted to ./a/TEST.TXT from the root of the TFTP
  server root.
* Replaced constants with enums
* Added functional exception (for e.g. to abort transfers)
* Added error codes in exceptions
* Extracted common code between server and client
* Switched to Virtual Threads
* Uses nio instead of io API
* Writing of data is done into temporary files first and then moved to target location after success (we're not ending
  with partially uploaded files)
* Removed fix
  for https://issues.apache.org/jira/browse/NET-414 / https://svn.apache.org/viewvc?view=revision&revision=1782356, as
  that was breaking CLU compatibility.
