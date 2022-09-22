HDL.NET(R) PROXY SERVLET

Please be sure you have read the LICENSE.txt and agree to the terms.

The HDL.Net Proxy Servlet enables handle resolution using a web
browser. It accepts HTTP GET requests for handles, resolves those
handles, and returns an HTTP redirect to the URL value associated with
the handle (or chooses one of the URL values if there is more than
one).

The proxy servlet supports an REST API which allows handles to be
resolved in JSON format.  See the included REST-API.txt for
documentation.

The proxy servlet is implemented as a Java servlet. The proxy servlet
was developed and tested under the Apache Tomcat servlet environment.

CNRI runs a Proxy Server System at http://hdl.handle.net. The
servlet distribution is for those who want to set up their own proxy
server.

PLEASE FOLLOW THESE STEPS IN ORDER.

1) Install Java version 8 or higher on your computer.
   Note: if you already have Java installed on your computer, type
   'java -version' at the command prompt to find out what version
   has been installed.

2) Install a Servlet environment. The proxy servlet was developed
   and tested under Apache Tomcat.

3) Under the new hdlproxy-9.2.0 directory, you will find
   hdlproxy-9.2.0.war, which can be deployed in the servlet container
   to run the handle proxy.

   The hdlproxy.properties file, located by default under WEB-INF
   in the servlet directory, contains information that is used
   to configure a proxy servlet.

   To view the proxy servlet code and modify it for development of
   custom proxy servlets, you will need to unzip the
   hdlproxy-9.2.0-src.zip file.

4) Please send all comments, questions and bug reports to
   hdladmin@cnri.reston.va.us.

Thank you for your interest in CNRI's HANDLE.NET SOFTWARE.
