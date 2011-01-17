Igfoo Spring Asset Manager
--------------------------------------------------------------------------------

The Igfoo web assets manager is a configuration system that allow dynamic 
inclusion of web assets including meta tags, the title tag, link tags and style
sheets, and scripts for spring web applications.  All asset handling is based on
of the url path that is being requested. The assets manager consists of three 
components, a filter for setting the url request path, a service class for 
parsing and reloading configuration files, and a JSP tag library for including
assets in JSP pages.

The assets manager is setup in the web.xml file by including the filter and the 
filter mapping.  Usually this is for all requests.

<filter>
  <filter-name>AssetsFilter</filter-name>
  <filter-class>com.igfoo.core.web.AssetsFilter</filter-class>
</filter>  
<filter-mapping>
  <filter-name>AssetsFilter</filter-name>
  <url-pattern>/*</url-pattern>
</filter-mapping>
  
In the spring webapp context file we setup the asset manager service component.

<bean id="assetManager" class="com.igfoo.core.web.ReloadableAssetManager"
  init-method="initialize" destroy-method="shutdown">
  <property name="rootDir" value="${webapp.root}" />
  <property name="resources" value="/WEB-INF/assets/**/*-assets.json" />
  <property name="reloadInterval" value="5000" />
  <property name="caching" value="true" />
</bean>
  
The name must be assetManager as it is called by the JSP tags.  The resources
path allows dynamic inclusion of configuration files.  Usually we have a folder
under WEB-INF called assets under which are many folders and *-assets.json 
configuration files.  The assets manager will poll for file changes and reload
any file that it finds has changed.  The reloadInternal is in milliseconds.
While the configuration file content can be changed without reloading the web
application, more configuration files cannot be added without reloading.  The
configuration files that are found upon application startup are the files polled
for changes.  Caching will allow calculated assets values to be cached instead
of having to be reprocessed on every request.

All configuration is done through JSON files.  There is special file named 
global-assets.json used for assets to be included in every request.  All assets
other than global assets must have either a name or a path attribute.  Path
attributes are used to specify the url path for which assets are activated. 
Named assets allow assets to be activated by a common name.  Multiple assets 
can be configured per file.  An example is below.

[
  {
    "path": "/example.html",    
    "title": "global.title",
    "name": "myname",
    "meta" : [
      {
        "http-equiv": "Content-Type",
        "content": "text/html; charset=utf-8" 
      }
    ],
    "scripts" : [
      "/scripts/common/jquery-1.4.2.min.js",
      "/scripts/common/jquery-ui-1.8.4.min.js",
      {
        "id": "google-analytics",
        "type": "text/javascript",
        "src": "/scripts/common/google.js",
        "embed": "/scripts/common/embed.js"
      } 
    ],
    "links" : [
      {
        "rel": "canonical",
        "href": "/index.html" 
      },        
      "/styles/common/core-styles.css",
      "/styles/common/header-styles.css",
      "/styles/common/footer-styles.css",
      {
        "id": "page-styles",
        "type": "text/css",
      }
    ] 
  }
]

Path is the request path.  As you can see we can configure meta, script, link,
and title elements.  Title is the title of the page.  Meta are meta tags.  The
links are link elements including style sheets.  Meta will include as attributes
any nested property name and value.  

Notice that in title we use global.title.  Script values, meta names and values,
title elements, and link names and values can all use message properties in
their names and values.  These properties are pulled from a spring configured 
i18n message source for the current locale.  Using internationalized properties 
allow having internationalized titles, scripts, and meta tags.  For example you
can include a different style sheet for an arabic version of your website than
for the english version. 

Scripts can be configured either by a shorthand property containing only a 
string value or by a nested object.  If using a shorthand string the type will 
be text/javascript and the string value will be the script source.  Nested 
script object can contain an embed parameter with a filepath from root of the 
web application.  This will embed the script in the page inside of the script
tag.  As you can see the script tag can have a different href than is included.
This is useful for things like google analytics that have embeddable scripts.
Scripts can have an id element and the JSP tags allow referencing scripts by an
id.  This is useful when you want to embed a single script at a single place in
a webpage, such as with advertisements.

Links contain both link elements and style sheet elements.  For example you can
have a canonical url element.  Links also have a shorthand form.  If using the
shorthand type is "text/css" and href is the string value.

The asset manager tags are included on the JSP page using JSP tags.

<%@ taglib prefix="assets" uri="igfoo-assets.tld" %>

<html>
  <head>
    <assets:include types="title,meta,links" />
  </head>
  <body>
    <div id="content">
      <div id="ads">
        <assets:include types="scripts" ids="google-analytics, myid" />
      </div>
    </div>
    <div id="footer>
    </div>
    <assets:include types="scripts" />
  </body>
</html>

The assets tag has three options; types, ids, and includeGlobal.  Type are the
types of assets to included identified by path.  The types attribute is required
on all tags.  In the above example we include the title, meta tags, and links 
in the header and the scripts at the bottom of the web page.  Ids allow scripts 
and links, to be identified by id.  Each id must be unique the asset manager 
configurations.  In the example we include the scripts for google analytics and 
the myid link element.

