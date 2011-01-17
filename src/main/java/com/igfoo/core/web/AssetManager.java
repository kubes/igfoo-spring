package com.igfoo.core.web;

import java.util.List;
import java.util.Locale;

public interface AssetManager {

  public List<String> getScriptsForPath(String path, Locale locale,
    boolean global);

  public List<String> getScriptsForName(String name, Locale locale);

  public List<String> getMetaForPath(String path, Locale locale, boolean global);

  public List<String> getMetaForName(String name, Locale locale);

  public List<String> getLinksForPath(String path, Locale locale, boolean global);

  public List<String> getLinksForName(String name, Locale locale);

  public String getTitleForPath(String path, Locale locale, boolean global);

  public String getTitleForName(String name, Locale locale);

}
