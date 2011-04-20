Igfoo Spring Asset Manager
================================================================================
The Igfoo web assets manager is a configuration system that allow dynamic inclusion of web assets including meta tags, the title tag, link tags and style sheets, and scripts for spring web applications.  All asset handling is based on of the url path that is being requested. The assets manager consists of three components, a filter for setting the url request path, a service class for parsing and reloading configuration files, and a JSP tag library for including assets in JSP pages.

Setup
--------------------------------------------------------------------------------
The assets manager is setup in the web.xml file by including the filter and the filter mapping.  Usually this is for all requests.

    <filter>
      <filter-name>AssetsFilter</filter-name>
      <filter-class>com.igfoo.core.web.AssetsFilter</filter-class>
    </filter>  
    <filter-mapping>
      <filter-name>AssetsFilter</filter-name>
      <url-pattern>/*</url-pattern>
    </filter-mapping>

The asset manager service requires a root directory property.  This is used when including source files, such as scripts, directly on a webpage.  To make the configuration easy we use the ${webapp.root} parameter.  This is set by including the WebAppRootListener in the web.xml like this.
  
In the spring webapp context file we setup the asset manager service component.

    <bean id="assetManager" class="com.igfoo.core.web.ReloadableAssetManager"
      init-method="initialize" destroy-method="shutdown">
      <property name="rootDir" value="${webapp.root}" />
      <property name="resources" value="/WEB-INF/assets/**/*-assets.json" />
      <property name="reloadInterval" value="5000" />
      <property name="caching" value="true" />
    </bean>

The name must be assetManager as it is called by the JSP tags.  The resources path allows dynamic inclusion of configuration files.  Usually we have a folder under WEB-INF called assets under which are many folders and *-assets.json configuration files.  The assets manager will poll for file changes and reload any file that it finds has changed.  The reloadInternal is in milliseconds. While the configuration file content can be changed without reloading the web application, more configuration files cannot be added without reloading.  The configuration files that are found upon application startup are the files polled for changes.  Caching will allow calculated assets values to be cached instead of having to be reprocessed on every request.

Configuration
--------------------------------------------------------------------------------
All configuration is done through JSON files.  There is special file named global-assets.json used for assets to be included in every request.  All assets other than global assets must have either a name or a path attribute.  Path attributes are used to specify the url path for which assets are activated. Named assets allow assets to be activated by a common name.  Multiple assets can be configured per file.  An example is below.

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

The assets json can be a single object or an array of objects for multiple asset configuration per file.

Path is the request path.  As you can see we can configure meta, script, link, and title elements.  Title is the title of the page.  Meta are meta tags.  The links are link elements including style sheets.  Meta will include as attributes any nested property name and value.  

Notice that in title we use global.title.  Script values, meta names and values, title elements, and link names and values can all use message properties in their names and values.  These properties are pulled from a spring configured i18n message source for the current locale.  Using internationalized properties allow having internationalized titles, scripts, and meta tags.  For example you can include a different style sheet for an arabic version of your website than for the english version. 

Scripts can be configured either by a shorthand property containing only a string value or by a nested object.  If using a shorthand string the type will be text/javascript and the string value will be the script source.  The nested script object can contain an embed parameter with a filepath from root of the web application.  This will embed the script in the page inside of a script tag.  As you can see the script tag can have a different href than the source of the script that is included.  This is useful for scripts like google analytics that need to be embedded on page.  Scripts can have an id element and the JSP tags allow referencing scripts by an id.  This is useful when you want to embed a single script at a single place in a webpage, such as with advertisements.

Links contain both link elements and style sheet elements.  For example you can have a canonical url element.  Links also have a shorthand form.  If using the shorthand type is "text/css" and href is the string value.

    {
        "aliases" : {    
          "core.css" : "/css/core/core.css",      
          "jquery.js" : "/js/vendor/jquery-1.4.4.min.js",      
        }
    }

The global-assets.json file can configure aliases along with the standard link, script, and meta elements.  Aliases can then be used as either key or values anywhere in any of the asset config files.  This is especially useful when you have an element, such as a script, that is used in many different places but not globally.

    [
      {
        "path": "/example2.html",    
        "scripts" : [
          "${jquery.js}",
        ],
        "links" : [      
          "${core.css}"
        ] 
      }
    ]

In this example the jquery.js and core.css aliases would be replaced with their corresponding full values before being displayed on the page.  Note the opening and closing tags for defining ${aliases}.

Dynamic Assets
--------------------------------------------------------------------------------
Assets can be included dynamicaly inside of spring controllers.  The Assets class has four different asset identifiers.  Dynamic assets are included on a page by placing objects into the request under one of the asset identifers.

    // dynamic asset inclusion from controllers
    public static final String TITLE = "title";
    public static final String METAS = "metas";
    public static final String LINKS = "links";
    public static final String SCRIPTS = "scripts";

Title is expected to be a string.  Metas, links and scripts are expected to be a List<Map<String, String>> of values where the map is a name to attribute mapping similar to that found in the json configuration files.  Dynamic assets do not currently support the assets shorthand options.

Asset Custom JSP Tag Library
--------------------------------------------------------------------------------
The asset manager tag is included on the JSP page using a JSP tag.

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

The assets tag has four options; types, ids, includedDynamic, and includeGlobal.Types are the types of assets to included identified by path.  The types attribute is required on all tags.  In the above example we include the title, meta tags, and links in the header and the scripts at the bottom of the web page.  This is a standard for webpage performance.

Ids allow scripts and links, to be identified by id.  Each id must be unique the asset manager configurations.  In the example we include the scripts for google analytics and the myid link element.

By default includeDynamic and includeGlobal are true.  There is no need to set those attributes unless you want to specifically exclude global or dynamic assets from being shown.

Future
--------------------------------------------------------------------------------
In the future we are planning functionality that will allow aggregating and minifying scripts and stylesheets for a given url into a single cached page. Nothing would change in the configuration, but the output shown to the user would be a single script or stylesheet that was compressed instead of having multiple scripts and stylesheets on a page.

Pattern Reloadable Resource Bundle MessageSource
================================================================================
The Igfoo PatternReloadableResourceBundleMessageSource is a spring messagesource implementation build atop the spring ReloadableResourceBundleMessageSource class. It add the functionality to specify bundle files by filename pattern.  The class will handle reloading of all language specific version of a file as well as the default.  For example *_i18n.xml and *.18n_fr.xml will both be reloaded should they exist and be changed.  You would only need to specify the main bundle name as shown below.

The messagesource class is configured in the web.xml file.  A best practice for this configuration is having a folder beneath WEB-INF that holds bundles.  Each bundle being named in the same manner.

    <bean id="messageSource"
      class="com.igfoo.core.web.PatternReloadableResourceBundleMessageSource">
      <property name="cacheSeconds" value="1" />
      <property name="resources">
        <list>
          <value>/WEB-INF/bundles/**/*_i18n.xml</value>
        </list>
      </property>
    </bean>
    
New bundle files that are added to the bundles folder will not be picked up until the application is restarted.  
