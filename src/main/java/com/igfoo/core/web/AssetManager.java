package com.igfoo.core.web;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface AssetManager {

  // assets by path

  public List<String> getScriptsForPath(String path, Locale locale,
    boolean global);

  public List<String> getMetaForPath(String path, Locale locale, boolean global);

  public List<String> getLinksForPath(String path, Locale locale, boolean global);

  public String getTitleForPath(String path, Locale locale, boolean global);

  // assets by name

  public List<String> getScriptsForName(String name, Locale locale);

  public List<String> getMetaForName(String name, Locale locale);

  public List<String> getLinksForName(String name, Locale locale);

  public String getTitleForName(String name, Locale locale);

  // dynamic assets

  public List<String> getDynamicScripts(List scripts, Locale locale);

  public List<String> getDynamicMetas(List<Map<String, String>> metas,
    Locale locale);

  public List<String> getDynamicLinks(List links, Locale locale);

  public String getDynamicTitle(String title, Locale locale);

}
