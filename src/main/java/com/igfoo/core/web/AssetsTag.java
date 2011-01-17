package com.igfoo.core.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;

import org.apache.commons.lang.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.support.RequestContextUtils;

public class AssetsTag
  extends TagSupport {

  private String types;
  private String names;
  private boolean includeGlobal = true;

  public void setTypes(String types) {
    this.types = types;
  }

  public void setNames(String names) {
    this.names = names;
  }

  public void setIncludeGlobal(boolean includeGlobal) {
    this.includeGlobal = includeGlobal;
  }

  public int doStartTag()
    throws JspException {

    try {

      HttpServletRequest request = (HttpServletRequest)pageContext.getRequest();
      WebApplicationContext context = RequestContextUtils
        .getWebApplicationContext(request);
      String beanName = "assetManager";
      AssetManager assetManager = (AssetManager)context.getBean(beanName);
      Locale curLocale = request.getLocale();
      JspWriter out = pageContext.getOut();

      if (request != null) {

        if (names != null) {

          List<String> nameList = new ArrayList<String>();
          String[] includeNamesAr = StringUtils.split(names, ",");
          for (String includeName : includeNamesAr) {
            includeName = StringUtils.trim(StringUtils.lowerCase(includeName));
            nameList.add(includeName);
          }

          List<String> titles = new ArrayList<String>();
          List<String> metas = new ArrayList<String>();
          List<String> links = new ArrayList<String>();
          List<String> scripts = new ArrayList<String>();

          // get all the named resources
          for (String name : nameList) {

            String namedTitle = assetManager.getTitleForName(name, curLocale);
            if (StringUtils.isNotBlank(namedTitle)) {
              titles.add(namedTitle);
            }
            List<String> namedMetas = assetManager.getMetaForName(name,
              curLocale);
            if (namedMetas != null && !namedMetas.isEmpty()) {
              metas.addAll(namedMetas);
            }
            List<String> namedLinks = assetManager.getLinksForName(name,
              curLocale);
            if (namedLinks != null && namedLinks.size() > 0) {
              links.addAll(namedLinks);
            }
            List<String> namedScripts = assetManager.getScriptsForName(name,
              curLocale);
            if (namedScripts != null && namedScripts.size() > 0) {
              scripts.addAll(namedScripts);
            }
          }

          // write out only a single titles
          if (titles != null && titles.size() > 0) {
            String title = titles.get(0);
            out.print(title + "\n");
            request.setAttribute("titleTag", title);
          }
          
          // write out meta tags, links, and scripts in that order
          if (metas != null && !metas.isEmpty()) {
            for (String header : metas) {
              out.print(header + "\n");
            }
            request.setAttribute("metaTags", metas);
          }
          if (links != null && links.size() > 0) {
            for (String link : links) {
              out.print(link + "\n");
            }
            request.setAttribute("linkTags", links);
          }
          if (scripts != null && scripts.size() > 0) {
            for (String script : scripts) {
              out.print(script + "\n");
            }
            request.setAttribute("scriptTags", scripts);
          }

        }
        else if (types != null) {

          Set<String> typeSet = new HashSet<String>();
          String[] includeTypesAr = StringUtils.split(types, ",");
          for (String includeType : includeTypesAr) {
            includeType = StringUtils.trim(StringUtils.lowerCase(includeType));
            typeSet.add(includeType);
          }
          boolean allTypes = types.isEmpty();

          String requestPath = (String)request
            .getAttribute("assets.request.path");
          if (requestPath == null) {
            requestPath = request.getRequestURI();
          }

          if (allTypes || types.contains("title")) {
            String title = assetManager.getTitleForPath(requestPath, curLocale,
              includeGlobal);
            if (StringUtils.isNotBlank(title)) {
              out.print(title + "\n");
              request.setAttribute("titleTag", title);
            }
          }

          if (allTypes || types.contains("meta")) {
            List<String> metas = assetManager.getMetaForPath(requestPath,
              curLocale, includeGlobal);
            if (metas != null && !metas.isEmpty()) {
              for (String header : metas) {
                out.print(header + "\n");
              }
              request.setAttribute("metaTags", metas);
            }
          }

          if (allTypes || types.contains("links")) {
            List<String> links = assetManager.getLinksForPath(requestPath,
              curLocale, includeGlobal);
            if (links != null && links.size() > 0) {
              for (String link : links) {
                out.print(link + "\n");
              }
              request.setAttribute("linkTags", links);
            }
          }

          if (allTypes || types.contains("scripts")) {
            List<String> scripts = assetManager.getScriptsForPath(requestPath,
              curLocale, includeGlobal);
            if (scripts != null && scripts.size() > 0) {
              for (String script : scripts) {
                out.print(script + "\n");
              }
              request.setAttribute("scriptTags", scripts);
            }
          }
        }
      }
    }
    catch (IOException e) {
      throw new JspException(e);
    }

    return SKIP_BODY;
  }

  public int doEndTag() {
    return EVAL_PAGE;
  }
}
