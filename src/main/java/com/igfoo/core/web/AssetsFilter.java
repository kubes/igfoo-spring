package com.igfoo.core.web;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

public class AssetsFilter
  implements Filter {

  public static final String REQUEST_PATH = "assets.request.path";

  @Override
  public void init(FilterConfig config)
    throws ServletException {

  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
    FilterChain chain)
    throws IOException, ServletException {
    HttpServletRequest httpReq = (HttpServletRequest)request;
    request.setAttribute(REQUEST_PATH, httpReq.getServletPath());
    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {

  }

}
