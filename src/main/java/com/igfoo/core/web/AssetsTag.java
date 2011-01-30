package com.igfoo.core.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
  private boolean includeDynamic = true;

  public void setTypes(String types) {
    this.types = types;
  }

  public void setNames(String names) {
    this.names = names;
  }

  public void setIncludeGlobal(boolean includeGlobal) {
    this.includeGlobal = includeGlobal;
  }

  public void setIncludeDynamic(boolean includeDynamic) {
    this.includeDynamic = includeDynamic;
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

          String title = null;
          List<String> metas = new ArrayList<String>();
          List<String> links = new ArrayList<String>();
          List<String> scripts = new ArrayList<String>();

          // get all the named resources
          for (String name : nameList) {

            // get only the first title, multiple titles not allowed
            String namedTitle = assetManager.getTitleForName(name, curLocale);
            if (title == null && StringUtils.isNotBlank(namedTitle)) {
              title = namedTitle;
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

          // write out the title
          if (StringUtils.isNotBlank(title)) {
            out.print(title + "\n");
            request.setAttribute(Assets.TITLE_TAG, title);
          }

          // write out meta tags, links, and scripts in that order
          if (metas != null && !metas.isEmpty()) {
            for (String header : metas) {
              out.print(header + "\n");
            }
            request.setAttribute(Assets.META_TAGS, metas);
          }
          if (links != null && links.size() > 0) {
            for (String link : links) {
              out.print(link + "\n");
            }
            request.setAttribute(Assets.LINK_TAGS, links);
          }
          if (scripts != null && scripts.size() > 0) {
            for (String script : scripts) {
              out.print(script + "\n");
            }
            request.setAttribute(Assets.SCRIPT_TAGS, scripts);
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

            // dynamic titles from controller with override any title for path
            if (includeDynamic) {
              String dynTitle = (String)request.getAttribute(Assets.TITLE);
              if (StringUtils.isNotBlank(dynTitle)) {
                String locTitle = assetManager.getDynamicTitle(dynTitle,
                  curLocale);
                if (StringUtils.isNotBlank(locTitle)) {
                  title = locTitle;
                }
              }
            }

            if (StringUtils.isNotBlank(title)) {
              out.print(title + "\n");
              request.setAttribute(Assets.TITLE_TAG, title);
            }
          }

          if (allTypes || types.contains("meta")) {

            List<String> metas = assetManager.getMetaForPath(requestPath,
              curLocale, includeGlobal);

            if (includeDynamic) {
              List<Map<String, String>> dynMetas = (List<Map<String, String>>)request
                .getAttribute(Assets.METAS);
              if (dynMetas != null && dynMetas.size() > 0) {
                List<String> allmetas = new ArrayList<String>();
                allmetas.addAll(metas);
                List<String> convMetas = assetManager.getDynamicMetas(dynMetas,
                  curLocale);
                if (convMetas != null && convMetas.size() > 0) {
                  allmetas.addAll(convMetas);
                }
                metas = allmetas;
              }
            }

            if (metas != null && !metas.isEmpty()) {
              for (String header : metas) {
                out.print(header + "\n");
              }
              request.setAttribute(Assets.META_TAGS, metas);
            }
          }

          if (allTypes || types.contains("links")) {

            List<String> links = assetManager.getLinksForPath(requestPath,
              curLocale, includeGlobal);

            if (includeDynamic) {
              List<Map<String, String>> dynLinks = (List<Map<String, String>>)request
                .getAttribute(Assets.LINKS);
              if (dynLinks != null && dynLinks.size() > 0) {
                List<String> alllinks = new ArrayList<String>();
                alllinks.addAll(links);
                List<String> convLinks = assetManager.getDynamicLinks(dynLinks,
                  curLocale);
                if (convLinks != null && convLinks.size() > 0) {
                  alllinks.addAll(convLinks);
                }
                links = alllinks;
              }
            }

            if (links != null && links.size() > 0) {
              for (String link : links) {
                out.print(link + "\n");
              }
              request.setAttribute(Assets.LINK_TAGS, links);
            }
          }

          if (allTypes || types.contains("scripts")) {

            List<String> scripts = assetManager.getScriptsForPath(requestPath,
              curLocale, includeGlobal);

            if (includeDynamic) {
              List<Map<String, String>> dynScripts = (List<Map<String, String>>)request
                .getAttribute(Assets.SCRIPTS);
              if (dynScripts != null && dynScripts.size() > 0) {
                List<String> allscripts = new ArrayList<String>();
                allscripts.addAll(scripts);
                List<String> convScripts = assetManager.getDynamicScripts(
                  dynScripts, curLocale);
                if (convScripts != null && convScripts.size() > 0) {
                  allscripts.addAll(convScripts);
                }
                scripts = allscripts;
              }
            }

            if (scripts != null && scripts.size() > 0) {
              for (String script : scripts) {
                out.print(script + "\n");
              }
              request.setAttribute(Assets.SCRIPT_TAGS, scripts);
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
