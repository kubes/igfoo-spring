package com.igfoo.core.web;

public interface Assets {
  
  // the asset path
  public static final String PATH = "assets.request.path";
  
  // dynamic asset inclusion from controllers
  public static final String TITLE = "title";
  public static final String METAS = "metas";
  public static final String LINKS = "links";
  public static final String SCRIPTS = "scripts";
  
  // output variables for assets in request
  public static final String TITLE_TAG = "titleTag";
  public static final String META_TAGS = "metaTags";
  public static final String LINK_TAGS = "linkTags";
  public static final String SCRIPT_TAGS = "scriptTags";

}
