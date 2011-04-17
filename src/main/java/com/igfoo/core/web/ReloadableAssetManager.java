package com.igfoo.core.web;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.NoSuchMessageException;
import org.springframework.core.io.Resource;

public class ReloadableAssetManager
  implements MessageSourceAware, AssetManager {

  private final static Logger LOG = LoggerFactory
    .getLogger(ReloadableAssetManager.class);

  private MessageSource messageSource;
  private Resource[] resources;
  private String rootDir;
  private Map<String, Long> fileModTimes = new ConcurrentHashMap<String, Long>();
  private Map<String, String> aliases = new ConcurrentHashMap<String, String>();
  private Map<String, Map> assets = new ConcurrentHashMap<String, Map>();
  private Map<String, Map> namedAssets = new ConcurrentHashMap<String, Map>();
  private boolean caching = false;
  private long reloadInterval = 2000;
  private AssetReloaderThread reloader;
  private AtomicBoolean active = new AtomicBoolean(false);

  private Map<String, List> scriptsCache = new HashMap<String, List>();
  private Map<String, List> metaCache = new HashMap<String, List>();
  private Map<String, List> linksCache = new HashMap<String, List>();
  private Map<String, String> titleCache = new HashMap<String, String>();

  public static final String GLOBAL = "global";
  public static final String SCRIPTS = "scripts";
  public static final String METAS = "metas";
  public static final String LINKS = "links";
  public static final String TITLE = "title";

  private class AssetReloaderThread
    extends Thread {

    @Override
    public void run() {

      while (active.get()) {

        // loop through the files checking for updated modified times
        for (Entry<String, Long> fileMod : fileModTimes.entrySet()) {
          File assetConfigFile = new File(fileMod.getKey());
          if (assetConfigFile.exists()
            && (assetConfigFile.lastModified() > fileMod.getValue())) {
            LOG.info("Reloading asset config: " + assetConfigFile.getPath());
            loadAssetResource(assetConfigFile);
          }
        }

        // sleep and then do it all over again
        try {
          Thread.sleep(reloadInterval);
        }
        catch (InterruptedException e) {
          // continue if interrupted
        }
      }
    }
  }

  private String replaceAlias(String value) {
    return aliases.containsKey(value) ? aliases.get(value) : value;
  }

  private Map<String, String> replaceAliases(Map<String, String> keyVals) {
    Map<String, String> replaced = new LinkedHashMap<String, String>();
    for (Entry<String, String> keyVal : keyVals.entrySet()) {
      String value = replaceAlias(keyVal.getValue());
      replaced.put(keyVal.getKey(), value);
    }
    return replaced;
  }

  private String getMessage(String property, Locale locale) {
    try {
      return messageSource.getMessage(property, new Object[0], locale);
    }
    catch (NoSuchMessageException e) {
      return property;
    }
  }

  private String getEmbeddedSource(String embedPath) {

    if (StringUtils.isNotBlank(embedPath)) {
      File embedFile = new File(rootDir + File.separator + embedPath);
      if (embedFile.exists()) {
        String source = null;
        try {
          source = FileUtils.readFileToString(embedFile);
        }
        catch (IOException e) {
          LOG.error("Error reading embed resource: " + embedFile.getPath(), e);
        }
        return source;
      }
    }
    return "";
  }

  private Map<String, String> getFieldValueMap(JsonNode root) {
    Map<String, String> attrMap = new LinkedHashMap<String, String>();
    for (String fieldname : JsonUtils.getFieldNames(root)) {
      String value = JsonUtils.getStringValue(root, fieldname);
      if (StringUtils.isNotBlank(value)) {
        attrMap.put(fieldname, value);
      }
    }
    return attrMap;
  }

  private void loadAssetResource(File assetConfigFile) {

    // don't do anything if config file doesn't exist
    if (!assetConfigFile.exists()) {
      LOG.warn("Asset config file doesn't exist: " + assetConfigFile.getPath()
        + ", ignoring it");
      return;
    }

    // set the last modified for the file before parsing in case of errors
    String configFilename = assetConfigFile.getPath();
    fileModTimes.put(configFilename, assetConfigFile.lastModified());

    try {

      // processing global file or content file
      String filename = assetConfigFile.getName();
      boolean isGlobal = StringUtils.equals(filename, "global-assets.json");

      // get the root of the json
      ObjectMapper mapper = new ObjectMapper();
      ArrayNode root = mapper.readValue(assetConfigFile, ArrayNode.class);

      // loop through each asset configuration, one per url, one for global
      for (JsonNode asset : root) {

        // process aliases first
        if (isGlobal && asset.has("aliases")) {
          System.out.println(asset.get("aliases"));
          aliases.putAll(getFieldValueMap(asset.get("aliases")));
        }

        // map to hold the configuration for the url or global
        Map<String, Object> curAssets = new LinkedHashMap<String, Object>();

        // get all paths
        List<String> paths = new ArrayList<String>();
        if (asset.has("paths")) {
          JsonNode pathsNode = asset.get("paths");
          paths = JsonUtils.getStringValues(pathsNode);
        }

        String title = JsonUtils.getStringValue(asset, "title");
        String name = JsonUtils.getStringValue(asset, "name");
        boolean isNamed = StringUtils.isNotBlank(name);

        // if no path and not global and not named then ignore and continue
        if (paths.isEmpty() && !isGlobal && !isNamed) {
          LOG.info("No paths for asset, ignoring: " + configFilename);
          continue;
        }

        // add the title if any
        if (StringUtils.isNotBlank(title)) {
          curAssets.put("title", replaceAlias(title));
        }

        // loop through the meta tag configurations
        if (asset.has("meta")) {
          List<Map<String, String>> metas = new ArrayList<Map<String, String>>();
          for (JsonNode meta : asset.get("meta")) {
            Map<String, String> fieldMap = replaceAliases(getFieldValueMap(meta));
            if (fieldMap.size() > 0) {
              metas.add(fieldMap);
            }
          }

          // add meta list to asset map if anything to add
          if (metas.size() > 0) {
            curAssets.put(METAS, metas);
          }
        }

        // loop through the scripts
        if (asset.has("scripts")) {
          List<Map<String, String>> scripts = new ArrayList<Map<String, String>>();
          for (JsonNode script : asset.get("scripts")) {

            // scripts can be shorthand of just the href, and can be an alias
            Map<String, String> fieldMap = null;
            if (script instanceof TextNode) {

              String src = ((TextNode)script).getValueAsText();
              src = replaceAlias(src);
              fieldMap = new LinkedHashMap<String, String>();
              fieldMap.put("type", "text/javascript");
              fieldMap.put("src", src);
            }
            else {
              fieldMap = replaceAliases(getFieldValueMap(script));
            }

            if (fieldMap.size() > 0) {
              scripts.add(fieldMap);
            }
          }

          // add script list to asset map if anything to add
          if (scripts.size() > 0) {
            curAssets.put(SCRIPTS, scripts);
          }
        }

        if (asset.has("links")) {
          List<Map<String, String>> links = new ArrayList<Map<String, String>>();
          for (JsonNode link : asset.get("links")) {

            // scripts can be shorthand of just the href, and can be an alias
            Map<String, String> fieldMap = null;
            if (link instanceof TextNode) {

              String href = ((TextNode)link).getValueAsText();
              href = replaceAlias(href);
              fieldMap = new LinkedHashMap<String, String>();
              fieldMap.put("rel", "stylesheet");
              fieldMap.put("type", "text/css");
              fieldMap.put("href", href);
            }
            else {
              fieldMap = replaceAliases(getFieldValueMap(link));
            }

            if (fieldMap.size() > 0) {
              links.add(fieldMap);
            }
          }

          // add links list to asset map if anything to add
          if (links.size() > 0) {
            curAssets.put(LINKS, links);
          }
        }

        // add the current assets as either global or for a specific path
        if (curAssets.size() > 0) {

          // setup as global, path, or named resources
          if (isGlobal) {
            assets.put(GLOBAL, curAssets);
            if (caching) {
              scriptsCache.clear();
              metaCache.clear();
              linksCache.clear();
              titleCache.clear();
            }
          }
          else if (isNamed) {
            namedAssets.put(name, curAssets);
            if (caching) {
              scriptsCache.remove(name);
              metaCache.remove(name);
              linksCache.remove(name);
              titleCache.remove(name);
            }
          }
          else {
            for (String path : paths) {
              assets.put(path, curAssets);
              if (caching) {
                scriptsCache.remove(path);
                metaCache.remove(path);
                linksCache.remove(path);
                titleCache.remove(path);
              }
            }
          }
        }
      } // end asset resources loop
    }
    catch (Exception e) {
      LOG.error("Error while parsing assets: " + configFilename, e);
    }
  }

  private String createScriptTag(Map<String, String> scriptAttrs, Locale locale) {

    StringBuilder scriptTagBuilder = new StringBuilder();

    String type = scriptAttrs.get("type");
    String src = getMessage(scriptAttrs.get("src"), locale);
    scriptTagBuilder.append("<script");
    if (StringUtils.isNotBlank(type)) {
      scriptTagBuilder.append(" type=\"" + type + "\"");
    }
    if (StringUtils.isNotBlank(src)) {
      scriptTagBuilder.append(" src=\"" + src + "\"");
    }
    scriptTagBuilder.append(">");

    String embedScript = getMessage(scriptAttrs.get("embed"), locale);
    if (StringUtils.isNotBlank(embedScript)) {
      scriptTagBuilder.append("\n" + getEmbeddedSource(embedScript) + "\n");
    }
    scriptTagBuilder.append("</script>");

    return scriptTagBuilder.toString();
  }

  private String createMetaTag(Map<String, String> metaAttrs, Locale locale) {

    StringBuilder metaTagBuilder = new StringBuilder();
    metaTagBuilder.append("<meta");
    for (Entry<String, String> metaAttr : metaAttrs.entrySet()) {
      String key = getMessage(metaAttr.getKey(), locale);
      String value = getMessage(metaAttr.getValue(), locale);
      if (StringUtils.isNotBlank(key)) {
        metaTagBuilder.append(" " + key + "=\"");
      }
      metaTagBuilder.append(value + "\"");
    }
    metaTagBuilder.append(" />");
    return metaTagBuilder.toString();
  }

  private String createLinkTag(Map<String, String> linkAttrs, Locale locale) {

    StringBuilder linkTagBuilder = new StringBuilder();

    linkTagBuilder.append("<link");
    for (Entry<String, String> linkAttr : linkAttrs.entrySet()) {
      String key = getMessage(linkAttr.getKey(), locale);
      String value = getMessage(linkAttr.getValue(), locale);
      if (StringUtils.isNotBlank(key)) {
        linkTagBuilder.append(" " + key + "=\"");
      }
      linkTagBuilder.append(value + "\"");
    }
    linkTagBuilder.append(" />");

    return linkTagBuilder.toString();
  }

  public ReloadableAssetManager() {

  }

  public ReloadableAssetManager(String rootDir, Resource[] resources) {

    // set the root directory and resources
    this.rootDir = rootDir;
    this.resources = resources;
  }

  public void initialize() {

    // loop through resources to load asset configs
    for (Resource resource : resources) {
      try {
        File assetConfigFile = resource.getFile();
        LOG.info("Loading asset config: " + assetConfigFile.getPath());
        loadAssetResource(assetConfigFile);
      }
      catch (Exception e) {
        // do nothing, continue with other files
      }
    }

    // activate the service
    active.set(true);

    // start the reloading thread if we have a reload interval
    if (reloadInterval > 0) {
      reloader = new AssetReloaderThread();
      reloader.setDaemon(true);
      reloader.start();
    }
  }

  public void shutdown() {
    active.set(false);
  }

  @Override
  public List<String> getScriptsForPath(String path, Locale locale,
    boolean global) {

    // check the cache first
    if (scriptsCache.containsKey(path)) {
      return scriptsCache.get(path);
    }

    // not in cache create a new list and populate with the global scripts
    List<String> scriptTags = new ArrayList<String>();

    // get the global and path assets
    Map globalAssets = (Map)assets.get(GLOBAL);
    Map pathAssets = (Map)assets.get(path);

    // add the global scripts
    if (global && globalAssets != null) {
      List<Map> globalScripts = (List<Map>)globalAssets.get(SCRIPTS);
      if (globalScripts != null && globalScripts.size() > 0) {
        for (Map<String, String> scriptAttrs : globalScripts) {
          String scriptTag = createScriptTag(scriptAttrs, locale);
          scriptTags.add(scriptTag);
        }
      }
    }

    // add the scripts for the path
    if (pathAssets != null) {
      List<Map> pathScripts = (List<Map>)pathAssets.get(SCRIPTS);
      if (pathScripts != null && pathScripts.size() > 0) {
        for (Map<String, String> scriptAttrs : pathScripts) {
          String scriptTag = createScriptTag(scriptAttrs, locale);
          scriptTags.add(scriptTag);
        }
      }
    }

    // don't allow the list to be modified by caller
    Collections.unmodifiableList(scriptTags);

    // cache the list for next time and return it
    if (caching && scriptTags.size() > 0) {
      scriptsCache.put(path, scriptTags);
    }

    return scriptTags;
  }

  @Override
  public List<String> getMetaForPath(String path, Locale locale, boolean global) {

    // check the cache first
    if (metaCache.containsKey(path)) {
      return metaCache.get(path);
    }

    // not in cache create a new list and populate with the global scripts
    List<String> metaTags = new ArrayList<String>();

    // get the global and path assets
    Map globalAssets = (Map)assets.get(GLOBAL);
    Map pathAssets = (Map)assets.get(path);

    // add the global meta tags
    if (global && globalAssets != null) {
      List<Map> globalMetas = (List<Map>)globalAssets.get(METAS);
      if (globalMetas != null && globalMetas.size() > 0) {
        for (Map<String, String> metaAttrs : globalMetas) {
          String metaTag = createMetaTag(metaAttrs, locale);
          metaTags.add(metaTag);
        }
      }
    }

    // add the met tags for the path
    if (pathAssets != null) {
      List<Map> pathMetas = (List<Map>)pathAssets.get(METAS);
      if (pathMetas != null && pathMetas.size() > 0) {
        for (Map<String, String> metaAttrs : pathMetas) {
          String metaTag = createMetaTag(metaAttrs, locale);
          metaTags.add(metaTag);
        }
      }
    }

    // don't allow the list to be modified by caller
    Collections.unmodifiableList(metaTags);

    // cache the list for next time and return it
    if (caching && metaTags.size() > 0) {
      metaCache.put(path, metaTags);
    }

    return metaTags;
  }

  @Override
  public List<String> getLinksForPath(String path, Locale locale, boolean global) {

    // check the cache first
    if (linksCache.containsKey(path)) {
      return linksCache.get(path);
    }

    // not in cache create a new list and populate with the global scripts
    List<String> linkTags = new ArrayList<String>();

    // get the global and path assets
    Map globalAssets = (Map)assets.get(GLOBAL);
    Map pathAssets = (Map)assets.get(path);

    // add the global links
    if (global && globalAssets != null) {
      List<Map> globalLinks = (List<Map>)globalAssets.get(LINKS);
      if (globalLinks != null && globalLinks.size() > 0) {
        for (Map<String, String> linkAttrs : globalLinks) {
          String linkTag = createLinkTag(linkAttrs, locale);
          linkTags.add(linkTag);
        }
      }
    }

    // add the links for the path
    if (pathAssets != null) {
      List<Map> pathLinks = (List<Map>)pathAssets.get(LINKS);
      if (pathLinks != null && pathLinks.size() > 0) {
        for (Map<String, String> linkAttrs : pathLinks) {
          String linkTag = createLinkTag(linkAttrs, locale);
          linkTags.add(linkTag);
        }
      }
    }

    // don't allow the list to be modified by caller
    Collections.unmodifiableList(linkTags);

    // cache the list for next time and return it
    if (caching && linkTags.size() > 0) {
      linksCache.put(path, linkTags);
    }

    return linkTags;
  }

  @Override
  public String getTitleForPath(String path, Locale locale, boolean global) {

    // check the cache first
    if (titleCache.containsKey(path)) {
      return titleCache.get(path);
    }

    // get the global and path assets
    Map globalAssets = (Map)assets.get(GLOBAL);
    Map pathAssets = (Map)assets.get(path);

    String title = null;
    if (pathAssets != null) {
      title = (String)pathAssets.get(TITLE);
      if (global && globalAssets != null && StringUtils.isBlank(title)) {
        title = (String)globalAssets.get(TITLE);
      }
    }

    // convert to message if necessary
    if (StringUtils.isNotBlank(title)) {
      title = getMessage(title, locale);
    }

    // add title wrapper and cache
    if (StringUtils.isNotBlank(title)) {
      title = "<title>" + title + "</title>";
      if (caching) {
        titleCache.put(path, title);
      }
    }

    return title;
  }

  @Override
  public List<String> getScriptsForName(String name, Locale locale) {

    // check the cache first
    if (scriptsCache.containsKey(name)) {
      return scriptsCache.get(name);
    }

    // not in cache create a new list and populate with the global scripts
    List<String> scriptTags = new ArrayList<String>();

    // get the global and path assets
    Map assets = (Map)namedAssets.get(name);

    // add the scripts for the path
    if (assets != null) {
      List<Map> scripts = (List<Map>)assets.get(SCRIPTS);
      if (scripts != null) {
        for (Map<String, String> scriptAttrs : scripts) {
          String scriptTag = createScriptTag(scriptAttrs, locale);
          scriptTags.add(scriptTag);
        }
      }
    }

    // don't allow the list to be modified by caller
    Collections.unmodifiableList(scriptTags);

    // cache the list for next time and return it
    if (caching && scriptTags.size() > 0) {
      scriptsCache.put(name, scriptTags);
    }

    return scriptTags;
  }

  @Override
  public List<String> getMetaForName(String name, Locale locale) {

    // check the cache first
    if (metaCache.containsKey(name)) {
      return metaCache.get(name);
    }

    // not in cache create a new list and populate with the global scripts
    List<String> metaTags = new ArrayList<String>();

    // get the named assets
    Map assets = (Map)namedAssets.get(name);

    // add the met tags for the name
    if (assets != null) {
      List<Map> metas = (List<Map>)assets.get(METAS);
      if (metas != null) {
        for (Map<String, String> metaAttrs : metas) {
          String metaTag = createMetaTag(metaAttrs, locale);
          metaTags.add(metaTag);
        }
      }
    }

    // don't allow the list to be modified by caller
    Collections.unmodifiableList(metaTags);

    // cache the list for next time and return it
    if (caching && metaTags.size() > 0) {
      metaCache.put(name, metaTags);
    }

    return metaTags;
  }

  @Override
  public List<String> getLinksForName(String name, Locale locale) {

    // check the cache first
    if (linksCache.containsKey(name)) {
      return linksCache.get(name);
    }

    // not in cache create a new list and populate with the global scripts
    List<String> linkTags = new ArrayList<String>();

    // get the named assets
    Map assets = (Map)namedAssets.get(name);

    // add the links for the path
    if (assets != null) {
      List<Map> links = (List<Map>)assets.get(LINKS);
      if (links != null) {
        for (Map<String, String> linkAttrs : links) {
          String linkTag = createLinkTag(linkAttrs, locale);
          linkTags.add(linkTag);
        }
      }
    }

    // don't allow the list to be modified by caller
    Collections.unmodifiableList(linkTags);

    // cache the list for next time and return it
    if (caching && linkTags.size() > 0) {
      linksCache.put(name, linkTags);
    }

    return linkTags;
  }

  @Override
  public String getTitleForName(String name, Locale locale) {

    // check the cache first
    if (titleCache.containsKey(name)) {
      return titleCache.get(name);
    }

    // get the global and path assets
    Map assets = (Map)namedAssets.get(name);
    String title = null;
    if (assets != null) {
      title = (String)assets.get(TITLE);
    }

    // convert to message if necessary
    if (StringUtils.isNotBlank(title)) {
      title = getMessage(title, locale);
      title = "<title>" + title + "</title>";
      if (caching) {
        titleCache.put(name, title);
      }
    }

    return title;
  }

  @Override
  public List<String> getDynamicScripts(List scripts, Locale locale) {

    // create a new list every time for dynamic scripts
    List<String> scriptTags = new ArrayList<String>();

    // if we have scripts, loop through
    if (scripts != null && scripts.size() > 0) {
      for (int i = 0; i < scripts.size(); i++) {

        // either is a map of attributes or is just a string, create the single
        // script tag from either
        Object scriptObj = scripts.get(i);
        if (scriptObj instanceof Map) {
          Map<String, String> scriptAttrs = (Map<String, String>)scriptObj;
          if (scriptAttrs != null && scriptAttrs.size() > 0) {
            String scriptTag = createScriptTag(scriptAttrs, locale);
            scriptTags.add(scriptTag);
          }
        }
        else if (scriptObj instanceof String) {

          // set default values for script tag if shorthand string
          Map<String, String> scriptAttrs = new LinkedHashMap<String, String>();
          scriptAttrs.put("type", "text/javascript");
          scriptAttrs.put("src", (String)scriptObj);
          String scriptTag = createScriptTag(scriptAttrs, locale);
          scriptTags.add(scriptTag);
        }
      }
    }

    // don't allow the list to be modified by caller
    Collections.unmodifiableList(scriptTags);

    return scriptTags;
  }

  @Override
  public List<String> getDynamicMetas(List<Map<String, String>> metas,
    Locale locale) {

    // not in cache create a new list and populate with the global scripts
    List<String> metaTags = new ArrayList<String>();

    // if we have metas, loop through
    if (metas != null && metas.size() > 0) {
      for (Map<String, String> metaAttrs : metas) {
        if (metaAttrs != null && metaAttrs.size() > 0) {
          String metaTag = createMetaTag(metaAttrs, locale);
          metaTags.add(metaTag);
        }
      }
    }

    // don't allow the list to be modified by caller
    Collections.unmodifiableList(metaTags);

    return metaTags;
  }

  @Override
  public List<String> getDynamicLinks(List links, Locale locale) {

    // create a new list every time for dynamic links
    List<String> linkTags = new ArrayList<String>();

    // if we have links, loop through
    if (links != null && links.size() > 0) {
      for (int i = 0; i < links.size(); i++) {

        // either is a map of attributes or is just a string, create the single
        // link tag from either
        Object linkObj = links.get(i);
        if (linkObj instanceof Map) {
          Map<String, String> linkAttrs = (Map<String, String>)linkObj;
          if (linkAttrs != null && linkAttrs.size() > 0) {
            String linkTag = createScriptTag(linkAttrs, locale);
            linkTags.add(linkTag);
          }
        }
        else if (linkObj instanceof String) {

          // set default values for link tag if shorthand string
          Map<String, String> linkAttrs = new LinkedHashMap<String, String>();
          linkAttrs.put("rel", "stylesheet");
          linkAttrs.put("type", "text/css");
          linkAttrs.put("href", (String)linkObj);
          String linkTag = createLinkTag(linkAttrs, locale);
          linkTags.add(linkTag);
        }
      }
    }

    // don't allow the list to be modified by caller
    Collections.unmodifiableList(linkTags);

    return linkTags;
  }

  @Override
  public String getDynamicTitle(String title, Locale locale) {

    // convert to message if necessary
    if (StringUtils.isNotBlank(title)) {
      title = getMessage(title, locale);
      title = "<title>" + title + "</title>";
    }

    return title;
  }

  public void setMessageSource(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  public void setCaching(boolean caching) {
    this.caching = caching;
  }

  public void setResources(Resource[] resources) {
    this.resources = resources;
  }

  public void setRootDir(String rootDir) {
    this.rootDir = rootDir;
  }

  public void setReloadInterval(long reloadInterval) {
    this.reloadInterval = reloadInterval;
  }

}
