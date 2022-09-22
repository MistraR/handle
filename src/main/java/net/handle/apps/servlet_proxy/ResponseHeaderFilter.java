package net.handle.apps.servlet_proxy;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

public class ResponseHeaderFilter implements Filter {

	private Map<String, String> headers = new HashMap<>();
	
	@Override
	public void destroy() {
		//no-op
	}
	
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
		  HttpServletResponse httpServletResponse = (HttpServletResponse) response;
		  for (Map.Entry<String, String> entry : headers.entrySet()) {
	          httpServletResponse.addHeader(entry.getKey(), entry.getValue());
		  }
		  filterChain.doFilter(request, response);
		  
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	    for (String header : Collections.list(filterConfig.getInitParameterNames())) {
	        headers.put(header, filterConfig.getInitParameter(header));
	    }
	}
}
