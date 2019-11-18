package hr.hrg.watch.build.proxy;

import java.io.IOException;
import java.util.*;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AddExpiresHeaderResponse implements Filter {

    public static final String NO_CACHE_URLS = "noCacheUrls";
	private String[] noCacheUrls;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    	String urls = filterConfig.getInitParameter(NO_CACHE_URLS);
    	if(urls != null){
    		noCacheUrls = urls.split(",");
    		Arrays.sort(noCacheUrls);    	
    	}else{
    		ArrayList<String> list = new ArrayList<String>();
    		for(int i=0; i<999; i++){
    			String url = filterConfig.getInitParameter("noCacheUrl"+i);
    			if(url == null){
    				if(i>0) break;
    			}else
    				list.add(url);
    		}
    		Collections.sort(list);
    		noCacheUrls = list.toArray(new String[list.size()]);
    	}
    }

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest httpReq = (HttpServletRequest) request;
		HttpServletResponse httpResp = (HttpServletResponse) response;
	
		String url = httpReq.getRequestURI();
		
		if (Arrays.binarySearch(noCacheUrls, url) > -1) {
        	httpResp.setHeader("Expires", "-1");
        	httpResp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        } else {
            Calendar inOneYear = new GregorianCalendar();
            inOneYear.add(Calendar.YEAR, 1);

            httpResp.setDateHeader("Expires", inOneYear.getTimeInMillis());
        }
		chain.doFilter(request, response);
	}
	

	@Override
	public void destroy() {
		
	}
}